package org.blackaddons.extras

import org.blackaddons.api.testing.LangCatalog
import org.junit.jupiter.api.Test

class LocalizationCatalogTest {
    private val catalog = LangCatalog("blackaddons-extras")

    @Test
    fun `English catalog covers every Extras feature`() = catalog.assertCoversFeaturesIn(LangCatalog.repoDir("addons/extras"))

    @Test
    fun `every shipped locale matches the English catalog`() = catalog.assertLocalesMatchEnglish()
}
