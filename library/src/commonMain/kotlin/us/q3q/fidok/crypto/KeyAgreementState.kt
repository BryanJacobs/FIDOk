package us.q3q.fidok.crypto

/**
 * Intermediate state for an in-progress ECDH key agreement
 *
 * @property localPublicX The Platform public key's X-point
 * @property localPublicY The Platform public key's Y-point
 * @property opaqueState An optional, opaque data storage object that may be used
 *                       by the `CryptoProvider` to store the Platform private key
 *                       and/or any other state tracking
 */
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
