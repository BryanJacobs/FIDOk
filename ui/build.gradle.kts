import org.apache.tools.ant.taskdefs.condition.Os
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform")
    // kotlin("jvm")
    // kotlin("android")
    // id("com.android.application") version "8.1.0"
    // id("com.android.library") version "8.1.0"
    id("org.jetbrains.compose") version libs.versions.compose
}

kotlin {
    // androidTarget()
    jvm("desktop") {
        jvmToolchain(11)
        withJava()
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":library"))
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material)
                implementation(libs.qrcode.kotlin)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.common)
                implementation(compose.desktop.currentOs)
            }
        }
        val desktopTest by getting
        /*val androidMain by getting {
            dependencies {
                api("androidx.activity:activity-compose:1.7.2")
                api("androidx.appcompat:appcompat:1.6.1")
                api("androidx.core:core-ktx:1.10.1")
            }
        }*/
    }
}

tasks.register<Copy>("copyNativeLibrariesIntoResources") {
    doFirst {
        mkdir(layout.buildDirectory.dir("natives/common"))
    }
    if (Os.isFamily(Os.FAMILY_MAC)) {
        dependsOn(":library:linkFidokDebugSharedMacos")
        from(project(":library").layout.buildDirectory.file("bin/macos/fidokDebugShared/libfidok.dylib"))
        from(project(":library").layout.buildDirectory.file("botan-macos/libbotan-3.2.dylib"))
    } else if (Os.isFamily(Os.FAMILY_WINDOWS)) {
        dependsOn(":library:linkFidokDebugSharedWindows")
        from(project(":library").layout.buildDirectory.file("bin/windows/fidokDebugShared/libfidok.dll"))
        from(project(":library").layout.buildDirectory.file("botan-windows/libbotan-3.dll"))
    } else {
        dependsOn(":library:linkFidokDebugSharedLinux")
        from(project(":library").layout.buildDirectory.file("bin/linux/fidokDebugShared/libfidok.so"))
        from(project(":library").layout.buildDirectory.file("botan-linux/libbotan-3.so.2"))
    }

    into(layout.buildDirectory.dir("natives/common"))
}

compose.desktop {
    application {
        from(kotlin.targets["desktop"])

        mainClass = "us.q3q.fidok.ui.MainKt"

        nativeDistributions {
            if (Os.isFamily(Os.FAMILY_MAC)) {
                targetFormats(TargetFormat.Dmg)
            } else {
                targetFormats(TargetFormat.AppImage, TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.Msi)
            }
            packageName = "FidoK"
            description = "Manage FIDO Authenticators"
            copyright = "2023 Bryan Jacobs"
            licenseFile.set(file(rootProject.layout.projectDirectory.file("LICENSE")))

            appResourcesRootDir.set(project.layout.buildDirectory.dir("natives"))
        }

        // jvmArgs += ""
    }
}

tasks.whenTaskAdded {
    // This needs to be a task-graph subscription because:
    // 1. the task is dynamically created, and
    // 2. this task doesn't appear to have its own DSL block for a dependsOn
    if (name == "prepareAppResources") {
        dependsOn("copyNativeLibrariesIntoResources")
    }
}
