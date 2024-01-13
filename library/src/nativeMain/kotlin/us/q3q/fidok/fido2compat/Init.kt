@file:Suppress("FunctionName", "unused")

package us.q3q.fidok.fido2compat

import us.q3q.fidok.BotanCryptoProvider
import us.q3q.fidok.ctap.CTAPError
import us.q3q.fidok.ctap.CTAPResponse
import us.q3q.fidok.ctap.DeviceCommunicationException
import us.q3q.fidok.ctap.FIDOkLibrary
import us.q3q.fidok.platformDeviceProviders
import kotlin.experimental.ExperimentalNativeApi

enum class FidoCompatErrors(val v: Int) {
    FIDO_OK(0),
    FIDO_ERR_TX(-1),
    FIDO_ERR_RX(-2),
    FIDO_ERR_RX_NOT_CBOR(-3),
    FIDO_ERR_RX_INVALID_CBOR(-4),
    FIDO_ERR_INVALID_PARAM(-5),
    FIDO_ERR_INVALID_SIG(-6),
    FIDO_ERR_INVALID_ARGUMENT(-7),
    FIDO_ERR_USER_PRESENCE_REQUIRED(-8),
    FIDO_ERR_INTERNAL(-9),
    FIDO_ERR_NOTFOUND(-10),
    FIDO_ERR_COMPRESS(-11),
}

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

@OptIn(ExperimentalNativeApi::class)
@CName("fido_strerr")
fun fido_strerr(n: Int): String {
    return if (n <= 0) {
        FidoCompatErrors.entries.firstOrNull { it.v == n }
    } else {
        CTAPResponse.entries.firstOrNull { it.value.toInt() == n }
    }?.name ?: "Unknown error $n"
}

fun get_fidocompat_lib(): FIDOkLibrary {
    return library
        ?: throw IllegalStateException("fido_init not called")
}

fun fido_do_with_error_handling(c: () -> Unit): Int {
    try {
        c()
        return FidoCompatErrors.FIDO_OK.v
    } catch (e: CTAPError) {
        return e.code.toInt()
    } catch (e: DeviceCommunicationException) {
        return FidoCompatErrors.FIDO_ERR_TX.v
    }
}
