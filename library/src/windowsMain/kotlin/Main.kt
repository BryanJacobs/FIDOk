import us.q3q.fidok.BotanCryptoProvider
import us.q3q.fidok.LibHIDDevice
import us.q3q.fidok.PCSCDevice
import us.q3q.fidok.cli.DefaultCliCallbacks
import us.q3q.fidok.cli.Main
import us.q3q.fidok.ctap.FIDOkLibrary

fun main(args: Array<String>) {
    Main(
        mapOf(
            "hid" to LibHIDDevice,
            "pcsc" to PCSCDevice,
        ),
        libraryBuilder = {
            FIDOkLibrary.init(
                BotanCryptoProvider(),
                it,
                DefaultCliCallbacks(),
            )
        },
    ).main(args)
}
