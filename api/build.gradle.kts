import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

// :api - the stable contract addons compile against, and the only published module (`org.blackaddons:blackaddons-api`).
plugins {
    id("blackaddons.jvm-module")
    id("blackaddons.host-module")
    id("maven-publish")
    // Shared test helpers (LangCatalog); never published.
    id("java-test-fixtures")
}

fun prop(name: String): String = providers.gradleProperty(name).get()

base {
    archivesName = "blackaddons-api"
}

// `-Psnapshot` adds the suffix main publishes, so addon authors can track the contract between releases.
if (providers.gradleProperty("snapshot").isPresent) {
    version = "$version-SNAPSHOT"
}

dependencies {
    // JOML types in facade signatures; Minecraft bundles it.
    compileOnly("org.joml:joml:${prop("joml_version")}")

    // Used by EventBus, FeatureManager, DevFileLogger; Minecraft provides the binding.
    compileOnly("org.slf4j:slf4j-api:${prop("slf4j_version")}")

    // Settings serialize to JsonElement; Minecraft provides gson.
    compileOnly("com.google.code.gson:gson:${prop("gson_version")}")

    testFixturesImplementation(platform("org.junit:junit-bom:${prop("junit_version")}"))
    testFixturesImplementation("org.junit.jupiter:junit-jupiter-api")
    testFixturesImplementation("com.google.code.gson:gson:${prop("gson_version")}")
}

/*
 * The addon contract as a text dump in api/api/. `check` fails when the public API changes and the dump was not
 * regenerated with `gradle :api:updateKotlinAbi`, so every change to what addons compile against shows in review.
 */
@OptIn(ExperimentalAbiValidation::class)
kotlin {
    abiValidation()
}

tasks.check {
    @OptIn(ExperimentalAbiValidation::class)
    dependsOn(kotlin.abiValidation.checkTaskProvider)
}

tasks.register<JavaExec>("reuseReport") {
    group = "verification"
    description = "Leaderboard of copied function bodies and repeated lines across every module, to pick what to reuse."
    classpath = sourceSets["testFixtures"].runtimeClasspath
    mainClass = "org.blackaddons.api.testing.ReuseReportKt"
}

// Test tooling, not part of the contract.
(components["java"] as AdhocComponentWithVariants).apply {
    withVariantsFromConfiguration(configurations["testFixturesApiElements"]) { skip() }
    withVariantsFromConfiguration(configurations["testFixturesRuntimeElements"]) { skip() }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            // Set here rather than by naming the project; that was only for the old composite build.
            artifactId = "blackaddons-api"
            from(components["java"])

            pom {
                name = "BlackAddons API"
                description = "The stable contract a BlackAddons addon compiles against."
                url = "${prop("gitlab_host")}/${prop("gitlab_project")}"
                licenses {
                    license {
                        name = "PolyForm Strict License 1.0.0"
                        url = "https://polyformproject.org/licenses/strict/1.0.0/"
                    }
                }
            }
        }
    }

    repositories {
        /*
         * GitLab Maven registry. In CI: the pipeline's project and job token; locally: the project id from
         * gradle.properties and a personal token (`-PgitlabToken=...` or GITLAB_TOKEN). The header name is
         * part of the credential: GitLab rejects a job token as Private-Token and vice versa.
         */
        maven {
            name = "GitLab"
            val projectRef = System.getenv("CI_PROJECT_ID") ?: prop("gitlab_project_id")
            url = uri("${prop("gitlab_host")}/api/v4/projects/$projectRef/packages/maven")

            val jobToken: String? = System.getenv("CI_JOB_TOKEN")
            credentials(HttpHeaderCredentials::class) {
                name = if (jobToken != null) "Job-Token" else "Private-Token"
                // Empty rather than absent, so builds that never publish don't fail.
                value = jobToken
                    ?: providers.gradleProperty("gitlabToken").orNull
                    ?: System.getenv("GITLAB_TOKEN")
                    ?: ""
            }
            authentication {
                create<HttpHeaderAuthentication>("header")
            }
        }
    }
}
