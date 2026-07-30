import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin

plugins {
    kotlin("multiplatform") version "2.0.21" apply false
    id("org.jetbrains.compose") version "1.7.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("androidx.room") version "2.7.0-alpha11" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.25" apply false
}

rootProject.plugins.withType<NodeJsRootPlugin> {
    rootProject.extensions.getByType<NodeJsRootExtension>().version = "20.10.0"
}
