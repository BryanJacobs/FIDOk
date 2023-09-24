package us.q3q.fidok.crypto

import us.q3q.fidok.ctap.PinUVToken

/**
 * Implements PIN/UV protocol version one.
 *
 * This is significantly worse than protocol two, so should only be used with less-feature-rich
 * Authenticators.
 *
 * @param cryptoProvider Provider of the cryptographic capabilities necessary for the protocol
 */
class PinUVProtocolV1(private val cryptoProvider: CryptoProvider) : PinUVProtocol {

    private val EMPTY_IV = ByteArray(16) { 0x00 }

    override fun encrypt(key: KeyAgreementPlatformKey, data: ByteArray): ByteArray {
        return cryptoProvider.aes256CBCEncrypt(data, AES256Key(key.pinUvProtocol1Key, EMPTY_IV))
    }

    override fun decrypt(key: KeyAgreementPlatformKey, data: ByteArray): ByteArray {
        return cryptoProvider.aes256CBCDecrypt(data, AES256Key(key.pinUvProtocol1Key, EMPTY_IV))
    }

    private fun underlyingAuthenticate(key: ByteArray, data: ByteArray): ByteArray {
        return cryptoProvider.hmacSHA256(data, AES256Key(key, EMPTY_IV))
            .hash.copyOfRange(0, 16)
    }

    override fun authenticate(key: KeyAgreementPlatformKey, data: ByteArray): ByteArray {
        return underlyingAuthenticate(key.pinUvProtocol1Key, data)
    }

    override fun authenticate(pinUVToken: PinUVToken, data: ByteArray): ByteArray {
        return underlyingAuthenticate(pinUVToken.token, data)
    }

    override fun getVersion(): UByte {
        return 1u
    }
}
