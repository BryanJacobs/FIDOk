package us.q3q.fidok

import us.q3q.fidok.ctap.CTAPClient

fun main() {
    val libraryPath = loadNativeLibraryForPlatform()

    val numDevices = NativeDeviceListing(libraryPath).list()
    if (numDevices < 1) {
        throw RuntimeException("No native devices found")
    }

    val device = NativeBackedDevice(libraryPath, 0)
    val client = CTAPClient(device)

    println(client.getInfo())
}
