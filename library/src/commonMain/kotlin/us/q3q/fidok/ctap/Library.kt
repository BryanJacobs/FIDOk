package us.q3q.fidok.ctap

import us.q3q.fidok.cable.CaBLESupport
import us.q3q.fidok.crypto.CryptoProvider
import kotlin.jvm.JvmStatic

class Library private constructor(
    val cryptoProvider: CryptoProvider,
    private val deviceAccessors: List<DeviceListing>,
) {

    companion object {
        @JvmStatic
        fun init(cryptoProvider: CryptoProvider, deviceAccessors: List<DeviceListing> = listOf()): Library {
            return Library(cryptoProvider, deviceAccessors)
        }
    }

    fun listDevices(allowedTransports: List<AuthenticatorTransport>? = null): Array<Device> {
        val devices = arrayListOf<Device>()
        for (accessor in deviceAccessors) {
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

    fun ctapClient(device: Device): CTAPClient {
        return CTAPClient(this, device)
    }
}
