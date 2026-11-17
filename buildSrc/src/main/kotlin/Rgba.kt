/*
 * Resize and quantize ported from Pillow 12.3 so the sheets match the Pillow-made ones pixel for pixel:
 * Resample.c, Convert.c and Quant.c (HPND, (c) Secret Labs AB, Fredrik Lundh, Jeffrey A. Clark and contributors),
 * QuantOctree.c (MIT, (c) 2010 Oliver Tonnhofer, Omniscale).
 */

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.sin

internal class Rgba(val width: Int, val height: Int, val channels: IntArray = IntArray(width * height * 4)) {
    fun paste(image: Rgba, x: Int, y: Int) {
        for (row in 0 until image.height) {
            image.channels.copyInto(channels, ((y + row) * width + x) * 4, row * image.width * 4, (row + 1) * image.width * 4)
        }
    }

    fun resizeLanczos(outWidth: Int, outHeight: Int): Rgba {
        var image = Rgba(width, height, IntArray(channels.size) { i ->
            if (i % 4 == 3) channels[i] else (channels[i] * channels[i - i % 4 + 3] + 128).let { ((it shr 8) + it) shr 8 }
        })
        if (outWidth != width) image = image.resample(outWidth, horizontal = true)
        if (outHeight != height) image = image.resample(outHeight, horizontal = false)
        for (i in image.channels.indices step 4) {
            val alpha = image.channels[i + 3]
            if (alpha == 0 || alpha == 255) continue
            for (c in 0..2) image.channels[i + c] = (255 * image.channels[i + c] / alpha).coerceAtMost(255)
        }
        return image
    }

    private fun resample(outSize: Int, horizontal: Boolean): Rgba {
        val (ksize, bounds, kk) = lanczosCoefficients(if (horizontal) width else height, outSize)
        val out = if (horizontal) Rgba(outSize, height) else Rgba(width, outSize)
        for (y in 0 until out.height) {
            for (x in 0 until out.width) {
                val o = if (horizontal) x else y
                val min = bounds[o * 2]
                for (c in 0..3) {
                    var ss = 1 shl (PRECISION_BITS - 1)
                    for (k in 0 until bounds[o * 2 + 1]) {
                        val source = if (horizontal) y * width + min + k else (min + k) * width + x
                        ss += channels[source * 4 + c] * kk[o * ksize + k]
                    }
                    out.channels[(y * out.width + x) * 4 + c] = (ss shr PRECISION_BITS).coerceIn(0, 255)
                }
            }
        }
        return out
    }

    fun quantizeOctree(colors: Int): Indexed {
        val pixels = channels.copyOf()
        val firstTransparent = (pixels.indices step 4).firstOrNull { pixels[it + 3] == 0 }
        if (firstTransparent != null) {
            for (i in pixels.indices step 4) {
                if (pixels[i + 3] == 0) pixels.copyInto(pixels, i, firstTransparent, firstTransparent + 3)
            }
        }

        val fine = ColorCube(FINE_BITS)
        for (i in pixels.indices step 4) fine.bucketOf(pixels, i).add(pixels, i)
        val coarse = fine.copy(COARSE_BITS)
        var coarseColors = minOf(coarse.used(), colors)
        var fineColors = colors - coarseColors
        val fineSorted = fine.sorted()
        coarse.subtract(fineSorted, 0, fineColors)
        while (coarseColors > coarse.used()) {
            val alreadySubtracted = fineColors
            coarseColors = coarse.used()
            fineColors = colors - coarseColors
            coarse.subtract(fineSorted, alreadySubtracted, fineColors - alreadySubtracted)
        }
        val palette = coarse.sorted().take(coarseColors) + fineSorted.take(fineColors)

        val coarseLookup = ColorCube(COARSE_BITS)
        coarseLookup.addLookup(palette, coarseColors, 0)
        val lookup = coarseLookup.copy(FINE_BITS)
        lookup.addLookup(palette, fineColors, coarseColors)

        return Indexed(
            width,
            height,
            ByteArray(width * height) { lookup.bucketOf(pixels, it * 4).count.toByte() },
            palette.flatMap { it.average().asList() }.toIntArray(),
        )
    }

    private class Bucket {
        var count = 0L
        val sums = LongArray(4)

        fun add(pixels: IntArray, at: Int) {
            count++
            for (c in 0..3) sums[c] += pixels[at + c].toLong()
        }

        fun add(other: Bucket, sign: Int = 1) {
            count += sign * other.count
            for (c in 0..3) sums[c] += sign * other.sums[c]
        }

        fun average(): IntArray =
            if (count == 0L) IntArray(4)
            else IntArray(4) { (sums[it].toFloat() / count.toFloat()).toInt().coerceIn(0, 255) }
    }

    private class ColorCube(val bits: IntArray) {
        val buckets = Array(1 shl bits.sum()) { Bucket() }

        fun offset(r: Int, g: Int, b: Int, a: Int): Int =
            (r shl (bits[1] + bits[2] + bits[3])) or (g shl (bits[2] + bits[3])) or (b shl bits[3]) or a

        fun bucketOf(pixels: IntArray, at: Int = 0): Bucket = buckets[
            offset(
                pixels[at] shr (8 - bits[0]),
                pixels[at + 1] shr (8 - bits[1]),
                pixels[at + 2] shr (8 - bits[2]),
                pixels[at + 3] shr (8 - bits[3]),
            ),
        ]

        fun used(): Int = buckets.count { it.count > 0 }

        fun sorted(): List<Bucket> = buckets.sortedByDescending { it.count }

        fun copy(toBits: IntArray): ColorCube {
            val result = ColorCube(toBits)
            val sourceReduce = IntArray(4) { maxOf(toBits[it] - bits[it], 0) }
            val targetReduce = IntArray(4) { maxOf(bits[it] - toBits[it], 0) }
            val widths = IntArray(4) { 1 shl maxOf(bits[it], toBits[it]) }
            for (r in 0 until widths[0]) for (g in 0 until widths[1]) for (b in 0 until widths[2]) for (a in 0 until widths[3]) {
                val source = buckets[offset(r shr sourceReduce[0], g shr sourceReduce[1], b shr sourceReduce[2], a shr sourceReduce[3])]
                result.buckets[
                    result.offset(r shr targetReduce[0], g shr targetReduce[1], b shr targetReduce[2], a shr targetReduce[3]),
                ].add(source)
            }
            return result
        }

        fun subtract(sorted: List<Bucket>, from: Int, count: Int) {
            for (i in from until from + count) {
                val subtrahend = sorted[i]
                if (subtrahend.count == 0L) continue
                bucketOf(subtrahend.average()).add(subtrahend, sign = -1)
            }
        }

        fun addLookup(palette: List<Bucket>, colors: Int, offset: Int) {
            for (i in offset + colors - 1 downTo offset) bucketOf(palette[i].average()).count = i.toLong()
        }
    }

    companion object {
        private const val PRECISION_BITS = 32 - 8 - 2
        private val FINE_BITS = intArrayOf(3, 4, 3, 3)
        private val COARSE_BITS = intArrayOf(2, 2, 2, 2)

        fun read(png: ByteArray): Rgba {
            val image = ImageIO.read(ByteArrayInputStream(png))
            val argb = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
            return Rgba(image.width, image.height, IntArray(argb.size * 4) { i ->
                val pixel = argb[i / 4]
                when (i % 4) {
                    0 -> pixel shr 16 and 0xFF
                    1 -> pixel shr 8 and 0xFF
                    2 -> pixel and 0xFF
                    else -> pixel ushr 24
                }
            })
        }

        private fun lanczosCoefficients(inSize: Int, outSize: Int): Triple<Int, IntArray, IntArray> {
            val scale = inSize.toDouble() / outSize
            val filterScale = maxOf(scale, 1.0)
            val inverseFilterScale = 1.0 / filterScale
            val support = 3.0 * filterScale
            val ksize = ceil(support).toInt() * 2 + 1
            val bounds = IntArray(outSize * 2)
            val kk = IntArray(outSize * ksize)
            for (xx in 0 until outSize) {
                val center = (xx + 0.5) * scale
                val min = maxOf((center - support + 0.5).toInt(), 0)
                val count = minOf((center + support + 0.5).toInt(), inSize) - min
                val weights = DoubleArray(count) { lanczos((it + min - center + 0.5) * inverseFilterScale) }
                val total = weights.sum()
                for (x in 0 until count) {
                    val weight = if (total != 0.0) weights[x] / total else weights[x]
                    kk[xx * ksize + x] = (if (weight < 0) -0.5 + weight * (1 shl PRECISION_BITS) else 0.5 + weight * (1 shl PRECISION_BITS)).toInt()
                }
                bounds[xx * 2] = min
                bounds[xx * 2 + 1] = count
            }
            return Triple(ksize, bounds, kk)
        }

        private fun lanczos(x: Double): Double = if (-3.0 <= x && x < 3.0) sinc(x) * sinc(x / 3) else 0.0

        private fun sinc(x: Double): Double = if (x == 0.0) 1.0 else (x * PI).let { sin(it) / it }
    }
}

internal class Indexed(val width: Int, val height: Int, val indices: ByteArray, val palette: IntArray) {
    /* ImageIO filters palette rows adaptively, so they come out a third bigger than Pillow's unfiltered rows. */
    fun write(file: File) {
        val rows = ByteArrayOutputStream()
        for (y in 0 until height) {
            rows.write(0)
            rows.write(indices, y * width, width)
        }
        DataOutputStream(file.outputStream().buffered()).use { out ->
            out.write(byteArrayOf(-119, 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10))
            out.chunk("IHDR", ByteBuffer.allocate(13).putInt(width).putInt(height).put(8).put(3).put(0).put(0).put(0).array())
            out.chunk("PLTE", ByteArray(palette.size / 4 * 3) { palette[it / 3 * 4 + it % 3].toByte() })
            out.chunk("tRNS", ByteArray(palette.size / 4) { palette[it * 4 + 3].toByte() })
            out.chunk("IDAT", compressed(rows.toByteArray()))
            out.chunk("IEND", ByteArray(0))
        }
    }

    private fun compressed(data: ByteArray): ByteArray {
        val deflater = Deflater(9)
        val out = ByteArrayOutputStream()
        DeflaterOutputStream(out, deflater).use { it.write(data) }
        deflater.end()
        return out.toByteArray()
    }

    private fun DataOutputStream.chunk(type: String, data: ByteArray) {
        val crc = CRC32()
        crc.update(type.toByteArray())
        crc.update(data)
        writeInt(data.size)
        writeBytes(type)
        write(data)
        writeInt(crc.value.toInt())
    }
}
