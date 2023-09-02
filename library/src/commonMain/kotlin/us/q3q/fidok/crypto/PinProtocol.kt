package us.q3q.fidok.crypto

import us.q3q.fidok.ctap.PinToken

interface PinProtocol {
    fun encrypt(key: KeyAgreementPlatformKey, data: ByteArray): ByteArray
    fun decrypt(key: KeyAgreementPlatformKey, data: ByteArray): ByteArray
    fun authenticate(key: KeyAgreementPlatformKey, data: ByteArray): ByteArray
    fun authenticate(pinToken: PinToken, data: ByteArray): ByteArray
    fun verify(key: KeyAgreementPlatformKey, data: ByteArray, signature: ByteArray): Boolean {
        val result = authenticate(key, data)
        return result.contentEquals(signature)
    }

    fun getVersion(): UByte
}
