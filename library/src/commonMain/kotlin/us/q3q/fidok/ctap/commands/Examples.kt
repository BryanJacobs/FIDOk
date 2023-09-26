package us.q3q.fidok.ctap.commands

import us.q3q.fidok.crypto.NullCryptoProvider
import us.q3q.fidok.ctap.CTAPClient
import us.q3q.fidok.ctap.FIDOkLibrary

class Examples {
    companion object {
        fun getCTAPClient(): CTAPClient {
            val library = FIDOkLibrary.init(NullCryptoProvider())
            return library.ctapClient(library.listDevices().first())
        }
    }
}
