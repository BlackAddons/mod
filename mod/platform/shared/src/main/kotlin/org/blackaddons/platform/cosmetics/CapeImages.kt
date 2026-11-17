package org.blackaddons.platform.cosmetics

import com.mojang.blaze3d.platform.NativeImage
import org.blackaddons.platform.cosmetics.CapeImages.drawFrame
import org.blackaddons.platform.cosmetics.CapeImages.fillEmptyBacking
import java.awt.AlphaComposite
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.IOException
import javax.imageio.ImageIO
import javax.imageio.ImageReader
import javax.imageio.metadata.IIOMetadataNode

/** A non-multiple of vanilla's 64 puts the model's UVs off-texel. */
private const val VANILLA_SHEET_WIDTH = 64

/**
 * The cape face is 10x16 of vanilla's 64x32, so 1024x512 already gives about one texel per screen pixel up close.
 * 2048x1024 is a ceiling, not a goal (nearest sampling just shimmers).
 */
private const val MAX_WIDTH = 2048
private const val MIN_WIDTH = 64

/** Floor for a GIF frame's on-screen time, so a 0ms delay doesn't spin. */
private const val MIN_FRAME_MS = 20

/**
 * Neutral gray for unpainted faces, never sampled (undrawn lining is backing, not a guess). Works with any channel
 * order.
 */
private const val BACKING_COLOR = 0xFF6A6A6A.toInt()

private class Face(val x: Int, val y: Int, val w: Int, val h: Int)

/** Every face except the front (the author's canvas, we never touch it). */
private val BACKING_FACES = listOf(
    Face(12, 1, 10, 16), // lining
    Face(0, 1, 1, 16),   // left edge
    Face(11, 1, 1, 16),  // right edge
    Face(1, 0, 10, 1),   // top edge
    Face(11, 0, 10, 1),  // bottom edge
)

internal class FrameMeta(
    val x: Int,
    val y: Int,
    val delayMs: Int,
    val restoreToBackground: Boolean,
)

/** Cape images: byte format, GIF frames and timing via ImageIO, the mirrored sheet. Pure pixels, safe on any worker. */
internal object CapeImages {

    fun gifReader(): ImageReader = ImageIO.getImageReadersBySuffix("gif").let {
        if (it.hasNext()) it.next() else throw IOException("No GIF reader")
    }

    fun decodePng(bytes: ByteArray): BufferedImage {
        val image = ImageIO.read(ByteArrayInputStream(bytes))
            ?: throw IOException("Unreadable cape image")
        requireValidSize(image.width, image.height)
        return image
    }

    /** 2:1, a multiple of vanilla's layout, in range. The frontend converts. */
    fun requireValidSize(w: Int, h: Int) {
        if (w != h * 2 || w !in MIN_WIDTH..MAX_WIDTH || w % VANILLA_SHEET_WIDTH != 0) {
            throw IOException("Cape ${w}x$h is not a valid 2:1 size")
        }
    }

    fun frameMeta(reader: ImageReader, index: Int): FrameMeta {
        val root = reader.getImageMetadata(index)
            .getAsTree("javax_imageio_gif_image_1.0") as IIOMetadataNode
        val descriptor = child(root, "ImageDescriptor")
        val gce = child(root, "GraphicControlExtension")
        val x = descriptor?.getAttribute("imageLeftPosition")?.toIntOrNull() ?: 0
        val y = descriptor?.getAttribute("imageTopPosition")?.toIntOrNull() ?: 0
        // GIF delay is in centiseconds.
        val delayMs = (gce?.getAttribute("delayTime")?.toIntOrNull() ?: 10) * 10
        val restore = gce?.getAttribute("disposalMethod") == "restoreToBackgroundColor"
        return FrameMeta(x, y, delayMs.coerceAtLeast(MIN_FRAME_MS), restore)
    }

    /** The GIF's declared canvas, or its first frame's size. */
    fun screenSize(reader: ImageReader, firstFrame: BufferedImage): Pair<Int, Int> {
        val tree = runCatching {
            reader.streamMetadata?.getAsTree("javax_imageio_gif_stream_1.0") as? IIOMetadataNode
        }.getOrNull()
        val descriptor = tree?.let { child(it, "LogicalScreenDescriptor") }
        val w = descriptor?.getAttribute("logicalScreenWidth")?.toIntOrNull() ?: firstFrame.width
        val h = descriptor?.getAttribute("logicalScreenHeight")?.toIntOrNull() ?: firstFrame.height
        return w to h
    }

    fun drawFrame(canvas: BufferedImage, frame: BufferedImage, meta: FrameMeta) {
        canvas.createGraphics().apply { drawImage(frame, meta.x, meta.y, null); dispose() }
    }

    /** Undoes [drawFrame] for clear-background disposal. */
    fun clearFrame(canvas: BufferedImage, frame: BufferedImage, meta: FrameMeta) {
        canvas.createGraphics().apply {
            composite = AlphaComposite.Clear
            fillRect(meta.x, meta.y, frame.width, frame.height)
            dispose()
        }
    }

    /** Every cape texture goes through here, so the backing ([fillEmptyBacking]) can't get skipped. */
    fun toCapeTexture(image: BufferedImage): NativeImage {
        val w = image.width
        val h = image.height
        val out = NativeImage(w, h, false)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val argb = image.getRGB(x, y)
                val a = (argb ushr 24) and 0xFF
                val r = (argb ushr 16) and 0xFF
                val g = (argb ushr 8) and 0xFF
                val b = argb and 0xFF
                // NativeImage stores ABGR.
                out.setPixelABGR(x, y, (a shl 24) or (b shl 16) or (g shl 8) or r)
            }
        }
        fillEmptyBacking(out)
        return out
    }

    /**
     * Fills fully transparent non-front faces with [BACKING_COLOR]: `entitySolid` discards transparent texels, so an
     * unpainted lining turns into a window to the world. Partly transparent faces are design and stay.
     */
    private fun fillEmptyBacking(image: NativeImage) {
        val scale = image.width / VANILLA_SHEET_WIDTH
        for (face in BACKING_FACES) {
            val x = face.x * scale
            val y = face.y * scale
            val w = face.w * scale
            val h = face.h * scale
            if (isTransparent(image, x, y, w, h)) image.fillRect(x, y, w, h, BACKING_COLOR)
        }
    }

    private fun isTransparent(image: NativeImage, x: Int, y: Int, w: Int, h: Int): Boolean {
        for (py in y until y + h) {
            for (px in x until x + w) {
                // Alpha is the top byte either way.
                if ((image.getPixel(px, py) ushr 24) != 0) return false
            }
        }
        return true
    }

    /**
     * Copy with the artwork turned 180 degrees, for players mirrored on Y. Rotated per face, not the whole sheet: the
     * model samples fixed rectangles, so spinning the sheet would sample empty pixels. Face and lining turn in place,
     * opposite edge strips swap.
     */
    fun rotate(source: NativeImage): NativeImage {
        val out = NativeImage(source.width, source.height, false)
        source.copyRect(out, 0, 0, 0, 0, source.width, source.height, false, false)

        val scale = source.width / VANILLA_SHEET_WIDTH
        fun spin(fromX: Int, fromY: Int, toX: Int, toY: Int, w: Int, h: Int) = source.copyRect(
            out, fromX * scale, fromY * scale, toX * scale, toY * scale, w * scale, h * scale, true, true,
        )

        spin(1, 1, 1, 1, 10, 16)    // the cape's face, turned in place
        spin(12, 1, 12, 1, 10, 16)  // lining, likewise
        spin(0, 1, 11, 1, 1, 16)    // left <-> right
        spin(11, 1, 0, 1, 1, 16)
        spin(1, 0, 11, 0, 10, 1)    // top <-> bottom
        spin(11, 0, 1, 0, 10, 1)
        return out
    }

    private fun child(parent: IIOMetadataNode, name: String): IIOMetadataNode? {
        val list = parent.getElementsByTagName(name)
        return if (list.length > 0) list.item(0) as IIOMetadataNode else null
    }
}
