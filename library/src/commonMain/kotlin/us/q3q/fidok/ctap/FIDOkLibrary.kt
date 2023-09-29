package us.q3q.fidok.ctap

import us.q3q.fidok.cable.CaBLESupport
import us.q3q.fidok.crypto.CryptoProvider
import kotlin.jvm.JvmStatic

class FIDOkLibrary private constructor(
    val cryptoProvider: CryptoProvider,
    private val authenticatorAccessors: List<AuthenticatorListing>,
) {

    companion object {
        @JvmStatic
        fun init(cryptoProvider: CryptoProvider, authenticatorAccessors: List<AuthenticatorListing> = listOf()): FIDOkLibrary {
            return FIDOkLibrary(cryptoProvider, authenticatorAccessors)
        }
    }

    fun listDevices(allowedTransports: List<AuthenticatorTransport>? = null): Array<AuthenticatorDevice> {
        val devices = arrayListOf<AuthenticatorDevice>()
        for (accessor in authenticatorAccessors) {
            devices.addAll(
                accessor.listDevices().filter {
                    if (allowedTransports == null) {
                        true
                    } else {
                        var match = false
                        for (transport in it.getTransports()) {
                            if (allowedTransports.contains(transport)) {
                                match = true
                                break
                            }
                        }
                        match
                    }
                },
            )
        }
        return devices.toTypedArray()
    }

    fun caBLESupport(): CaBLESupport {
        return CaBLESupport(this)
    }

    fun ctapClient(device: AuthenticatorDevice, collectPinFromUser: suspend () -> String? = { null }): CTAPClient {
        return CTAPClient(this, device, collectPinFromUser)
    }
}
