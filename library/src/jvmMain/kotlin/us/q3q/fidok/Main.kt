package us.q3q.fidok

import us.q3q.fidok.cli.Main
import us.q3q.fidok.cli.cliPinCollection
import us.q3q.fidok.ctap.FIDOkLibrary

fun main(args: Array<String>) {
    val libraryPath = getNativeLibraryPathForPlatform()

    Main(
        mapOf(
            "native" to NativeDeviceListing(libraryPath),
        ),
        {
            FIDOkLibrary.init(
                NativeBackedCryptoProvider(libraryPath),
                it,
                ::cliPinCollection,
            )
        },
    ).main(args)
}
