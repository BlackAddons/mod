package org.blackaddons.core.update

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** String comparison gets `1.4.10` vs `1.4.9` wrong, which would nag everyone on the newest build. */
class ModVersionTest {

    private infix fun String.isOlderThan(other: String) =
        ModVersion.parse(this)!! < ModVersion.parse(other)!!

    private infix fun String.isSameAs(other: String) =
        ModVersion.parse(this)!!.compareTo(ModVersion.parse(other)!!) == 0

    @Test
    fun `a double-digit patch beats a single-digit one`() {
        assertTrue("1.4.9" isOlderThan "1.4.10")
    }

    @Test
    fun `earlier components win over later ones`() {
        assertTrue("1.9.9" isOlderThan "2.0.0")
        assertTrue("1.4.2" isOlderThan "1.5.0")
    }

    @Test
    fun `a missing component reads as zero`() {
        assertTrue("1.4" isSameAs "1.4.0")
        assertTrue("1.4" isOlderThan "1.4.1")
    }

    @Test
    fun `a prerelease sorts below the release it leads to`() {
        assertTrue("1.4.2-beta.1" isOlderThan "1.4.2")
        assertTrue("1.4.2-beta.1" isOlderThan "1.4.2-beta.2")
        assertTrue("1.4.2-alpha" isOlderThan "1.4.2-beta")
    }

    @Test
    fun `a numeric prerelease identifier ranks below an alphanumeric one`() {
        assertTrue("1.4.2-1" isOlderThan "1.4.2-alpha")
    }

    @Test
    fun `build metadata is not part of the ordering`() {
        assertTrue("1.4.2+deadbeef" isSameAs "1.4.2")
    }

    @Test
    fun `a leading v is tolerated`() {
        assertTrue("v1.4.2" isSameAs "1.4.2")
    }

    @Test
    fun `a version that is not a version has no ordering at all`() {
        assertNull(ModVersion.parse("unknown"))
        assertNull(ModVersion.parse("1.4.2-"))
        assertNull(ModVersion.parse("deadbeef"))
        assertNull(ModVersion.parse(""))
        assertNull(ModVersion.parse(null))
    }

    @Test
    fun `a plain release parses`() {
        assertNotNull(ModVersion.parse("1.0.0"))
    }
}
