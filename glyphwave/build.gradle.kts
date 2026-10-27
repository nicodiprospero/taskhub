// Plugins are declared here (apply false) so every module shares one Kotlin plugin classloader.
// The Android plugin is declared only in :app, so `-Pglyphwave.coreOnly=true` builds never resolve it.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
