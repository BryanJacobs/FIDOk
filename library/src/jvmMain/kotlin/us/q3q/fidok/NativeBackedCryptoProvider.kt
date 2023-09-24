package us.q3q.fidok

import us.q3q.fidok.crypto.AES256Key
import us.q3q.fidok.crypto.CryptoProvider
import us.q3q.fidok.crypto.KeyAgreementResult
import us.q3q.fidok.crypto.KeyAgreementState
import us.q3q.fidok.crypto.P256Point
import us.q3q.fidok.crypto.SHA256Result
import us.q3q.fidok.crypto.X509Info
import java.nio.ByteBuffer

class NativeBackedCryptoProvider(libraryPath: String) : NativeLibraryUser(libraryPath), CryptoProvider {

    override fun ecdhKeyAgreementInit(otherPublicKeyPoint: P256Point): KeyAgreementState {
        val outX = ByteBuffer.allocateDirect(32)
        val outY = ByteBuffer.allocateDirect(32)
        val ret = native.fidok_crypto_ecdh_init(
            toBB(otherPublicKeyPoint.x),
            toBB(otherPublicKeyPoint.y),
            outX,
            outY,
        )
        return KeyAgreementState(toBA(outX), toBA(outY), ret)
    }

    override fun ecdhKeyAgreementKDF(
        state: KeyAgreementState,
        otherPublicKeyPoint: P256Point,
        useHKDF: Boolean,
        salt: ByteArray,
        info: ByteArray,
    ): KeyAgreementResult {
        val ret = ByteBuffer.allocateDirect(32)
        native.fidok_crypto_ecdh_kdf(
            state.opaqueState as Long, toBB(state.localPublicX),
            toBB(state.localPublicY), useHKDF, toBB(salt), salt.size,
            toBB(info), info.size, ret,
        )
        return KeyAgreementResult(toBA(ret))
    }

    override fun ecdhKeyAgreementDestroy(state: KeyAgreementState) {
        native.fidok_crypto_ecdh_destroy(state.opaqueState as Long)
    }

    override fun sha256(data: ByteArray): SHA256Result {
        val ret = ByteBuffer.allocateDirect(32)
        native.fidok_crypto_sha256(toBB(data), data.size, ret)
        return SHA256Result(toBA(ret))
    }

    override fun secureRandom(numBytes: Int): ByteArray {
        val buffer = ByteBuffer.allocateDirect(numBytes)
        native.fidok_crypto_secure_random(numBytes, buffer)
        return toBA(buffer)
    }

    override fun aes256CBCEncrypt(bytes: ByteArray, key: AES256Key): ByteArray {
        if (key.iv == null) {
            throw IllegalArgumentException("AES encryption requires an IV")
        }
        val ret = ByteBuffer.allocateDirect(bytes.size)
        native.fidok_crypto_aes_256_cbc_encrypt(
            toBB(bytes),
            bytes.size,
            toBB(key.key),
            toBB(key.iv),
            ret,
        )
        return toBA(ret)
    }

    override fun aes256CBCDecrypt(bytes: ByteArray, key: AES256Key): ByteArray {
        if (key.iv == null) {
            throw IllegalArgumentException("AES decryption requires an IV")
        }
        val ret = ByteBuffer.allocateDirect(bytes.size)
        native.fidok_crypto_aes_256_cbc_decrypt(
            toBB(bytes),
            bytes.size,
            toBB(key.key),
            toBB(key.iv),
            ret,
        )
        return toBA(ret)
    }

    override fun hmacSHA256(bytes: ByteArray, key: AES256Key): SHA256Result {
        val ret = ByteBuffer.allocateDirect(32)
        native.fidok_crypto_hmac_sha256(
            toBB(bytes),
            bytes.size,
            toBB(key.key),
            ret,
        )
        return SHA256Result(toBA(ret))
    }

    override fun es256SignatureValidate(
        signedBytes: ByteArray,
        key: P256Point,
        sig: ByteArray,
    ): Boolean {
        return native.fidok_crypto_es256_signature_validate(
            toBB(signedBytes),
            signedBytes.size,
            toBB(key.x),
            toBB(key.y),
            toBB(sig),
            sig.size,
        )
    }

    override fun parseES256X509(x509Bytes: ByteArray): X509Info {
        val keyX = ByteBuffer.allocateDirect(32)
        val keyY = ByteBuffer.allocateDirect(32)

        native.fidok_crypto_parse_es256_x509(
            toBB(x509Bytes),
            x509Bytes.size,
            keyX,
            keyY,
        )

        return X509Info(
            toBA(keyX, 32),
            toBA(keyY, 32),
            null,
        )
    }
}
