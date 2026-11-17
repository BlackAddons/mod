package org.blackaddons.core.feature

import io.github.classgraph.ClassGraph
import org.blackaddons.api.feature.Feature
import org.blackaddons.api.feature.annotations.DevOnly
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@DevOnly
private object DevOnlyProbe : Feature()

private object OrdinaryProbe : Feature()

class DevOnlyFeatureTest {
    @Test
    fun `dev-only feature is absent from production discovery`() {
        ClassGraph().enableClasspath().enableClassInfo().enableAnnotationInfo().ignoreClassVisibility()
            .acceptPackages("org.blackaddons.core.feature").scan().use { scan ->
            val devOnly = checkNotNull(scan.getClassInfo(DevOnlyProbe::class.java.name))
            val ordinary = checkNotNull(scan.getClassInfo(OrdinaryProbe::class.java.name))

            assertFalse(shouldRegisterFeature(devOnly, development = false))
            assertTrue(shouldRegisterFeature(devOnly, development = true))
            assertTrue(shouldRegisterFeature(ordinary, development = false))
        }
    }
}
