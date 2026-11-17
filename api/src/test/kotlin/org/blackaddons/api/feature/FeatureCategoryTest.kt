package org.blackaddons.api.feature

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FeatureCategoryTest {

    @Test
    fun `a section carries the category that declared it`() {
        assertSame(Auras, Auras.Combat.category)
        assertEquals(listOf(Auras.Combat, Auras.Utility), Auras.sections)
    }

    @Test
    fun `the first declared section is the default, and a flat category has none`() {
        assertSame(Auras.Combat, Auras.defaultSection)
        assertNull(Flat.defaultSection)
        assertTrue(Flat.sections.isEmpty())
    }

    @Test
    fun `ids are lowercased so a package segment always matches`() {
        assertEquals("mixedcase", MixedCase.id)
        assertEquals("floor7", MixedCase.Floor7.id)
    }

    @Test
    fun `host categories are registered up front, and nothing else is`() {
        assertSame(Categories.Dungeons, CategoryRegistry.of("dungeons"))
        assertSame(Categories.Interface, CategoryRegistry.of("ui"))
        assertNull(CategoryRegistry.addonIdOf(Categories.Dungeons))
        assertNull(CategoryRegistry.of("definitely-not-declared"))
    }

    @Test
    fun `registering an addon category claims the id once`() {
        assertTrue(CategoryRegistry.register("first-addon", listOf(FirstClaim)).isEmpty())
        assertSame(FirstClaim, CategoryRegistry.of("test-claimed"))
        assertEquals("first-addon", CategoryRegistry.addonIdOf(FirstClaim))

        assertEquals(listOf(SecondClaim), CategoryRegistry.register("second-addon", listOf(SecondClaim)))
        assertSame(FirstClaim, CategoryRegistry.of("test-claimed"))
    }

    @Test
    fun `a host id cannot be claimed by an addon`() {
        assertEquals(listOf(Impostor), CategoryRegistry.register("rude-addon", listOf(Impostor)))
        assertSame(Categories.Dungeons, CategoryRegistry.of("dungeons"))
    }

    @Test
    fun `a feature takes the category of the section it names`() {
        assertSame(Auras, Sectioned.category)
        assertSame(Auras.Utility, Sectioned.effectiveSection)
    }

    @Test
    fun `a feature with no section falls into its category's default`() {
        assertSame(Auras, Unsectioned.category)
        assertSame(Auras.Combat, Unsectioned.effectiveSection)
    }

    @Test
    fun `an addon category joins the registry the host seeded`() {
        assertNull(CategoryRegistry.of("test-late"))
        CategoryRegistry.register("late-addon", listOf(LateArrival))
        assertSame(LateArrival, CategoryRegistry.of("test-late"))
        assertEquals("late-addon", CategoryRegistry.addonIdOf(LateArrival))
        assertTrue(CategoryRegistry.all.containsAll(Categories.builtIns))
    }

    @Test
    fun `an undeclared package segment is refused, not minted`() {
        // No `impl` segment here, the same refusal path a typo takes.
        val thrown = assertThrows<IllegalArgumentException> { CategoryRegistry.infer(Unrelated::class.java) }
        assertNotNull(thrown.message)
        assertNull(CategoryRegistry.of("feature"))
    }

    private object Auras : FeatureCategory("auras") {
        val Combat = section("combat", "Combat")
        val Utility = section("utility", "Utility")
    }

    private object Flat : FeatureCategory("flat")

    private object MixedCase : FeatureCategory("MixedCase") {
        val Floor7 = section("Floor7", "Floor 7")
    }

    private object FirstClaim : FeatureCategory("test-claimed")
    private object SecondClaim : FeatureCategory("test-claimed")
    private object Impostor : FeatureCategory("dungeons")
    private object LateArrival : FeatureCategory("test-late")

    private object Sectioned : Feature() {
        override val section = Auras.Utility
    }

    private object Unsectioned : Feature() {
        override val category = Auras
    }

    private class Unrelated
}
