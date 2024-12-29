package us.q3q.fidok.ui

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.datlag.kcef.KCEF
import dev.datlag.kcef.KCEFBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import us.q3q.fidok.NativeDeviceListing
import us.q3q.fidok.PureJVMCryptoProvider
import us.q3q.fidok.ctap.AuthenticatorDevice
import us.q3q.fidok.ctap.FIDOkLibrary
import java.io.File

fun main() {
    val resourcesDirPath =
        System.getProperty("compose.application.resources.dir")
            ?: throw IllegalStateException("Could not find native libraries!")

    val os = System.getProperty("os.name").lowercase()

    val libFileName =
        if (os.contains("linux")) {
            "libfidok.so"
        } else if (os.contains("windows")) {
            "libfidok.dll"
        } else {
            "libfidok.dylib"
        }

    val libPath = File(resourcesDirPath).resolve(libFileName).absolutePath

    val lister = NativeDeviceListing(libPath)
    val library =
        FIDOkLibrary.init(
            PureJVMCryptoProvider(),
            authenticatorAccessors = listOf(lister),
        )
    application {
        Window(title = "FIDOk", onCloseRequest = ::exitApplication) {
            var devices by remember { mutableStateOf<List<AuthenticatorDevice>>(listOf()) }

            var restartRequired by remember { mutableStateOf(false) }
            var downloading by remember { mutableStateOf(0F) }
            var initialized by remember { mutableStateOf(false) }
            val download: KCEFBuilder.Download = remember { KCEFBuilder.Download.Builder().github().build() }

            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    KCEF.init(builder = {
                        installDir(File("kcef-bundle"))

                        /*
                          Add this code when using JDK 17.
                          Builder().github {
                              release("jbr-release-17.0.10b1087.23")
                          }.buffer(download.bufferSize).build()
                         */
                        progress {
                            onDownloading {
                                downloading = if (it > 0F) it else 0F
                            }
                            onInitialized {
                                initialized = true
                            }
                        }
                        settings {
                            cachePath = File("cache").absolutePath
                        }
                    }, onError = {
                        it?.printStackTrace()
                    }, onRestartRequired = {
                        restartRequired = true
                    })
                }
            }

            MaterialTheme {
                if (restartRequired) {
                    Text(text = "Restart required.")
                } else {
                    if (initialized) {
                        MainView(library, devices = devices, onListDevices = {
                            devices = library.listDevices().toList()
                        })
                    } else {
                        Text(text = "Downloading $downloading%")
                    }
                }
            }

            DisposableEffect(Unit) {
                onDispose {
                    KCEF.disposeBlocking()
                }
            }
        }
    }
}
