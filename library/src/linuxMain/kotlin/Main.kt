import us.q3q.fidok.BotanCryptoProvider
import us.q3q.fidok.LibHIDDevice
import us.q3q.fidok.LibPCSCLiteDevice
import us.q3q.fidok.ctap.FIDOkLibrary

val PROVIDER_MAP = mapOf(
    "hid" to LibHIDDevice,
    "pcsc" to LibPCSCLiteDevice,
)

fun main(args: Array<String>) {
    Main(PROVIDER_MAP) {
        FIDOkLibrary.init(
            BotanCryptoProvider(),
            authenticatorAccessors = it,
            pinCollection = pinCollection@{
                print("Enter authenticator PIN for $it: ")
                var pin = readlnOrNull() ?: return@pinCollection null
                if (pin.endsWith('\n')) {
                    pin = pin.substring(0, pin.length - 1)
                }
                pin
            },
        )
    }.execute(args)
}
