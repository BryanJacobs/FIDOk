import us.q3q.fidok.BotanCryptoProvider
import us.q3q.fidok.LibHIDDevice
import us.q3q.fidok.cli.Main
import us.q3q.fidok.cli.cliPinCollection
import us.q3q.fidok.ctap.FIDOkLibrary

fun main(args: Array<String>) {
    Main(
        mapOf(
            "hid" to LibHIDDevice,
            // "pcsc" to PCSCDevice,
        ),
        libraryBuilder = {
            FIDOkLibrary.init(
                BotanCryptoProvider(),
                it,
                pinCollection = ::cliPinCollection,
            )
        },
    ).main(args)
}
