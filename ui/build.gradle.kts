import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform")
    // kotlin("jvm")
    // kotlin("android")
    // id("com.android.application") version "8.1.0"
    // id("com.android.library") version "8.1.0"
    id("org.jetbrains.compose") version "1.5.2"
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
                implementation("io.github.g0dkar:qrcode-kotlin:3.3.0")
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
    dependsOn(":library:linkFidokDebugSharedLinux")

    from(project(":library").layout.buildDirectory.file("bin/linux/fidokDebugShared/libfidok.so"))
    into(layout.buildDirectory.dir("natives/common"))
}

compose.desktop {
    application {
        from(kotlin.targets["desktop"])

        mainClass = "us.q3q.fidok.ui.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.AppImage, TargetFormat.Deb, TargetFormat.Rpm)
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
