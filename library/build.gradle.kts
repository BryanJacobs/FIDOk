import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

val submodulesDir = project.layout.projectDirectory.dir("submodules")
val botanDir = submodulesDir.dir("botan")
val hidDir = submodulesDir.dir("hidapi")

fun hidAPITasks(platform: String) {
    val lcPlatform = platform.lowercase()
    val hidBuild = project.layout.buildDirectory.dir("hidapi-$lcPlatform").get()
    hidBuild.asFile.mkdirs()

    val cmakeExtraArgs = if (platform == "Windows") {
        listOf(
            "-DCMAKE_C_COMPILER=x86_64-w64-mingw32-gcc",
            "-DCMAKE_SYSTEM_NAME=Windows",
            "-DCMAKE_CROSSCOMPILING=TRUE",
        )
    } else {
        listOf()
    }

    val output = when (platform) {
        "Linux" -> hidBuild.dir("src").dir(lcPlatform).file("libhidapi-hidraw.a")
        "Windows" -> hidBuild.dir("src").dir(lcPlatform).file("libhidapi.a")
        else -> throw NotImplementedError("Platform $platform not handling in HIDAPI build")
    }

    task<Exec>("configureHID$platform") {
        workingDir(hidBuild)
        commandLine(listOf("cmake", hidDir.asFile.absolutePath, "-DBUILD_SHARED_LIBS=FALSE") + cmakeExtraArgs)
        inputs.files(hidDir, hidBuild)
        outputs.files(hidBuild.file("Makefile"))
    }
    task<Exec>("buildHID$platform") {
        workingDir(hidBuild)
        commandLine("cmake", "--build", ".")
        dependsOn("configureHID$platform")
        inputs.files(hidBuild.file("Makefile"), hidBuild.file("CMakeCache.txt"))
        outputs.files(output)
    }
}

hidAPITasks("Linux")
hidAPITasks("Windows")

fun botanTasks(platform: String, extraArgs: List<String> = listOf()) {
    val lcPlatform = platform.lowercase()
    val dlSuffix = when (lcPlatform) {
        "windows" -> "dll"
        "linux" -> "so"
        else -> "dylib"
    }
    val dirName = lcPlatform
    task<Exec>("configureBotan$platform") {
        workingDir(botanDir)
        val args = commonBotan(lcPlatform)
        commandLine(args + extraArgs)
        outputs.files(botanDir.dir(dirName).file("Makefile"))
    }
    task<Exec>("buildBotan$platform") {
        dependsOn("configureBotan$platform")
        workingDir(botanDir)
        commandLine("make", "-j4", "-f", "$dirName/Makefile")
        inputs.files(botanDir.dir(dirName).file("Makefile"))
        if (platform == "Windows") {
            // Don't ask why Botan puts the DLL def file up here instead of in the build directory
            outputs.files(botanDir.file("libbotan-3.dll.a"))
        } else {
            outputs.files(botanDir.dir(dirName).file("libbotan-3.$dlSuffix"))
        }
    }
}

botanTasks(
    "Windows",
    listOf(
        "--os=mingw",
        "--cc-bin=x86_64-w64-mingw32-g++",
        "--ar-command=x86_64-w64-mingw32-ar",
        "--ldflags=-Wl,--output-def,windows/libbotan-3.def",
    ),
)

botanTasks("Linux")

fun nativeBuild(target: KotlinNativeTarget, platform: String) {
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
    }

    target.apply {
        compilations.getByName("main") {
            cinterops {
                val hidapi by creating {
                    includeDirs(hidDir)
                }
                if (platform != "Windows") {
                    val pcsc by creating {
                        includeDirs("/usr/include/PCSC")
                    }
                }
                val botan by creating {
                    includeDirs(botanDir.dir(lcPlatform).dir("build").dir("include"))
                }
            }
        }
        binaries {
            all {
                linkerOpts(linkerOpts)
            }
            executable()
            sharedLib("fidok")
        }
    }
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

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.6.0")
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
        val jvmMain by getting {
            dependencies {
                implementation("com.github.jnr:jnr-ffi:2.2.14")
            }
        }
        val jvmTest by getting
        val jsMain by getting
        val jsTest by getting
        val nativeMain by creating {
            dependsOn(commonMain)
        }
        val nativeTest by creating {
            dependsOn(commonTest)
        }
        val windowsMain by creating {
            dependsOn(nativeMain)
        }
        val windowsTest by creating {
            dependsOn(nativeTest)
        }
        val linuxMain by creating {
            dependsOn(nativeMain)
        }
        val linuxTest by creating {
            dependsOn(nativeTest)
        }
    }

    nativeBuild(linuxX64("linux"), "Linux")
    nativeBuild(mingwX64("windows"), "Windows")
}

fun commonBotan(suffix: String): List<String> {
    return listOf(
        "./configure.py",
        "--without-documentation",
        "--optimize-for-size",
        "--without-compilation-database",
        "--build-targets=shared,static",
        "--with-build-dir=$suffix",
        /*"--extra-cxxflags=-fPIC",
        "--extra-cxxflags=-static-libstdc++",
        "--extra-cxxflags=-D_GLIBCXX_USE_CXX11_ABI=0",*/
        "--minimized-build",
        "--enable-modules=ffi,aes,aes_ni,auto_rng,cbc,dh,ecc_key,ecdh," +
            "ecdsa,hash,hkdf,hmac,kdf,kdf1,kdf2,keypair,pubkey,raw_hash,rng,sha2_64,simd,simd_avx2,simd_avx512,system_rng",
    )
}

tasks.getByName("compileKotlinLinux").dependsOn("buildBotanLinux", "buildHIDLinux")
tasks.getByName("cinteropBotanLinux").dependsOn("buildBotanLinux")
tasks.getByName("compileKotlinWindows").dependsOn("buildBotanWindows", "buildHIDWindows")
tasks.getByName("cinteropBotanWindows").dependsOn("buildBotanWindows")
