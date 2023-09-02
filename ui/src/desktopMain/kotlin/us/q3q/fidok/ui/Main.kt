package us.q3q.fidok.ui

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import us.q3q.fidok.NativeBackedDevice
import us.q3q.fidok.NativeDeviceListing
import java.io.File

fun main() {
    val resourcesDirPath = System.getProperty("compose.application.resources.dir")
        ?: throw IllegalStateException("Could not find native libraries!")
    val libPath = File(resourcesDirPath).resolve("libfidok.so").absolutePath
    val lister = NativeDeviceListing(libPath)
    application {
        Window(onCloseRequest = ::exitApplication) {
            MainView {
                val numDevs = lister.list()
                (0..<numDevs).map {
                    NativeBackedDevice(libPath, it)
                }
            }
        }
    }
}
