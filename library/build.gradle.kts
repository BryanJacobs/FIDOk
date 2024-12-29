import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import org.apache.tools.ant.taskdefs.condition.Os
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    java
    alias(libs.plugins.kotlinMultiplatform)
    kotlin("plugin.serialization")
    id("com.vanniktech.maven.publish") version "0.27.0"
}

mavenPublishing {
    configure(
        KotlinMultiplatform(
            javadocJar = JavadocJar.None(),
            sourcesJar = true,
        ),
    )
}

val submodulesDir = project.layout.projectDirectory.dir("submodules")
val botanDir = submodulesDir.dir("botan")
val hidDir = submodulesDir.dir("hidapi")
val bincDir = submodulesDir.dir("bluez_inc")

fun bincTasks(platform: String) {
    if (platform != "Linux") {
        return
    }

    val buildDir = project.layout.buildDirectory.dir("binc-${platform.lowercase()}").get()
    buildDir.asFile.mkdirs()
    val output = buildDir.dir("binc").file("libBinc.a")

    task<Exec>("configureBinc$platform") {
        workingDir(buildDir)
        commandLine("cmake", bincDir.asFile.absolutePath)
        environment("CFLAGS", "-fPIC")
        environment("CXXFLAGS", "-fPIC")
        inputs.property("platform", platform)
        inputs.files(fileTree(bincDir))
        outputs.files(buildDir.file("Makefile"))
    }
    task<Exec>("buildBinc$platform") {
        workingDir(buildDir)
        commandLine("cmake", "--build", ".")
        dependsOn("configureBinc$platform")
        inputs.property("platform", platform)
        inputs.files(fileTree(buildDir))
        outputs.files(output)
    }
}

fun hidAPITasks(
    platform: String,
    cmakeExtraArgs: List<String> = listOf(),
) {
    val lcPlatform = platform.lowercase()
    val hidBuild = project.layout.buildDirectory.dir("hidapi-$lcPlatform").get()
    hidBuild.asFile.mkdirs()

    val output =
        when (platform) {
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

bincTasks("Linux")

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

fun botanTasks(
    platform: String,
    extraArgs: List<String> = listOf(),
    dlSuffix: String = "so",
    staticLibSuffix: String = "a",
) {
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
        outputs.files(
            buildDir.file("libbotan-3.$staticLibSuffix"),
            if (platform == "Windows") {
                // Don't ask why Botan puts the DLL def file up here instead of in the build directory
                botanDir.file("libbotan-3.$dlSuffix")
            } else {
                buildDir.file("libbotan-3.$dlSuffix")
            },
        )
    }
}

botanTasks(
    "Macos",
    listOf(
        "--cpu=${determineMacOsArch()}",
        "--cc-abi-flags=\"-mmacosx-version-min=11.0\"",
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

fun nativeBuild(
    target: KotlinNativeTarget,
    platform: String,
) {
    val hidBuild = tasks.getByName("buildHID$platform")
    val botanBuild = tasks.getByName("buildBotan$platform")
    val lcPlatform = platform.lowercase()
    val hidFile = hidBuild.outputs.files.singleFile
    val hidLibName = hidFile.name.replace(Regex("^lib"), "").replace(Regex("\\..*$"), "")

    val linkerOpts =
        arrayListOf(
            "-L${hidFile.parent}",
            "-l$hidLibName",
        )
    when (platform) {
        "Linux" -> {
            val bincBuild = tasks.getByName("buildBinc$platform")
            val bincFile = bincBuild.outputs.files.singleFile
            linkerOpts.add("-l${submodulesDir.dir("pcsc").file("libpcsclite.so.1.0.0").asFile.absolutePath}")
            linkerOpts.add("-l/usr/lib/libudev.so")
            linkerOpts.add("-L${bincFile.parent}")
            linkerOpts.add("-lBinc")
            linkerOpts.add("-l/usr/lib/libglib-2.0.so")
            linkerOpts.add("-l/usr/lib/libgio-2.0.so")
            linkerOpts.add("-l/usr/lib/libgobject-2.0.so")
        }
        "Windows" -> {
            linkerOpts.add("-lwinscard")
        }
        "Macos" -> {
            linkerOpts.addAll(
                arrayOf(
                    "-framework",
                    "IOKit",
                    "-framework",
                    "AppKit",
                    "-framework",
                    "PCSC",
                ),
            )
        }
    }

    // Botan links against libstdc++ and uses newer glibc constructs. Let it do that, and sort it out at load time.
    val botanLinkerOptsForShared =
        arrayListOf(
            "--allow-shlib-undefined",
            botanBuild.outputs.files.first().path,
            "-lstdc++",
            "--no-allow-shlib-undefined",
        )
    // ... but for an executable, we need to resolve those symbols NOW, so use the dynamic botan instead of static
    val botanLinkerOptsForExecutable =
        if (platform == "Windows") {
            arrayListOf(
                botanBuild.outputs.files.first().path,
                "/usr/x86_64-w64-mingw32/lib/libstdc++.a",
            )
        } else {
            arrayListOf(
                "-L${botanBuild.outputs.files.last().parent}",
                "-lbotan-3",
            )
        }

    val linkerOptsShared = linkerOpts + if (platform == "Windows") botanLinkerOptsForExecutable else botanLinkerOptsForShared
    val linkerOptsExecutable = linkerOpts + botanLinkerOptsForExecutable

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
                    val binc by creating {
                        includeDirs(bincDir.dir("binc"), "/usr/include/glib-2.0", "/usr/lib/glib-2.0/include")
                    }
                    val uhid by creating {
                        includeDirs("/usr/include")
                    }
                } else if (platform == "Macos") {
                    val macLibraries = "/Library/Developer/CommandLineTools/SDKs/MacOSX.sdk/System/Library/Frameworks"
                    val pcsc by creating {
                        includeDirs("$macLibraries/PCSC.framework/Versions/A/Headers/")
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
                if (platform != "Windows") {
                    binaryOption("sourceInfoType", "libbacktrace")
                }
            }
            executable {
                linkerOpts(linkerOptsExecutable)
            }
            sharedLib("fidok") {
                linkerOpts(linkerOptsShared)
            }
        }
        // kotlin/native doesn't allow defining both executable{} and test{} without collisions
        // so, set up the appropriate test linking options after the fact
        this.binaries["debugTest"].linkerOpts(linkerOptsExecutable)
    }
}

kotlin {
    applyDefaultHierarchyTemplate()
    jvmToolchain(11)
    jvm {
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

    if (!project.hasProperty("jvmOnly")) {
        if (Os.isFamily(Os.FAMILY_MAC)) {
            val arch = determineMacOsArch()
            if (arch == "arm64") {
                nativeBuild(macosArm64("macos"), "Macos")
            } else {
                nativeBuild(macosX64("macos"), "Macos")
            }
        } else {
            nativeBuild(linuxX64("linux"), "Linux")
            nativeBuild(mingwX64("windows"), "Windows")
        }
    }
}

fun determineMacOsArch(): String {
    if (!Os.isFamily(Os.FAMILY_MAC)) return "NOT_MAC"

    val arch = System.getProperty("os.arch").lowercase()
    if (arch.contains("aarch64")) {
        val process =
            ProcessBuilder("sysctl", "-in", "sysctl.proc_translated")
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start()

        process.waitFor(5, TimeUnit.SECONDS)
        val xlated = process.inputStream.bufferedReader().readText().toIntOrNull()
        if (xlated == 1) return "x86_64"

        return "arm64"
    } else {
        return "x86_64"
    }
}

fun commonBotan(buildDirPath: String): List<String> {
    return listOf(
        "./configure.py",
        "--without-documentation",
        "--optimize-for-size",
        "--without-compilation-database",
        "--build-targets=shared,static",
        "--with-build-dir=$buildDirPath",
        "--extra-cxxflags=-fPIC",
        "--minimized-build",
        "--enable-modules=ffi,aes,aes_ni,auto_rng,cbc,dh,ecc_key,ecdh," +
            "ecdsa,hash,hkdf,hmac,kdf,kdf1,kdf2,keypair,pubkey,raw_hash," +
            "rng,sha2_64,simd,simd_avx2,simd_avx512,system_rng",
    )
}

if (!project.hasProperty("jvmOnly")) {
    if (Os.isFamily(Os.FAMILY_MAC)) {
        tasks.getByName("compileKotlinMacos").dependsOn("buildBotanMacos", "buildHIDMacos")
        tasks.getByName("cinteropBotanMacos").dependsOn("buildBotanMacos")
    } else {
        tasks.getByName("compileKotlinLinux").dependsOn("buildBotanLinux", "buildHIDLinux", "buildBincLinux")
        tasks.getByName("cinteropBotanLinux").dependsOn("buildBotanLinux")
        tasks.getByName("compileKotlinWindows").dependsOn("buildBotanWindows", "buildHIDWindows")
        tasks.getByName("cinteropBotanWindows").dependsOn("buildBotanWindows")
    }
}

tasks.getByName("jvmTestClasses").dependsOn("buildEmbeddedAuthenticatorJar")
