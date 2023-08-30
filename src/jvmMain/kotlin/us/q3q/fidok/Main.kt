package us.q3q.fidok

import us.q3q.fidok.ctap.CTAPClient
import us.q3q.fidok.ctap.Library

fun main() {
    val libraryPath = "build/bin/native/debugShared/libfidok.so"

    Library.init(NativeBackedCryptoProvider(libraryPath))

    val numDevices = NativeDeviceListing(libraryPath).list()
    if (numDevices < 1) {
        throw RuntimeException("No native devices found")
    }

    val device = NativeBackedDevice(libraryPath, 0)
    val client = CTAPClient(device)

    println(client.getInfo())
}
