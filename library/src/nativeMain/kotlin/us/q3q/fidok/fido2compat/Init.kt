@file:Suppress("FunctionName", "unused")

package us.q3q.fidok.fido2compat

import us.q3q.fidok.BotanCryptoProvider
import us.q3q.fidok.ctap.CTAPError
import us.q3q.fidok.ctap.DeviceCommunicationException
import us.q3q.fidok.ctap.FIDOkLibrary
import us.q3q.fidok.platformDeviceProviders
import kotlin.experimental.ExperimentalNativeApi

internal var library: FIDOkLibrary? = null

@OptIn(ExperimentalNativeApi::class)
@CName("fido_init")
fun fido_init(flags: Int) {
    if (library == null) {
        library =
            FIDOkLibrary.init(
                cryptoProvider = BotanCryptoProvider(),
                authenticatorAccessors = platformDeviceProviders(),
            )
    }
}

fun get_fidocompat_lib(): FIDOkLibrary {
    return library
        ?: throw IllegalStateException("fido_init not called")
}

fun fido_do_with_error_handling(c: () -> Unit): Int {
    try {
        c()
        return FIDO_OK
    } catch (e: CTAPError) {
        return e.code.toInt()
    } catch (e: DeviceCommunicationException) {
        return FIDO_ERR_TX
    }
}
