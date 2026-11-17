package org.blackaddons.platform.cosmetics

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import org.blackaddons.core.log.Log
import org.blackaddons.platform.util.modId
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.util.concurrent.ArrayBlockingQueue
import javax.imageio.ImageIO
import javax.imageio.ImageReader
import javax.imageio.stream.ImageInputStream

/** Frames decoded ahead. Enough to hide a decode, more is just latency. */
private const val QUEUE_DEPTH = 3

internal abstract class CapeEntry {
    /** Written on the client thread, read by the decoder. */
    @Volatile
    var lastUsedMs = System.currentTimeMillis()

    /** Client thread, null until the first frame is up. */
    abstract fun identifier(rotated: Boolean): Identifier?

    /** Client thread. */
    abstract fun releaseTextures()
}

internal class Variant(val id: Identifier, val texture: DynamicTexture)

/** Client thread. */
internal fun registerVariant(base: String, rotated: Boolean, image: NativeImage): Variant {
    val suffix = if (rotated) "rotated" else "upright"
    val id = modId("capes/$base/$suffix")
    val texture = DynamicTexture({ "blackaddons-cape" }, image)
    Minecraft.getInstance().textureManager.register(id, texture)
    return Variant(id, texture)
}

/** A PNG, or a single-frame GIF. */
internal class StillCape(private val base: String, image: NativeImage) : CapeEntry() {
    private val normal = registerVariant(base, rotated = false, image = image)
    private var rotated: Variant? = null
    private var rotationFailed = false

    override fun identifier(rotated: Boolean): Identifier {
        lastUsedMs = System.currentTimeMillis()
        if (!rotated || rotationFailed) return normal.id
        val variant = this.rotated ?: try {
            registerVariant(base, rotated = true, image = CapeImages.rotate(normal.texture.pixels))
                .also { this.rotated = it }
        } catch (t: Throwable) {
            // Fall back to unrotated instead of retrying every frame.
            Log.main.error("Rotated cape upload failed for {}", base, t)
            rotationFailed = true
            return normal.id
        }
        return variant.id
    }

    override fun releaseTextures() {
        val textures = Minecraft.getInstance().textureManager
        textures.release(normal.id)
        rotated?.let { textures.release(it.id) }
    }
}

/** Each image is owned by exactly one texture. */
private class Frame(val image: NativeImage, val rotated: NativeImage?, val delayMs: Int)

/**
 * Streamed animated cape. The decoder thread owns [reader], [canvas] and [cursor] and fills [queue], the client thread
 * drains it in [identifier] and owns textures. The queue is bounded, so an undrained cape stops decoding.
 */
internal class AnimatedCape(
    private val base: String,
    private val source: ByteArray,
    private val frameCount: Int,
    private val width: Int,
    private val height: Int,
) : CapeEntry() {

    private val queue = ArrayBlockingQueue<Frame>(QUEUE_DEPTH)

    // Orientations requested so far. Sticky, since one cape can be worn upright and mirrored.
    @Volatile private var wantsNormal = false
    @Volatile private var wantsRotated = false

    /** Set by the client thread, the decoder frees its own state once it sees it. */
    @Volatile var dropped = false

    /** Not retried: the cape just freezes on its last frame. */
    @Volatile private var failed = false

    // Client thread only.
    private var normal: Variant? = null
    private var rotated: Variant? = null
    private var nextFrameAt = 0L

    // Decoder thread only.
    private var reader: ImageReader? = null
    private var input: ImageInputStream? = null
    private var canvas: BufferedImage? = null
    private var cursor = 0

    override fun identifier(rotated: Boolean): Identifier? {
        lastUsedMs = System.currentTimeMillis()
        if (rotated) wantsRotated = true else wantsNormal = true
        advance()
        return (if (rotated) this.rotated else this.normal)?.id
    }

    /** Client thread. */
    private fun advance() {
        val now = System.currentTimeMillis()
        // Never wait on the first frame.
        if ((normal != null || rotated != null) && now < nextFrameAt) return
        val frame = queue.poll() ?: return // Decoder behind: keep the current frame.
        show(frame)
        nextFrameAt = now + frame.delayMs
    }

    /**
     * [DynamicTexture.setPixels] takes ownership and closes the replaced image, so every image either goes to one
     * texture or gets closed. Otherwise a 20 fps cape leaks a frame per frame.
     */
    private fun show(frame: Frame) {
        /*
         * Rotate before handing off the normal image (that transfers ownership). Frames decoded before any mirrored
         * player have no rotated copy.
         */
        val rotatedImage = if (wantsRotated) frame.rotated ?: CapeImages.rotate(frame.image) else null
        if (!wantsRotated) frame.rotated?.close()
        rotated = fill(rotated, rotatedImage, rotated = true)

        if (wantsNormal) normal = fill(normal, frame.image, rotated = false) else frame.image.close()
    }

    /** Creates the texture on that orientation's first frame. */
    private fun fill(variant: Variant?, image: NativeImage?, rotated: Boolean): Variant? {
        if (image == null) return variant
        if (variant == null) return registerVariant(base, rotated, image)
        variant.texture.pixels = image
        variant.texture.upload()
        return variant
    }

    override fun releaseTextures() {
        val textures = Minecraft.getInstance().textureManager
        normal?.let { textures.release(it.id) }
        rotated?.let { textures.release(it.id) }
        normal = null
        rotated = null
    }

    /** Decoder thread: in view and room to decode ahead? */
    fun hungry(): Boolean = !failed &&
        System.currentTimeMillis() - lastUsedMs < IDLE_PAUSE_MS && queue.remainingCapacity() > 0

    /** Decoder thread. False on failure (the cape then freezes). */
    fun decodeNext(): Boolean {
        try {
            val reader = reader ?: openReader()
            // Looping restarts the canvas: frame 0 is a full frame.
            if (cursor >= frameCount) { cursor = 0; canvas = null }
            val canvas = canvas ?: BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).also { canvas = it }

            val frame = reader.read(cursor)
            val meta = CapeImages.frameMeta(reader, cursor)
            CapeImages.drawFrame(canvas, frame, meta)

            val image = CapeImages.toCapeTexture(canvas)
            // Rotating on the client would be a multi-ms loop per frame at 2048x1024.
            val rotatedImage = if (wantsRotated) CapeImages.rotate(image) else null
            queue.put(Frame(image, rotatedImage, meta.delayMs))

            if (meta.restoreToBackground) CapeImages.clearFrame(canvas, frame, meta)
            cursor++
            return true
        } catch (t: Throwable) {
            Log.main.error("Cape frame decode failed for {}", base, t)
            failed = true
            return false
        }
    }

    /** Decoder thread, after the entry is dropped. */
    fun disposeDecoder() {
        reader?.dispose()
        runCatching { input?.close() }
        reader = null
        input = null
        canvas = null
        // Native memory: closing is mandatory.
        generateSequence { queue.poll() }.forEach { it.image.close(); it.rotated?.close() }
    }

    private fun openReader(): ImageReader {
        val stream = ImageIO.createImageInputStream(ByteArrayInputStream(source))
        val reader = CapeImages.gifReader()
        reader.input = stream
        input = stream
        this.reader = reader
        return reader
    }
}
