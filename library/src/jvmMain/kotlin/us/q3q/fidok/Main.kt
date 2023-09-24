package us.q3q.fidok

import us.q3q.fidok.ctap.FIDOkLibrary

fun main() {
    val libraryPath = getNativeLibraryPathForPlatform()

    val library = FIDOkLibrary.init(NativeBackedCryptoProvider(libraryPath))

    val numDevices = NativeDeviceListing(libraryPath).list()
    if (numDevices < 1) {
        throw RuntimeException("No native devices found")
    }

    val device = NativeBackedDevice(libraryPath, 0)
    val client = library.ctapClient(device)

    println(client.getInfo())
}
