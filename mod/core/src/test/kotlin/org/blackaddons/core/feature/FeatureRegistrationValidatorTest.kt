/**
 * In the same package as the class under test. Categories resolve lazily, so features outside
 * `.features.impl.<category>` construct fine.
 */
package org.blackaddons.core.feature

import org.blackaddons.api.feature.Feature
import org.blackaddons.api.feature.settings.impl.BooleanSetting
import org.blackaddons.api.hud.HudElement
import org.blackaddons.api.ui.layout.LayoutNode
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FeatureRegistrationValidatorTest {

    @Test
    fun `feature ids are unique per addon`() {
        val validator = FeatureRegistrationValidator()

        assertNull(validator.rejectionFor(TestFeature("Shared"), "first-addon"))
        assertNull(validator.rejectionFor(TestFeature("Shared"), "second-addon"))
        assertNotNull(validator.rejectionFor(TestFeature("Shared"), "first-addon"))
    }

    @Test
    fun `duplicate setting and HUD ids are rejected`() {
        val validator = FeatureRegistrationValidator()
        val duplicateSettings = TestFeature("Settings").apply {
            registerSetting(BooleanSetting("First", id = "same"))
            registerSetting(BooleanSetting("Second", id = "same"))
        }
        val duplicateHuds = TestFeature("HUDs").apply {
            hudElements += TestHud("Shared HUD")
            hudElements += TestHud("Shared HUD")
        }

        assertNotNull(validator.rejectionFor(duplicateSettings, "test-addon"))
        assertNotNull(validator.rejectionFor(duplicateHuds, "test-addon"))
    }

    private class TestFeature(override val id: String) : Feature()

    private class TestHud(override val name: String) : HudElement() {
        override fun content(example: Boolean): LayoutNode = error("Not rendered in this test")
    }
}
