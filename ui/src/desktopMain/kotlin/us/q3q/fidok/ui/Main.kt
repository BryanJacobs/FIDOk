package us.q3q.fidok.ui

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import us.q3q.fidok.NativeDeviceListing
import us.q3q.fidok.PureJVMCryptoProvider
import us.q3q.fidok.ctap.AuthenticatorDevice
import us.q3q.fidok.ctap.FIDOkLibrary
import java.io.File

fun main() {
    val resourcesDirPath = System.getProperty("compose.application.resources.dir")
        ?: throw IllegalStateException("Could not find native libraries!")
    val os = System.getProperty("os.name")
    val libFileName = if (os.contains("Linux")) "libfidok.so" else "libfidok.dylib"
    val libPath = File(resourcesDirPath).resolve(libFileName).absolutePath

    val botanFileName = if (os.contains("Linux")) "libbotan-3.so.2" else "libbotan-3.2.dylib"
    val botanPath = File(resourcesDirPath).resolve(botanFileName).absolutePath
    System.load(botanPath)

    val lister = NativeDeviceListing(libPath)
    val library = FIDOkLibrary.init(
        PureJVMCryptoProvider(),
        authenticatorAccessors = listOf(lister),
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
