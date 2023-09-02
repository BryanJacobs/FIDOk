plugins {
    kotlin("multiplatform") version "1.9.0" apply false
    kotlin("android") version "1.9.0" apply false
    kotlin("jvm") version "1.9.0" apply false
    kotlin("plugin.serialization") version "1.9.0" apply false
    id("org.jetbrains.compose") version "1.5.0" apply false
    // Commented out until it stops throwing parse errors
    // id("org.jlleitschuh.gradle.ktlint") version "11.5.1"
}

group = "us.q3q"
version = "1.0-SNAPSHOT"

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
    // apply(plugin = "org.jlleitschuh.gradle.ktlint")
}
