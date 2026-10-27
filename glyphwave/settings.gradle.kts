pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "GlyphWave"

include(":core")

// `-Pglyphwave.coreOnly=true` skips the Android module so the pure-Kotlin core can be built and
// tested on machines without the Android SDK (CI, cloud containers). See DECISIONS.md D-003.
if (providers.gradleProperty("glyphwave.coreOnly").orNull != "true") {
    include(":app")
}
