plugins {
    kotlin("multiplatform") version "1.9.0"
    kotlin("plugin.serialization") version "1.9.0"
    // Commented out until it stops throwing parse errors
    // id("org.jlleitschuh.gradle.ktlint") version "11.5.1"
}

group = "us.q3q"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvm {
        jvmToolchain(11)
        withJava()
        testRuns.named("test") {
            executionTask.configure {
                useJUnitPlatform()
            }
        }
    }
    js {
        browser {
            commonWebpackConfig(
                Action {
                    cssSupport {
                        enabled.set(true)
                    }
                },
            )
        }
    }
    val hostOs = System.getProperty("os.name")
    val isArm64 = System.getProperty("os.arch") == "aarch64"
    val isMingwX64 = hostOs.startsWith("Windows")
    val nativeTarget = when {
        hostOs == "Mac OS X" && isArm64 -> macosArm64("native")
        hostOs == "Mac OS X" && !isArm64 -> macosX64("native")
        hostOs == "Linux" && isArm64 -> linuxArm64("native")
        hostOs == "Linux" && !isArm64 -> linuxX64("native")
        isMingwX64 -> mingwX64("native")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }
    nativeTarget.apply {
        compilations.getByName("main") {
            cinterops {
                val hidapi by creating
                val pcsc by creating
                val botan by creating
            }
        }
        binaries {
            all {
                linkerOpts(
                    "-l/home/braujac/sc/fidok/submodules/pcsc/libpcsclite.so.1.0.0",
                )
            }
            executable()
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.6.0-RC")
                implementation("co.touchlab:kermit:2.0.0-RC5") {
                    // conflicts with junit5
                    exclude(group = "org.jetbrains.kotlin", module = "kotlin-test-junit")
                }
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting
        val jvmTest by getting
        val jsMain by getting
        val jsTest by getting
        val nativeMain by getting
        val nativeTest by getting
    }
}

subprojects {
    // apply(plugin = "org.jlleitschuh.gradle.ktlint")
}
