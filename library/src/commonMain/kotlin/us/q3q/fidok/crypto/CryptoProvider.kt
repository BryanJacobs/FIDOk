package us.q3q.fidok.crypto

class KeyAgreementState(
    val localPublicX: ByteArray,
    val localPublicY: ByteArray,
    val opaqueState: Any?,
) {
    init {
        require(localPublicX.size == 32)
        require(localPublicY.size == 32)
    }
}

class KeyAgreementResult(val bytes: ByteArray) {
    init {
        require(bytes.size == 32)
    }
}

interface CryptoProvider {
    fun ecdhKeyAgreementInit(otherPublicKeyPoint: P256Point): KeyAgreementState
    fun ecdhKeyAgreementKDF(
        state: KeyAgreementState,
        otherPublicKeyPoint: P256Point,
        useHKDF: Boolean,
        salt: ByteArray,
        info: ByteArray,
    ): KeyAgreementResult
    fun ecdhKeyAgreementDestroy(state: KeyAgreementState)

    fun sha256(data: ByteArray): SHA256Result

    fun secureRandom(numBytes: Int): ByteArray

    fun aes256CBCEncrypt(bytes: ByteArray, key: AES256Key): ByteArray

    fun aes256CBCDecrypt(bytes: ByteArray, key: AES256Key): ByteArray

    fun hmacSHA256(bytes: ByteArray, key: AES256Key): SHA256Result

    fun es256SignatureValidate(signedBytes: ByteArray, keyX: ByteArray, keyY: ByteArray, sig: ByteArray): Boolean
}
