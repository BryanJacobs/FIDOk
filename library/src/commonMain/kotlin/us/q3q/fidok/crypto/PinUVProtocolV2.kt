package us.q3q.fidok.crypto

import us.q3q.fidok.ctap.PinUVToken

/**
 * Implements PIN/UV protocol version two.
 *
 * @param cryptoProvider Provider of the cryptographic capabilities necessary for the protocol
 */
class PinUVProtocolV2(private val cryptoProvider: CryptoProvider) : PinUVProtocol {

    private val EMPTY_IV = ByteArray(16) { 0x00 }

    override fun encrypt(key: KeyAgreementPlatformKey, data: ByteArray): ByteArray {
        val newIV = cryptoProvider.secureRandom(16)

        return (
            newIV.toList() +
                cryptoProvider.aes256CBCEncrypt(data, AES256Key(key.pinUvProtocol2AESKey, newIV)).toList()
            ).toByteArray()
    }

    override fun decrypt(key: KeyAgreementPlatformKey, data: ByteArray): ByteArray {
        if (data.size < 16) {
            throw IllegalArgumentException("Data to decrypt must be at least 16 bytes long")
        }

        return cryptoProvider.aes256CBCDecrypt(
            data.copyOfRange(16, data.size),
            AES256Key(key.pinUvProtocol2AESKey, data.copyOfRange(0, 16)),
        )
    }

    private fun underlyingAuthenticate(key: ByteArray, data: ByteArray): ByteArray {
        return cryptoProvider.hmacSHA256(data, AES256Key(key, EMPTY_IV)).hash
    }

    override fun authenticate(key: KeyAgreementPlatformKey, data: ByteArray): ByteArray {
        return underlyingAuthenticate(key.pinUvProtocol2HMACKey, data)
    }

    override fun authenticate(pinUVToken: PinUVToken, data: ByteArray): ByteArray {
        return underlyingAuthenticate(pinUVToken.token, data)
    }

    override fun getVersion(): UByte {
        return 2u
    }
}
