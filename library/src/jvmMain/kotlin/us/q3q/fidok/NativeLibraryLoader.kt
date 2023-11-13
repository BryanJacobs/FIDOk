package us.q3q.fidok

fun getNativeLibraryPathForPlatform(): String {
    var osName = System.getProperty("os.name")
    if (osName.startsWith("Windows")) {
        osName = "Windows"
    }
    if (osName.startsWith("Mac")) {
        osName = "Mac"
    }
    val soSuffix =
        when (osName) {
            "Linux" -> "so"
            "Windows" -> "dll"
            "Mac" -> "dylib"
            else -> throw NotImplementedError("Unknown operating system $osName")
        }
    val libraryPath = "build/bin/${osName.lowercase()}/fidokDebugShared/libfidok.$soSuffix"

    return libraryPath
}
