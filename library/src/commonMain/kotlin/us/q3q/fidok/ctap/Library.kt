package us.q3q.fidok.ctap

import us.q3q.fidok.crypto.CryptoProvider

class Library {
    fun listDevices(): Array<Device> {
        return arrayOf()
    }

    companion object {
        var cryptoProvider: CryptoProvider? = null
            private set(cryptoProvider) {
                field = cryptoProvider
            }

        fun init(cryptoProvider: CryptoProvider) {
            this.cryptoProvider = cryptoProvider
        }
    }
}
