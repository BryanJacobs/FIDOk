rootProject.name = "FIDOk"

include(":library")
project(":library").name = "fidok"

include(":ui")
include(":android-app")
project(":android-app").name = "fidok-app"
include(":android-library")
project(":android-library").name = "fidok-android"


pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}