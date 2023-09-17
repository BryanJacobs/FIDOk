package us.q3q.fidok

import us.q3q.fidok.ctap.Library

fun loadNativeLibraryForPlatform(): String {
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

    return libraryPath
}
