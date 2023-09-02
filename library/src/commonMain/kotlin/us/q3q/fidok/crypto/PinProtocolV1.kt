package us.q3q.fidok.crypto

import us.q3q.fidok.ctap.Library
import us.q3q.fidok.ctap.PinToken

class PinProtocolV1 : PinProtocol {

    private val EMPTY_IV = ByteArray(16) { 0x00 }

    override fun encrypt(key: KeyAgreementPlatformKey, data: ByteArray): ByteArray {
        val crypto = Library.cryptoProvider ?: throw IllegalStateException("Library not initialized")

        return crypto.aes256CBCEncrypt(data, AES256Key(key.pinProtocol1Key, EMPTY_IV))
    }

    override fun decrypt(key: KeyAgreementPlatformKey, data: ByteArray): ByteArray {
        val crypto = Library.cryptoProvider ?: throw IllegalStateException("Library not initialized")

        return crypto.aes256CBCDecrypt(data, AES256Key(key.pinProtocol1Key, EMPTY_IV))
    }

    private fun underlyingAuthenticate(key: ByteArray, data: ByteArray): ByteArray {
        val crypto = Library.cryptoProvider ?: throw IllegalStateException("Library not initialized")

        return crypto.hmacSHA256(data, AES256Key(key, EMPTY_IV))
            .hash.copyOfRange(0, 16)
    }

    override fun authenticate(key: KeyAgreementPlatformKey, data: ByteArray): ByteArray {
        return underlyingAuthenticate(key.pinProtocol1Key, data)
    }

    override fun authenticate(pinToken: PinToken, data: ByteArray): ByteArray {
        return underlyingAuthenticate(pinToken.token, data)
    }

    override fun getVersion(): UByte {
        return 1u
    }
}
