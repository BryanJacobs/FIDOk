package us.q3q.fidok.crypto

class NullCryptoProvider : CryptoProvider {
    override fun ecdhKeyAgreementInit(otherPublicKeyPoint: P256Point): KeyAgreementState {
        throw NotImplementedError("Null crypto provider")
    }

    override fun ecdhKeyAgreementKDF(
        state: KeyAgreementState,
        otherPublicKeyPoint: P256Point,
        useHKDF: Boolean,
        salt: ByteArray,
        info: ByteArray,
    ): KeyAgreementResult {
        throw NotImplementedError("Null crypto provider")
    }

    override fun ecdhKeyAgreementDestroy(state: KeyAgreementState) {
        throw NotImplementedError("Null crypto provider")
    }

    override fun sha256(data: ByteArray): SHA256Result {
        throw NotImplementedError("Null crypto provider")
    }

    override fun secureRandom(numBytes: Int): ByteArray {
        throw NotImplementedError("Null crypto provider")
    }

    override fun aes256CBCEncrypt(bytes: ByteArray, key: AES256Key): ByteArray {
        throw NotImplementedError("Null crypto provider")
    }

    override fun aes256CBCDecrypt(bytes: ByteArray, key: AES256Key): ByteArray {
        throw NotImplementedError("Null crypto provider")
    }

    override fun hmacSHA256(bytes: ByteArray, key: AES256Key): SHA256Result {
        throw NotImplementedError("Null crypto provider")
    }

    override fun es256SignatureValidate(
        signedBytes: ByteArray,
        keyX: ByteArray,
        keyY: ByteArray,
        sig: ByteArray,
    ): Boolean {
        throw NotImplementedError("Null crypto provider")
    }

    override fun parseES256X509(x509Bytes: ByteArray): X509Info {
        throw NotImplementedError("Null crypto provider")
    }
}
