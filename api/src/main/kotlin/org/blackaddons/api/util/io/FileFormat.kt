package org.blackaddons.api.util.io
import java.nio.file.Files
import java.nio.file.Path

/** Detected from headers, not file names. */
enum class FileFormat(val isImage: Boolean = false, val isFont: Boolean = false) {
    PNG(isImage = true),
    JPEG(isImage = true),
    GIF(isImage = true),
    WEBP(isImage = true),
    TRUE_TYPE(isFont = true),
    OPEN_TYPE_CFF(isFont = true),
    ;

    companion object {
        private const val HEADER_SIZE = 12

        /** For file pickers. */
        val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif")

        /** Absolute path to a regular file with an image header. */
        fun isImageFile(path: Path): Boolean =
            runCatching { path.isAbsolute && Files.isRegularFile(path) && detect(path)?.isImage == true }.getOrDefault(false)

        fun detect(path: Path): FileFormat? =
            Files.newInputStream(path).use { detect(it.readNBytes(HEADER_SIZE)) }

        fun detect(bytes: ByteArray): FileFormat? = when {
            bytes.has(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> PNG
            bytes.has(0xFF, 0xD8, 0xFF) -> JPEG
            bytes.hasAscii(0, "GIF87a") || bytes.hasAscii(0, "GIF89a") -> GIF
            bytes.hasAscii(0, "RIFF") && bytes.hasAscii(8, "WEBP") -> WEBP
            bytes.has(0x00, 0x01, 0x00, 0x00) || bytes.hasAscii(0, "true") || bytes.hasAscii(0, "ttcf") -> TRUE_TYPE
            bytes.hasAscii(0, "OTTO") -> OPEN_TYPE_CFF
            else -> null
        }
    }
}

private fun ByteArray.has(vararg signature: Int): Boolean =
    size >= signature.size && signature.indices.all { this[it].toInt() and 0xFF == signature[it] }

private fun ByteArray.hasAscii(offset: Int, signature: String): Boolean =
    size >= offset + signature.length && signature.indices.all { this[offset + it].toInt() == signature[it].code }
