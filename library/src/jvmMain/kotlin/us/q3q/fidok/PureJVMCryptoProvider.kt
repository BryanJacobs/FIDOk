package us.q3q.fidok

import us.q3q.fidok.crypto.AESKey
import us.q3q.fidok.crypto.CryptoProvider
import us.q3q.fidok.crypto.KeyAgreementResult
import us.q3q.fidok.crypto.KeyAgreementState
import us.q3q.fidok.crypto.P256Point
import us.q3q.fidok.crypto.SHA256Result
import us.q3q.fidok.crypto.X509Info
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * A [CryptoProvider] using only the JVM's provided cryptography functions.
 *
 * Depending on the JVM implementation, this might throw exceptions at run time. Or it might
 * work fine. All cryptographic operations are delegated to the JVM's built-in implementations.
 */
class PureJVMCryptoProvider : CryptoProvider {
    private fun pointsToECPK(
        x: ByteArray,
        y: ByteArray,
        spec: ECParameterSpec,
    ): PublicKey {
        val pubKeySpec =
            ECPublicKeySpec(
                ECPoint(
                    BigInteger(1, x),
                    BigInteger(1, y),
                ),
                spec,
            )
        val kf = KeyFactory.getInstance("EC")
        return kf.generatePublic(pubKeySpec)
    }

    private fun fixedLengthByteArray(
        i: BigInteger,
        len: Int,
    ): ByteArray {
        var variableLengthByteArray = i.toByteArray().toList()
        if (variableLengthByteArray.size > len + 1) {
            throw IllegalArgumentException(
                "Incoming big integer is ${variableLengthByteArray.size} " +
                    "bytes long, but a $len byte long one is requested",
            )
        }
        if (variableLengthByteArray.size > len) {
            // Drop all-zero leading byte
            if (variableLengthByteArray[0] != 0x00.toByte()) {
                throw IllegalArgumentException("Incoming big integer has the wrong length and a non-zero leading byte")
            }
            variableLengthByteArray = variableLengthByteArray.subList(1, variableLengthByteArray.size)
        }
        val ret = arrayListOf<Byte>()
        repeat(len - variableLengthByteArray.size) {
            ret.add(0x00)
        }
        ret += variableLengthByteArray
        return ret.toByteArray()
    }

    override fun ecdhKeyAgreementInit(otherPublicKeyPoint: P256Point): KeyAgreementState {
        val curveParams = ECGenParameterSpec("secp256r1")

        val keyGen = KeyPairGenerator.getInstance("EC")
        keyGen.initialize(curveParams)
        val keyPair = keyGen.genKeyPair()
        val publicKey = keyPair.public

        val otherPubKey =
            pointsToECPK(
                otherPublicKeyPoint.x,
                otherPublicKeyPoint.y,
                (publicKey as ECPublicKey).params,
            )

        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(keyPair.private)
        ka.doPhase(otherPubKey, true)

        return KeyAgreementState(
            localPublicX = fixedLengthByteArray(publicKey.w.affineX, 32),
            localPublicY = fixedLengthByteArray(publicKey.w.affineY, 32),
            opaqueState = ka.generateSecret(),
        )
    }

    override fun ecdhKeyAgreementKDF(
        state: KeyAgreementState,
        otherPublicKeyPoint: P256Point,
        useHKDF: Boolean,
        salt: ByteArray,
        info: ByteArray,
    ): KeyAgreementResult {
        if (useHKDF) {
            // HKDF step one: get PRK by using salt as a key to HMAC-SHA256 the IKM
            val prk = hmacSHA256(state.opaqueState as ByteArray, AESKey(salt, null))

            // HKDF step two: use PRK to hash the "info" array given
            val res = hmacSHA256((info.toList() + listOf(0x01)).toByteArray(), AESKey(prk.hash, null))

            return KeyAgreementResult(res.hash)
        }
        return KeyAgreementResult(sha256(state.opaqueState as ByteArray).hash)
    }

    override fun ecdhKeyAgreementDestroy(state: KeyAgreementState) {
        // NOP
    }

    override fun sha256(data: ByteArray): SHA256Result {
        return SHA256Result(MessageDigest.getInstance("SHA-256").digest(data))
    }

    override fun secureRandom(numBytes: Int): ByteArray {
        return Random.nextBytes(numBytes)
    }

    private fun aes(
        mode: Int,
        bytes: ByteArray,
        key: AESKey,
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        val iv = key.iv?.let { IvParameterSpec(it) }
        cipher.init(mode, SecretKeySpec(key.key, "AES"), iv)
        return cipher.doFinal(bytes)
    }

    override fun aes256CBCEncrypt(
        bytes: ByteArray,
        key: AESKey,
    ): ByteArray {
        return aes(Cipher.ENCRYPT_MODE, bytes, key)
    }

    override fun aes128CBCEncrypt(
        bytes: ByteArray,
        key: AESKey,
    ): ByteArray {
        return aes(Cipher.ENCRYPT_MODE, bytes, key)
    }

    override fun aes128CBCDecrypt(
        bytes: ByteArray,
        key: AESKey,
    ): ByteArray {
        return aes(Cipher.DECRYPT_MODE, bytes, key)
    }

    override fun aes256CBCDecrypt(
        bytes: ByteArray,
        key: AESKey,
    ): ByteArray {
        return aes(Cipher.DECRYPT_MODE, bytes, key)
    }

    override fun hmacSHA256(
        bytes: ByteArray,
        key: AESKey,
    ): SHA256Result {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.key, "AES"))
        return SHA256Result(mac.doFinal(bytes))
    }

    override fun es256SignatureValidate(
        signedBytes: ByteArray,
        key: P256Point,
        sig: ByteArray,
    ): Boolean {
        val curve = ECGenParameterSpec("secp256r1")
        val parameters = AlgorithmParameters.getInstance("EC")
        parameters.init(curve)
        val ecParameters = parameters.getParameterSpec(ECParameterSpec::class.java)

        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(pointsToECPK(key.x, key.y, ecParameters))
        verifier.update(signedBytes)
        return verifier.verify(sig)
    }

    override fun parseES256X509(x509Bytes: ByteArray): X509Info {
        val cf = CertificateFactory.getInstance("X.509")
        val cert = cf.generateCertificate(ByteArrayInputStream(x509Bytes))
        val pk = cert.publicKey as ECPublicKey
        return X509Info(
            publicX = fixedLengthByteArray(pk.w.affineX, 32),
            publicY = fixedLengthByteArray(pk.w.affineY, 32),
            // TODO
            aaguid = null,
        )
    }
}
