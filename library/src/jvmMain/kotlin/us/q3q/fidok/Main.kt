package us.q3q.fidok

import us.q3q.fidok.cli.DefaultCliCallbacks
import us.q3q.fidok.cli.Main
import us.q3q.fidok.ctap.FIDOkLibrary

/**
 * Entry point for the FIDOk command line, JVM edition :-)
 */
fun main(args: Array<String>) {
    val libraryPath = getNativeLibraryPathForPlatform()

    Main(
        mapOf(
            "native" to NativeDeviceListing(libraryPath),
        ),
    ) {
        FIDOkLibrary.init(
            NativeBackedCryptoProvider(libraryPath),
            it,
            DefaultCliCallbacks(),
        )
    }.main(args)
}
