package org.blackaddons.platform.font

import com.mojang.blaze3d.font.GlyphInfo
import com.mojang.blaze3d.font.UnbakedGlyph
import org.blackaddons.api.util.concurrent.Workers
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

internal data class BakedStyles(
    val regular: NativeBakedStyle,
    val bold: LazyBakedValue<NativeBakedStyle>?,
    val native: Boolean,
) {
    fun select(bold: Boolean): NativeBakedStyle = if (bold) this.bold?.get() ?: regular else regular
}

internal class LazyBakedValue<T>(private var factory: (() -> T)?) {
    private var value: T? = null

    fun get(): T = value ?: requireNotNull(factory).invoke().also {
        value = it
        factory = null
    }
}

internal interface WorldStyles {
    fun poll(bold: Boolean, start: Boolean): BakedStyles?
    fun get(bold: Boolean): BakedStyles
}

internal class SyncWorldStyles(private val styles: LazyBakedValue<BakedStyles>) : WorldStyles {
    override fun poll(bold: Boolean, start: Boolean): BakedStyles = styles.get()
    override fun get(bold: Boolean): BakedStyles = styles.get()
}

internal class BackgroundWorldStyles(
    private val prepare: (bold: Boolean) -> PreparedStyle,
    private val info: GlyphInfo,
    private val stitcher: UnbakedGlyph.Stitcher,
) : WorldStyles {
    private val jobs = arrayOfNulls<CompletableFuture<PreparedStyle>>(2)
    private val styles = arrayOfNulls<BakedStyles>(2)

    override fun poll(bold: Boolean, start: Boolean): BakedStyles? {
        val slot = if (bold) 1 else 0
        styles[slot]?.let { return it }
        val job = jobs[slot]
            ?: if (start) CompletableFuture.supplyAsync({ prepare(bold) }, BAKER).also { jobs[slot] = it } else return null
        return if (job.isDone) finish(slot, job) else null
    }

    override fun get(bold: Boolean): BakedStyles {
        val slot = if (bold) 1 else 0
        return styles[slot] ?: finish(slot, jobs[slot] ?: CompletableFuture.completedFuture(prepare(bold)))
    }

    private fun finish(slot: Int, job: CompletableFuture<PreparedStyle>): BakedStyles {
        jobs[slot] = null
        return BakedStyles(job.join().stitch(info, stitcher), null, native = true).also { styles[slot] = it }
    }

    private companion object {
        val BAKER: ExecutorService = Workers.single("font-baker")
    }
}
