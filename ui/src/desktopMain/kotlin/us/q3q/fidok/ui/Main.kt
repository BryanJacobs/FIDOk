package us.q3q.fidok.ui

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import us.q3q.fidok.NativeBackedDevice
import us.q3q.fidok.NativeDeviceListing
import us.q3q.fidok.PureJVMCryptoProvider
import us.q3q.fidok.ctap.AuthenticatorDevice
import us.q3q.fidok.ctap.AuthenticatorListing
import us.q3q.fidok.ctap.FIDOkLibrary
import java.io.File

fun main() {
    val resourcesDirPath = System.getProperty("compose.application.resources.dir")
        ?: throw IllegalStateException("Could not find native libraries!")
    val libPath = File(resourcesDirPath).resolve("libfidok.so").absolutePath
    val lister = NativeDeviceListing(libPath)
    val library = FIDOkLibrary.init(
        PureJVMCryptoProvider(),
        authenticatorAccessors = listOf(
            object : AuthenticatorListing {
                override fun listDevices(): List<AuthenticatorDevice> {
                    val numDevs = lister.list()
                    return (0..<numDevs).map {
                        NativeBackedDevice(libPath, it)
                    }
                }
            },
        ),
    )
    application {
        Window(onCloseRequest = ::exitApplication) {
            var devices by remember { mutableStateOf<List<AuthenticatorDevice>>(listOf()) }
            MaterialTheme {
                MainView(library, devices = devices, onListDevices = {
                    devices = library.listDevices().toList()
                })
            }
        }
    }
}
