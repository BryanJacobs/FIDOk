import us.q3q.fidok.BotanCryptoProvider
import us.q3q.fidok.LibHIDDevice
import us.q3q.fidok.MacPCSCLiteDevice
import us.q3q.fidok.cli.DefaultCliCallbacks
import us.q3q.fidok.cli.Main
import us.q3q.fidok.ctap.FIDOkLibrary

fun main(args: Array<String>) {
    Main(
        mapOf(
            "hid" to LibHIDDevice,
            "pcsc" to MacPCSCLiteDevice,
        ),
        libraryBuilder = {
            FIDOkLibrary.init(
                BotanCryptoProvider(),
                it,
                callbacks = DefaultCliCallbacks()
            )
        },
    ).main(args)
}
