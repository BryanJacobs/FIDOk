@file:Suppress("unused", "FunctionName")
@file:OptIn(ExperimentalNativeApi::class)

package us.q3q.fidok

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import us.q3q.fidok.crypto.AESKey
import us.q3q.fidok.crypto.KeyAgreementState
import us.q3q.fidok.crypto.P256Point
import kotlin.experimental.ExperimentalNativeApi

private val crypto = BotanCryptoProvider()

private val keyAgreementState = hashMapOf<Long, KeyAgreementState>()
private var keyAgreementStateCounter = 0L

@OptIn(ExperimentalForeignApi::class)
@CName("fidok_crypto_ecdh_init")
fun ecdh_init(
    x: COpaquePointer,
    y: COpaquePointer,
    outX: COpaquePointer,
    outY: COpaquePointer,
): Long {
    val xB = inAsByteArray(x, 32)
    val yB = inAsByteArray(y, 32)
    val state = crypto.ecdhKeyAgreementInit(P256Point(xB, yB))
    val counter = keyAgreementStateCounter++
    keyAgreementState[counter] = state

    outFill(state.localPublicX, outX)
    outFill(state.localPublicY, outY)

    return counter
}

@OptIn(ExperimentalForeignApi::class)
@CName("fidok_crypto_ecdh_kdf")
fun ecdh_kdf(
    state: Long,
    x: COpaquePointer,
    y: COpaquePointer,
    useHKDF: Boolean,
    salt: COpaquePointer,
    saltLen: Int,
    info: COpaquePointer,
    infoLen: Int,
    out: COpaquePointer,
): Int {
    val retrievedState = keyAgreementState[state] ?: return -1

    val xB = inAsByteArray(x, 32)
    val yB = inAsByteArray(y, 32)
    val saltB = inAsByteArray(salt, saltLen)
    val infoB = inAsByteArray(info, infoLen)

    val ret =
        crypto.ecdhKeyAgreementKDF(
            retrievedState,
            P256Point(xB, yB),
            useHKDF,
            saltB,
            infoB,
        )

    outFill(ret.bytes, out)
    return 0
}

@CName("fidok_crypto_ecdh_destroy")
fun ecdh_destroy(state: Long) {
    keyAgreementState.remove(state)
}

@OptIn(ExperimentalForeignApi::class)
@CName("fidok_crypto_secure_random")
fun secure_random(
    numBytes: Int,
    out: COpaquePointer,
) {
    val ret = crypto.secureRandom(numBytes)
    outFill(ret, out)
}

@OptIn(ExperimentalForeignApi::class)
@CName("fidok_crypto_sha256")
fun sha256(
    data: COpaquePointer,
    len: Int,
    out: COpaquePointer,
) {
    val inB = inAsByteArray(data, len)
    val ret = crypto.sha256(inB).hash
    outFill(ret, out)
}

@OptIn(ExperimentalForeignApi::class)
@CName("fidok_crypto_aes_256_cbc_encrypt")
fun aes_256_encrypt(
    data: COpaquePointer,
    len: Int,
    key: COpaquePointer,
    iv: COpaquePointer,
    out: COpaquePointer,
) {
    val inB = inAsByteArray(data, len)
    val keyB = inAsByteArray(key, 32)
    val ivB = inAsByteArray(iv, 16)

    val ret = crypto.aes256CBCEncrypt(inB, AESKey(keyB, iv = ivB))

    outFill(ret, out)
}

@OptIn(ExperimentalForeignApi::class)
@CName("fidok_crypto_aes_128_cbc_encrypt")
fun aes_128_encrypt(
    data: COpaquePointer,
    len: Int,
    key: COpaquePointer,
    iv: COpaquePointer,
    out: COpaquePointer,
) {
    val inB = inAsByteArray(data, len)
    val keyB = inAsByteArray(key, 16)
    val ivB = inAsByteArray(iv, 16)

    val ret = crypto.aes128CBCEncrypt(inB, AESKey(keyB, iv = ivB))

    outFill(ret, out)
}

@OptIn(ExperimentalForeignApi::class)
@CName("fidok_crypto_aes_256_cbc_decrypt")
fun aes_256_decrypt(
    bytes: COpaquePointer,
    len: Int,
    key: COpaquePointer,
    iv: COpaquePointer,
    out: COpaquePointer,
) {
    val inB = inAsByteArray(bytes, len)
    val keyB = inAsByteArray(key, 32)
    val ivB = inAsByteArray(iv, 16)

    val ret = crypto.aes256CBCDecrypt(inB, AESKey(keyB, iv = ivB))

    outFill(ret, out)
}

@OptIn(ExperimentalForeignApi::class)
@CName("fidok_crypto_aes_128_cbc_decrypt")
fun aes_128_decrypt(
    bytes: COpaquePointer,
    len: Int,
    key: COpaquePointer,
    iv: COpaquePointer,
    out: COpaquePointer,
) {
    val inB = inAsByteArray(bytes, len)
    val keyB = inAsByteArray(key, 16)
    val ivB = inAsByteArray(iv, 16)

    val ret = crypto.aes128CBCDecrypt(inB, AESKey(keyB, iv = ivB))

    outFill(ret, out)
}

@OptIn(ExperimentalForeignApi::class)
@CName("fidok_crypto_hmac_sha256")
fun hmac_sha256(
    data: COpaquePointer,
    len: Int,
    key: COpaquePointer,
    out: COpaquePointer,
) {
    val inB = inAsByteArray(data, len)
    val keyB = inAsByteArray(key, 32)

    val ret = crypto.hmacSHA256(inB, AESKey(keyB, iv = null))

    outFill(ret.hash, out)
}

@OptIn(ExperimentalForeignApi::class)
@CName("fidok_crypto_es256_signature_validate")
fun es256_signature_validate(
    signedBytes: COpaquePointer,
    signedBytesLen: Int,
    keyX: COpaquePointer,
    keyY: COpaquePointer,
    signature: COpaquePointer,
    signatureLen: Int,
): Boolean {
    val signedBytesB = inAsByteArray(signedBytes, signedBytesLen)
    val keyXB = inAsByteArray(keyX, 32)
    val keyYB = inAsByteArray(keyY, 32)
    val sigB = inAsByteArray(signature, signatureLen)

    return crypto.es256SignatureValidate(signedBytesB, P256Point(keyXB, keyYB), sigB)
}

@OptIn(ExperimentalForeignApi::class)
@CName("fidok_crypto_parse_es256_x509")
fun es256_x509_info(
    cert: COpaquePointer,
    certLen: Int,
    keyX: COpaquePointer,
    keyY: COpaquePointer,
) {
    val certB = inAsByteArray(cert, certLen)

    val ret = crypto.parseES256X509(certB)

    outFill(ret.publicX, keyX)
    outFill(ret.publicY, keyY)
}
