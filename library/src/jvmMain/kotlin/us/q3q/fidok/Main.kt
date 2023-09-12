package us.q3q.fidok

import us.q3q.fidok.ctap.CTAPClient
import us.q3q.fidok.ctap.Library

fun main() {
    var osName = System.getProperty("os.name")
    if (osName.startsWith("Windows")) {
        osName = "Windows"
    }
    if (osName.startsWith("Mac")) {
        osName = "Mac"
    }
    val soSuffix = when (osName) {
        "Linux" -> "so"
        "Windows" -> "dll"
        "Mac" -> "dylib"
        else -> throw NotImplementedError("Unknown operating system $osName")
    }
    val libraryPath = "build/bin/${osName.lowercase()}/fidokDebugShared/libfidok.$soSuffix"

    Library.init(NativeBackedCryptoProvider(libraryPath))

    val numDevices = NativeDeviceListing(libraryPath).list()
    if (numDevices < 1) {
        throw RuntimeException("No native devices found")
    }

    val device = NativeBackedDevice(libraryPath, 0)
    val client = CTAPClient(device)

    println(client.getInfo())
}
