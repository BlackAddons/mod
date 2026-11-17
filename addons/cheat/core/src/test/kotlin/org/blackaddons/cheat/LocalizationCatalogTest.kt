package org.blackaddons.cheat

import org.blackaddons.api.testing.LangCatalog
import org.junit.jupiter.api.Test

class LocalizationCatalogTest {
    private val catalog = LangCatalog("blackaddons-cheat")

    @Test
    fun `English catalog covers every cheat feature and extension`() =
        catalog.assertCoversFeaturesIn(LangCatalog.repoDir("addons/cheat"))

    @Test
    fun `every shipped locale matches the English catalog`() = catalog.assertLocalesMatchEnglish()
}
