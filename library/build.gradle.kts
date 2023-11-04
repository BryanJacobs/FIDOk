import org.apache.tools.ant.taskdefs.condition.Os
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

val submodulesDir = project.layout.projectDirectory.dir("submodules")
val botanDir = submodulesDir.dir("botan")
val hidDir = submodulesDir.dir("hidapi")

fun hidAPITasks(platform: String, cmakeExtraArgs: List<String> = listOf()) {
    val lcPlatform = platform.lowercase()
    val hidBuild = project.layout.buildDirectory.dir("hidapi-$lcPlatform").get()
    hidBuild.asFile.mkdirs()

    val output = when (platform) {
        "Linux" -> hidBuild.dir("src").dir(lcPlatform).file("libhidapi-hidraw.a")
        "Windows" -> hidBuild.dir("src").dir(lcPlatform).file("libhidapi.a")
        "Macos" -> hidBuild.dir("src").dir("mac").file("libhidapi.a")
        else -> throw NotImplementedError("Platform $platform not handled in HIDAPI build")
    }

    task<Exec>("configureHID$platform") {
        workingDir(hidBuild)
        commandLine(listOf("cmake", hidDir.asFile.absolutePath, "-DBUILD_SHARED_LIBS=FALSE") + cmakeExtraArgs)
        inputs.property("platform", platform)
        inputs.files(fileTree(hidDir))
        outputs.files(hidBuild.file("Makefile"))
    }
    task<Exec>("buildHID$platform") {
        workingDir(hidBuild)
        commandLine("cmake", "--build", ".")
        dependsOn("configureHID$platform")
        inputs.property("platform", platform)
        inputs.files(fileTree(hidBuild))
        outputs.files(output)
    }
}

hidAPITasks("Linux")
hidAPITasks(
    "Macos",
    listOf(
        "-DCMAKE_OSX_DEPLOYMENT_TARGET=11.0",
    ),
)
hidAPITasks(
    "Windows",
    listOf(
        "-DCMAKE_C_COMPILER=x86_64-w64-mingw32-gcc",
        "-DCMAKE_SYSTEM_NAME=Windows",
        "-DCMAKE_CROSSCOMPILING=TRUE",
    ),
)

task<Exec>("buildEmbeddedAuthenticatorJar") {
    val appletDir = project.layout.projectDirectory.dir("submodules").dir("FIDO2Applet")
    val appletBuildDir = appletDir.dir("build")
    workingDir(appletDir)
    commandLine(listOf("./gradlew", "jar", "testJar"))
    inputs.files(fileTree(appletDir) - fileTree(appletBuildDir))
    outputs.files(
        appletBuildDir.dir("libs").file("fido2applet-1.0-SNAPSHOT.jar"),
        appletBuildDir.dir("libs").file("fido2applet-tests-1.0-SNAPSHOT.jar"),
    )
}

fun botanTasks(platform: String, extraArgs: List<String> = listOf(), dlSuffix: String = "so") {
    val buildDir = project.layout.buildDirectory.dir("botan-${platform.lowercase()}").get()
    buildDir.asFile.mkdirs()
    task<Exec>("configureBotan$platform") {
        workingDir(botanDir)
        val args = commonBotan(buildDir.asFile.absolutePath)
        commandLine(args + extraArgs)
        inputs.property("platform", platform)
        inputs.files(botanDir.file("configure.py"))
        outputs.files(buildDir.file("Makefile"))
    }
    task<Exec>("buildBotan$platform") {
        dependsOn("configureBotan$platform")
        workingDir(botanDir)
        commandLine("make", "-j4", "-f", "${buildDir.asFile.absolutePath}/Makefile")
        inputs.property("platform", platform)
        inputs.files(buildDir.file("Makefile"))
        if (platform == "Windows") {
            // Don't ask why Botan puts the DLL def file up here instead of in the build directory
            outputs.files(botanDir.file("libbotan-3.$dlSuffix"))
        } else {
            outputs.files(buildDir.file("libbotan-3.$dlSuffix"))
        }
    }
}

botanTasks(
    "Macos",
    listOf(
        if (System.getProperty("os.arch") == "amd64") "--cpu=x86_64" else "--cpu=arm64",
    ),
    "dylib",
)
botanTasks(
    "Windows",
    listOf(
        "--os=mingw",
        "--cc-bin=x86_64-w64-mingw32-g++",
        "--ar-command=x86_64-w64-mingw32-ar",
        // "--ldflags=-Wl,--output-def,windows/libbotan-3.def",
    ),
    "dll.a",
)

botanTasks("Linux")

fun nativeBuild(target: KotlinNativeTarget, platform: String, arch: String = "x86_64") {
    val hidBuild = tasks.getByName("buildHID$platform")
    val botanBuild = tasks.getByName("buildBotan$platform")
    val lcPlatform = platform.lowercase()
    val hidFile = hidBuild.outputs.files.singleFile
    val hidLibName = hidFile.name.replace(Regex("^lib"), "").replace(Regex("\\..*$"), "")
    val linkerOpts = arrayListOf(
        "-L${hidFile.parent}",
        "-l$hidLibName",
        "-L${botanBuild.outputs.files.singleFile.parent}",
        "-lbotan-3",
    )
    if (platform == "Linux") {
        linkerOpts.add("-l${submodulesDir.dir("pcsc").file("libpcsclite.so.1.0.0").asFile.absolutePath}")
        linkerOpts.add("-l/usr/lib/libudev.so")
    } else if (platform == "Windows") {
        linkerOpts.add("-lwinscard")
    } else if (platform == "Macos") {
        linkerOpts.add("-framework")
        linkerOpts.add("IOKit")
        linkerOpts.add("-framework")
        linkerOpts.add("AppKit")
    }

    target.apply {
        compilations.getByName("main") {
            cinterops {
                val hidapi by creating {
                    includeDirs(hidDir)
                }
                if (platform == "Linux") {
                    val pcsc by creating {
                        includeDirs("/usr/include/PCSC")
                    }
                }
                if (platform == "Linux") {
                    val uhid by creating {
                        includeDirs("/usr/include")
                    }
                }
                val botan by creating {
                    includeDirs(
                        project.layout.buildDirectory.dir("botan-$lcPlatform").get()
                            .dir("build").dir("include"),
                    )
                }
            }
        }
        binaries {
            all {
                linkerOpts(linkerOpts)
                if (platform != "Windows") {
                    binaryOption("sourceInfoType", "libbacktrace")
                }
            }
            executable()
            sharedLib("fidok")
        }
    }
}

kotlin {
    applyDefaultHierarchyTemplate()
    jvm {
        jvmToolchain(11)
        withJava()
        testRuns.named("test") {
            executionTask.configure {
                useJUnitPlatform()
            }
        }
    }
    /*js {
        browser {
            commonWebpackConfig(
                Action {
                    cssSupport {
                        enabled.set(true)
                    }
                },
            )
        }
    }*/

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.serialization.cbor)
                implementation(libs.serialization.json)
                implementation(libs.kermit.get().let { "${it.module}:${it.versionConstraint.requiredVersion}" }) {
                    // conflicts with junit5
                    exclude(group = "org.jetbrains.kotlin", module = "kotlin-test-junit")
                }
                implementation(libs.coroutines)
                implementation(libs.clikt)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.jnr)
                implementation(libs.blessed)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(tasks.getByName("buildEmbeddedAuthenticatorJar").outputs.files)
            }
        }
        /*val jsMain by getting
        val jsTest by getting*/
    }

    if (Os.isFamily(Os.FAMILY_MAC)) {
        if (System.getProperty("os.arch") == "amd64") {
            nativeBuild(macosX64("macos"), "Macos", "x86_64")
        } else {
            nativeBuild(macosArm64("macos"), "Macos", "arm64")
        }
    }
    nativeBuild(linuxX64("linux"), "Linux")
    nativeBuild(mingwX64("windows"), "Windows")
}

fun commonBotan(buildDirPath: String): List<String> {
    return listOf(
        "./configure.py",
        "--without-documentation",
        "--optimize-for-size",
        "--without-compilation-database",
        "--build-targets=shared",
        // "--build-targets=shared,static",
        "--with-build-dir=$buildDirPath",
        /*"--extra-cxxflags=-fPIC",
        "--extra-cxxflags=-static-libstdc++",
        "--extra-cxxflags=-D_GLIBCXX_USE_CXX11_ABI=0",*/
        "--minimized-build",
        "--enable-modules=ffi,aes,aes_ni,auto_rng,cbc,dh,ecc_key,ecdh," +
            "ecdsa,hash,hkdf,hmac,kdf,kdf1,kdf2,keypair,pubkey,raw_hash," +
            "rng,sha2_64,simd,simd_avx2,simd_avx512,system_rng",
    )
}

tasks.getByName("compileKotlinLinux").dependsOn("buildBotanLinux", "buildHIDLinux")
tasks.getByName("cinteropBotanLinux").dependsOn("buildBotanLinux")
tasks.getByName("compileKotlinWindows").dependsOn("buildBotanWindows", "buildHIDWindows")
tasks.getByName("cinteropBotanWindows").dependsOn("buildBotanWindows")
if (Os.isFamily(Os.FAMILY_MAC)) {
    tasks.getByName("compileKotlinMacos").dependsOn("buildBotanMacos", "buildHIDMacos")
    tasks.getByName("cinteropBotanMacos").dependsOn("buildBotanMacos")
}

tasks.getByName("jvmTestClasses").dependsOn("buildEmbeddedAuthenticatorJar")
