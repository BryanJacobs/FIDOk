package us.q3q.fidok.ctap.commands

import us.q3q.fidok.crypto.NullCryptoProvider
import us.q3q.fidok.ctap.CTAPClient
import us.q3q.fidok.ctap.FIDOkLibrary

/**
 * Helper code for documentation examples.
 */
class Examples {
    companion object {
        internal fun getLibrary(): FIDOkLibrary {
            return FIDOkLibrary.init(NullCryptoProvider())
        }

        internal fun getCTAPClient(): CTAPClient {
            val library = getLibrary()
            val devices = library.listDevices()
            return library.ctapClient(devices.first())
        }
    }
}
