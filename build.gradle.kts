plugins {
    kotlin("multiplatform") version libs.versions.kotlin apply false
    kotlin("android") version libs.versions.kotlin apply false
    kotlin("jvm") version libs.versions.kotlin apply false
    kotlin("plugin.serialization") version libs.versions.kotlin apply false
    id("org.jetbrains.compose") version libs.versions.compose apply false
    id("com.android.application") version "8.2.0" apply false
    // Commented out until it stops throwing parse errors
    // id("org.jlleitschuh.gradle.ktlint") version "11.6.1"
}

allprojects {
    repositories {
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
        }
    }
}

subprojects {
    // apply(plugin = "org.jlleitschuh.gradle.ktlint")
}
