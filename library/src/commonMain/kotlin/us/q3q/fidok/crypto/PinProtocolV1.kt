package us.q3q.fidok.crypto

import us.q3q.fidok.ctap.PinToken

class PinProtocolV1(private val cryptoProvider: CryptoProvider) : PinProtocol {

    private val EMPTY_IV = ByteArray(16) { 0x00 }

    override fun encrypt(key: KeyAgreementPlatformKey, data: ByteArray): ByteArray {
        return cryptoProvider.aes256CBCEncrypt(data, AES256Key(key.pinProtocol1Key, EMPTY_IV))
    }

    override fun decrypt(key: KeyAgreementPlatformKey, data: ByteArray): ByteArray {
        return cryptoProvider.aes256CBCDecrypt(data, AES256Key(key.pinProtocol1Key, EMPTY_IV))
    }

    private fun underlyingAuthenticate(key: ByteArray, data: ByteArray): ByteArray {
        return cryptoProvider.hmacSHA256(data, AES256Key(key, EMPTY_IV))
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
