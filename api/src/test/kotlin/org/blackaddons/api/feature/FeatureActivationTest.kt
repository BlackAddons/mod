package org.blackaddons.api.feature

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FeatureActivationTest {

    @Test
    fun `onEnable that throws leaves the feature off, and a retry runs it again`() {
        val feature = FlakyFeature(failEnable = true)

        feature.toggle()

        assertFalse(feature.enabled)
        assertEquals(1, feature.enables)

        feature.failEnable = false
        feature.toggle()

        assertTrue(feature.enabled)
        assertEquals(2, feature.enables)
    }

    @Test
    fun `onDisable that throws still turns the feature off`() {
        val feature = FlakyFeature(failDisable = true)
        feature.toggle()

        feature.toggle()

        assertFalse(feature.enabled)
        // Really off: re-enabling runs onEnable again.
        feature.toggle()
        assertEquals(2, feature.enables)
    }

    private class FlakyFeature(var failEnable: Boolean = false, val failDisable: Boolean = false) : Feature() {
        var enables = 0

        override fun onEnable() {
            enables++
            if (failEnable) error("enable failed")
        }

        override fun onDisable() {
            if (failDisable) error("disable failed")
        }
    }
}
