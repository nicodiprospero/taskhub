// Root build file used with `-Pglyphwave.coreOnly=true` (see settings.gradle.kts): no Android
// plugin, so the pure-Kotlin core builds on machines without access to Google Maven / Android SDK.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}
