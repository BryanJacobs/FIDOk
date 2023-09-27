package us.q3q.fidok.ctap.commands

import us.q3q.fidok.crypto.NullCryptoProvider
import us.q3q.fidok.ctap.CTAPClient
import us.q3q.fidok.ctap.FIDOkLibrary

class Examples {
    companion object {
        internal fun getLibrary(): FIDOkLibrary {
            return FIDOkLibrary.init(NullCryptoProvider())
        }

        internal fun getCTAPClient(): CTAPClient {
            val library = getLibrary()
            return library.ctapClient(library.listDevices().first())
        }
    }
}
