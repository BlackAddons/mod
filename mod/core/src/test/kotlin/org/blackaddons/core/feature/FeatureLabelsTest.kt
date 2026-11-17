package org.blackaddons.core.feature

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.blackaddons.api.feature.FeatureLabel
import org.blackaddons.api.testing.LangCatalog
import org.blackaddons.core.update.ModVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.readText

class FeatureLabelsTest {

    private val document = json(
        """
        { "features": [
          { "id": "AutoRelic", "from": "0.1.4-beta", "until": "0.1.5-beta",
            "labels": [ { "kind": "untested", "reason": "Not run yet." } ] },
          { "id": "Freecam", "labels": [ { "kind": "UNSAFE" }, { "kind": "nonsense" } ] },
          { "id": "SimonSays", "labels": [ { "kind": "untested", "reason": "old" } ] },
          { "id": "SimonSays", "labels": [ { "kind": "untested", "reason": "new" }, { "kind": "unsafe" } ] }
        ] }
        """,
    )

    @Test
    fun `an entry applies inside its version range only`() {
        assertEquals(
            listOf(FeatureLabel.untested("Not run yet.")),
            FeatureLabels.parse(document, version("0.1.4-beta"))["AutoRelic"],
        )
        assertEquals(null, FeatureLabels.parse(document, version("0.1.5-beta"))["AutoRelic"])
        assertEquals(null, FeatureLabels.parse(document, version("0.1.3-beta"))["AutoRelic"])
    }

    @Test
    fun `kinds parse in any case and unknown kinds are skipped`() {
        assertEquals(listOf(FeatureLabel.unsafe()), FeatureLabels.parse(document, version("0.1.4-beta"))["Freecam"])
    }

    @Test
    fun `entries for one id add up, a later label of the same kind winning`() {
        assertEquals(
            listOf(FeatureLabel.untested("new"), FeatureLabel.unsafe()),
            FeatureLabels.parse(document, version("0.1.4-beta"))["SimonSays"],
        )
    }

    @Test
    fun `a build with no parseable version gets only the unranged entries`() {
        val parsed = FeatureLabels.parse(document, null)
        assertEquals(null, parsed["AutoRelic"])
        assertEquals(listOf(FeatureLabel.unsafe()), parsed["Freecam"])
    }

    @Test
    fun `the shipped document parses whole and names only real features`() {
        val shipped = json(requireNotNull(javaClass.getResource("/gamedata/feature_labels.json")).readText())
        val entries = shipped.getAsJsonArray("features").map { it.asJsonObject }
        val declared = entries.sumOf { it.getAsJsonArray("labels").size() }
        val parsed = FeatureLabels.parse(shipped, version("0.1.4-beta"))
        assertEquals(declared, parsed.values.sumOf { it.size }, "every label has a known kind and every entry applies")

        val sources = listOf("mod", "addons").flatMap { dir ->
            Files.walk(LangCatalog.repoDir(dir)).use { paths ->
                paths.filter { it.extension == "kt" && "/src/main/" in it.invariantSeparatorsPathString }
                    .map { it.readText() }.toList()
            }
        }
        for (id in entries.map { it.get("id").asString }) {
            val pattern = Regex("""object\s+$id\s*:\s*Feature(Extension)?\(""")
            assertTrue(sources.any(pattern::containsMatchIn), "'$id' is not a feature or extension")
        }
    }

    private fun version(text: String) = requireNotNull(ModVersion.parse(text))

    private fun json(text: String): JsonObject = JsonParser.parseString(text).asJsonObject
}
