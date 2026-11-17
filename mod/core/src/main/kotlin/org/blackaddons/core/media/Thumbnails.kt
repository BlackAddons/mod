package org.blackaddons.core.media

import org.blackaddons.api.Platform
import org.blackaddons.api.ui.render.Render
import org.blackaddons.api.util.Hashes
import org.blackaddons.api.util.concurrent.Workers
import org.blackaddons.core.log.Log
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import kotlin.io.path.getLastModifiedTime

/**
 * Cover-cropped previews, cached on disk by path, mtime and size, built one at a time on a worker. Formats ImageIO
 * can't read (WebP, Lottie) get none.
 */
internal object Thumbnails {

    private val folder: Path get() = Platform.configDir.resolve("cache").resolve("thumbnails")

    private val worker = Workers.single("thumbnails")
    private val ready = ConcurrentHashMap<String, String>()
    private val pending = ConcurrentHashMap.newKeySet<String>()
    private val failed = ConcurrentHashMap.newKeySet<String>()

    private val aspects = ConcurrentHashMap<Path, Float>()

    /** Null while building or when impossible. Asking starts it. [onReady] runs on the worker. */
    fun request(path: Path, width: Int, height: Int, onReady: (String) -> Unit = {}): String? {
        val key = key(path, width, height) ?: return null
        ready[key]?.let { return it }
        if (key in failed || !pending.add(key)) return null
        worker.execute {
            try {
                val made = build(path, key, width, height)
                if (made == null) failed += key else {
                    ready[key] = made
                    onReady(made)
                }
            } finally {
                pending.remove(key)
            }
        }
        return null
    }

    fun aspect(path: Path): Float? = aspects[path]

    fun forget(path: Path) {
        aspects.remove(path)
        val prefix = hash(path.toAbsolutePath().toString()) + "-"
        ready.keys.filter { it.startsWith(prefix) }.forEach { key ->
            ready.remove(key)?.let { runCatching { Files.deleteIfExists(Path.of(it.removePrefix(Render.FILE_SCHEME))) } }
        }
    }

    private fun build(path: Path, key: String, width: Int, height: Int): String? = runCatching {
        val target = folder.resolve("$key.png")
        if (Files.exists(target)) {
            // Built in an earlier session, but still needs its aspect.
            if (!aspects.containsKey(path)) readAspect(path)?.let { aspects[path] = it }
            return@runCatching Render.FILE_SCHEME + target.toAbsolutePath()
        }
        val source = ImageIO.read(path.toFile()) ?: return@runCatching null
        aspects[path] = source.width.toFloat() / source.height

        val scale = maxOf(width.toFloat() / source.width, height.toFloat() / source.height)
        val cropW = (width / scale).toInt().coerceAtMost(source.width)
        val cropH = (height / scale).toInt().coerceAtMost(source.height)
        val thumb = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        thumb.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            val sx = (source.width - cropW) / 2
            val sy = (source.height - cropH) / 2
            drawImage(source, 0, 0, width, height, sx, sy, sx + cropW, sy + cropH, null)
            dispose()
        }
        Files.createDirectories(folder)
        ImageIO.write(thumb, "png", target.toFile())
        Render.FILE_SCHEME + target.toAbsolutePath()
    }.onFailure { Log.main.warn("Could not build a preview for {}", path, it) }.getOrNull()

    private fun readAspect(path: Path): Float? = runCatching {
        ImageIO.createImageInputStream(path.toFile()).use { stream ->
            val reader = ImageIO.getImageReaders(stream).asSequence().firstOrNull() ?: return null
            try {
                reader.input = stream
                reader.getWidth(0).toFloat() / reader.getHeight(0)
            } finally {
                reader.dispose()
            }
        }
    }.getOrNull()

    // Path hash first, so forget() finds every size.
    private fun key(path: Path, width: Int, height: Int): String? {
        val modified = runCatching { path.getLastModifiedTime().toMillis() }.getOrNull() ?: return null
        return hash(path.toAbsolutePath().toString()) + "-" + hash("$modified:${width}x$height")
    }

    private fun hash(text: String): String =
        Hashes.hex(MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).copyOf(10))
}
