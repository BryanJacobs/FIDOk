import us.q3q.fidok.BotanCryptoProvider
import us.q3q.fidok.LibHIDDevice
import us.q3q.fidok.LibPCSCLiteDevice
import us.q3q.fidok.cli.DefaultCliCallbacks
import us.q3q.fidok.cli.Main
import us.q3q.fidok.ctap.FIDOkLibrary

val PROVIDER_MAP =
    mapOf(
        "hid" to LibHIDDevice,
        "pcsc" to LibPCSCLiteDevice,
    )

fun main(args: Array<String>) {
    Main(PROVIDER_MAP) {
        FIDOkLibrary.init(
            BotanCryptoProvider(),
            authenticatorAccessors = it,
            DefaultCliCallbacks(),
        )
    }.main(args)
}
