package us.q3q.fidok.crypto

/**
 * The result of performing an ECDH key agreement operation using SHA-256
 *
 * @property bytes The computed key agreement result
 */
class KeyAgreementResult(val bytes: ByteArray) {
    init {
        require(bytes.size == 32)
    }
}
