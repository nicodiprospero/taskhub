import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

val previewDir = layout.buildDirectory.dir("matrix-previews")

tasks.test {
    systemProperty("glyphwave.previewDir", previewDir.get().asFile.absolutePath)
    // Preview rendering is a separate task; keep `test` fast and side-effect free.
    filter { excludeTestsMatching("*PreviewRenderTest") }
}

// Renders every test pattern / mode to PNGs (round dots on black, masked) in build/matrix-previews/.
tasks.register<Test>("renderPreviews") {
    group = "verification"
    description = "Renders matrix previews to build/matrix-previews/"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    systemProperty("glyphwave.previewDir", previewDir.get().asFile.absolutePath)
    systemProperty("java.awt.headless", "true")
    filter { includeTestsMatching("*PreviewRenderTest") }
    outputs.dir(previewDir)
    outputs.upToDateWhen { false }
}
