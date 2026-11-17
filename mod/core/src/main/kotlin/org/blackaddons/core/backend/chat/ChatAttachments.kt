package org.blackaddons.core.backend.chat

import com.google.gson.JsonParser
import org.blackaddons.api.util.I18n
import org.blackaddons.core.backend.Backend
import org.blackaddons.core.backend.Http
import org.blackaddons.core.backend.chat.ChatAttachments.COMPRESS_ABOVE_BYTES
import org.blackaddons.core.backend.chat.ChatAttachments.MAX_EDGE
import org.blackaddons.core.backend.gateway.stringOrNull
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.math.roundToInt

/** Pictures upload before the message, which then references their hash. A refused picture doesn't lose the message. */
internal object ChatAttachments {
    private val tr = I18n.scope("blackaddons.bachat.attach")

    /** The backend checks the bytes. This only filters the file picker. */
    val EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "json")

    const val MAX_PER_MESSAGE = 4

    private const val MAX_BYTES = 11 * 1024 * 1024

    private const val MAX_READ_BYTES = 64L * 1024L * 1024L

    private val COMPRESSIBLE = setOf("png", "jpg", "jpeg")

    private const val COMPRESS_ABOVE_BYTES = 512 * 1024

    private const val MAX_EDGE = 2560

    private const val JPEG_QUALITY = 0.88f

    private const val ALPHA_SAMPLES = 64

    sealed interface Upload {
        data class Stored(val hash: String) : Upload
        data class Refused(val reason: String) : Upload
    }

    /** Uploads to the chat pool by default, or to a ticket's [url]. Blocking. */
    fun upload(path: Path, url: String = Backend.CHAT_URL + "/attachments", maxBytes: Int = MAX_BYTES): Upload {
        val size = runCatching { Files.size(path) }.getOrNull()
            ?: return Upload.Refused(tr("unreadable", "Could not read that file"))
        // Too big to read into memory, even if it could compress under the limit.
        if (size > MAX_READ_BYTES) return Upload.Refused(tooLarge(maxBytes))

        val prepared = runCatching { prepare(path) }.getOrNull()
            ?: return Upload.Refused(tr("unreadable", "Could not read that file"))
        if (prepared.bytes.size > maxBytes) return Upload.Refused(tooLarge(maxBytes))

        val boundary = "blackaddons-" + UUID.randomUUID()
        val response = Http.send(
            url,
            bytes = multipart(boundary, prepared),
            contentType = "multipart/form-data; boundary=$boundary",
            authenticated = true,
        ) ?: return Upload.Refused(tr("offline", "Could not reach the server"))

        if (response.status == 200 || response.status == 201) {
            val hash = runCatching { JsonParser.parseString(response.body).asJsonObject.stringOrNull("hash") }.getOrNull()
            return hash?.let(Upload::Stored) ?: Upload.Refused(tr("failed", "Upload failed"))
        }
        // The backend writes the text for its 400/403s. We write the rest.
        val said = response.error
        return Upload.Refused(
            when (response.status) {
                413 -> tooLarge(maxBytes)
                429 -> tr("quota", "That is enough pictures for today")
                400, 403 -> said ?: tr("failed", "Upload failed")
                else -> tr("failed", "Upload failed")
            },
        )
    }

    internal class Prepared(val bytes: ByteArray, val name: String, val contentType: String)

    private fun tooLarge(maxBytes: Int): String =
        tr("too_large", "Larger than %d MB").format(maxBytes / (1024 * 1024))

    /**
     * Opaque PNG/JPEG over [COMPRESS_ABOVE_BYTES] gets scaled to [MAX_EDGE] and encoded again as JPEG. The smaller
     * result wins. GIF, WebP, Lottie and transparent PNGs stay as they are.
     */
    internal fun prepare(path: Path): Prepared {
        val original = Prepared(Files.readAllBytes(path), path.name, contentType(path))
        val extension = path.extension.lowercase()
        if (extension !in COMPRESSIBLE || original.bytes.size <= COMPRESS_ABOVE_BYTES) return original

        val image = ImageIO.read(ByteArrayInputStream(original.bytes)) ?: return original
        if (image.colorModel.hasAlpha() && hasTransparency(image)) return original

        val scale = minOf(1f, MAX_EDGE.toFloat() / maxOf(image.width, image.height))
        val width = (image.width * scale).roundToInt().coerceAtLeast(1)
        val height = (image.height * scale).roundToInt().coerceAtLeast(1)
        // Opaque canvas, because the JPEG writer tints alpha channels.
        val opaque = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        opaque.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            drawImage(image, 0, 0, width, height, null)
            dispose()
        }

        val jpeg = ByteArrayOutputStream()
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        try {
            ImageIO.createImageOutputStream(jpeg).use { out ->
                writer.output = out
                val params = writer.defaultWriteParam.apply {
                    compressionMode = ImageWriteParam.MODE_EXPLICIT
                    compressionQuality = JPEG_QUALITY
                }
                writer.write(null, IIOImage(opaque, null, null), params)
            }
        } finally {
            writer.dispose()
        }
        val compressed = jpeg.toByteArray()
        if (compressed.size >= original.bytes.size) return original
        return Prepared(compressed, path.name.substringBeforeLast('.') + ".jpg", "image/jpeg")
    }

    // Game screenshots have an alpha channel but are opaque.
    private fun hasTransparency(image: BufferedImage): Boolean {
        val step = maxOf(1, minOf(image.width, image.height) / ALPHA_SAMPLES)
        for (y in 0 until image.height step step) {
            for (x in 0 until image.width step step) {
                if ((image.getRGB(x, y) ushr 24) != 0xFF) return true
            }
        }
        return false
    }

    private fun multipart(boundary: String, prepared: Prepared): ByteArray = ByteArrayOutputStream(prepared.bytes.size + 512).apply {
        // Quotes or line breaks would break the header.
        val name = prepared.name.replace(Regex("[\"\\r\\n]"), "_")
        write(
            ("--$boundary\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"$name\"\r\n" +
                "Content-Type: ${prepared.contentType}\r\n\r\n").toByteArray(),
        )
        write(prepared.bytes)
        write("\r\n--$boundary--\r\n".toByteArray())
    }.toByteArray()

    private fun contentType(path: Path): String = when (path.extension.lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "json" -> "application/json"
        else -> "application/octet-stream"
    }
}
