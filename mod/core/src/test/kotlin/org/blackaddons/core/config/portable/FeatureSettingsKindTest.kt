package org.blackaddons.core.config.portable

import org.blackaddons.api.feature.Feature
import org.blackaddons.api.util.io.Portable
import org.blackaddons.api.util.io.PortableRead
import org.blackaddons.api.util.io.child
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Color

class FeatureSettingsKindTest {

    @Test
    fun `the preview lists what changes and leaves the settings alone until applied`() {
        val source = Sample().apply {
            flag.value = true
            range.value = 7.0
        }
        val target = Sample()

        val preview = preview(target, FeatureSettingsKind.export(source)) as ImportPreview.Ready

        assertEquals(listOf("Flag", "Range"), preview.rows.map { it.label })
        assertFalse(target.flag.value)
        preview.apply()
        assertTrue(target.flag.value)
        assertEquals(7.0, target.range.value)
    }

    @Test
    fun `settings that are already the same leave nothing to apply`() {
        val preview = preview(Sample(), FeatureSettingsKind.export(Sample())) as ImportPreview.Ready

        assertTrue(preview.rows.isEmpty())
        assertFalse(preview.canApply)
    }

    @Test
    fun `previewing a chroma color does not leave chroma on`() {
        val source = Sample().apply { tint.chroma = true }
        val target = Sample()

        preview(target, FeatureSettingsKind.export(source))

        assertFalse(target.tint.chroma)
    }

    @Test
    fun `a masked field neither leaves nor gets overwritten`() {
        val exported = FeatureSettingsKind.export(Sample().apply { token.value = "secret" })
        val settings = (Portable.decode(exported) as PortableRead.Document).json.child("settings")!!
        assertFalse(settings.has("Token"))

        val target = Sample().apply { token.value = "mine" }
        (preview(target, exported) as ImportPreview.Ready).apply()
        assertEquals("mine", target.token.value)
    }

    @Test
    fun `settings of another feature are refused`() {
        assertInstanceOf(ImportPreview.Failed::class.java, preview(Other(), FeatureSettingsKind.export(Sample())))
    }

    private fun preview(target: Feature, text: String): ImportPreview<Feature> =
        FeatureSettingsKind(target).preview((Portable.decode(text) as PortableRead.Document).json)

    private class Sample : Feature() {
        val flag by boolean("Flag")
        val range by number("Range", 4.0, 1.0, 8.0)
        val tint by color("Tint", Color.WHITE)
        val token by string("Token", "", isMasked = true)
    }

    private class Other : Feature() {
        @Suppress("unused")
        val flag by boolean("Flag")
    }
}
