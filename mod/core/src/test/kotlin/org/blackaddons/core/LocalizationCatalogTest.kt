package org.blackaddons.core

import org.blackaddons.api.testing.LangCatalog
import org.junit.jupiter.api.Test

class LocalizationCatalogTest {
    private val catalog = LangCatalog("blackaddons")

    @Test
    fun `English catalog covers every host feature`() = catalog.assertCoversFeaturesIn(LangCatalog.repoDir("mod"))

    @Test
    fun `every shipped locale matches the English catalog`() = catalog.assertLocalesMatchEnglish()
}
