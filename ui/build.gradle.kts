import org.apache.tools.ant.taskdefs.condition.Os
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    // kotlin("jvm")
    // kotlin("android")
    // id("com.android.application") version "8.1.0"
    // id("com.android.library") version "8.1.0"
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
}

repositories {
    maven("https://jogamp.org/deployment/maven")
}

kotlin {
    // androidTarget()
    jvmToolchain(11)
    jvm("desktop") {
        withJava()
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":fidok"))
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material)
                implementation(libs.qrcode.kotlin)
                implementation(libs.serialization.json)
                implementation(libs.kermit)

                api(libs.compose.webview.multiplatform)
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
        dependsOn(":fidok:linkFidokDebugSharedMacos")
        from(project(":fidok").layout.buildDirectory.file("bin/macos/fidokDebugShared/fidok.dylib"))
    } else if (Os.isFamily(Os.FAMILY_WINDOWS)) {
        dependsOn(":fidok:linkFidokDebugSharedWindows")
        from(project(":fidok").layout.buildDirectory.file("bin/windows/fidokDebugShared/fidok.dll"))
    } else {
        dependsOn(":fidok:linkFidokDebugSharedLinux")
        from(project(":fidok").layout.buildDirectory.file("bin/linux/fidokDebugShared/libfidok.so"))
    }

    into(layout.buildDirectory.dir("natives/common"))
}

compose.desktop {
    application {
        from(kotlin.targets["desktop"])

        mainClass = "us.q3q.fidok.ui.MainKt"

        jvmArgs("--add-opens", "java.desktop/sun.awt=ALL-UNNAMED")
        jvmArgs("--add-opens", "java.desktop/java.awt.peer=ALL-UNNAMED")
        if (Os.isFamily(Os.FAMILY_MAC)) {
            jvmArgs("--add-opens", "java.desktop/sun.lwawt=ALL-UNNAMED")
            jvmArgs("--add-opens", "java.desktop/sun.lwawt.macosx=ALL-UNNAMED")
        }

        nativeDistributions {
            if (Os.isFamily(Os.FAMILY_MAC)) {
                targetFormats(TargetFormat.Pkg)
                macOS {
                    bundleID = "us.q3q.fidok"
                    entitlementsFile.set(project.file("entitlements.plist"))
                    runtimeEntitlementsFile.set(project.file("runtime-entitlements.plist"))
                    appStore = false
                }
            } else {
                targetFormats(TargetFormat.AppImage, TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.Msi)
            }
            packageName = "FIDOk"
            description = "Manage FIDO Authenticators"
            copyright = "2023 Bryan Jacobs"
            version = "1.0.0"
            licenseFile.set(file(rootProject.layout.projectDirectory.file("LICENSE")))

            appResourcesRootDir.set(project.layout.buildDirectory.dir("natives"))
        }
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
