// All plugins are declared here (apply false) so they share ONE classloader. AGP 9's built-in Kotlin
// drives the Kotlin plugin directly; if AGP lived only in :app's classloader, the Kotlin plugin
// (loaded here for :core) could not see AGP classes and plugin application fails.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
