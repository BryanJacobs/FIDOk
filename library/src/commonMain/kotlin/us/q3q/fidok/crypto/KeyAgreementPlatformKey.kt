package us.q3q.fidok.crypto

import us.q3q.fidok.ctap.commands.COSEKey

/**
 * The negotiated agreement between a FIDO Platform and an Authenticator
 *
 * Established by a cryptographic key agreement in which the Platform and Authenticator
 * exchange ECDH keys.
 *
 * @property publicX The Platform-side public ECDSA P-256 key's X point
 * @property publicY The Platform-side public ECDSA P-256 key's Y point
 * @property pinUvProtocol1Key The computed secret for CTAP PIN Protocol 1
 * @property pinUvProtocol2HMACKey The computed secret for CTAP PIN Protocol 2 verification operations
 * @property pinUvProtocol2AESKey The computed secret for CTAP PIN Protocol 2 encryption/decryption operations
 */
data class KeyAgreementPlatformKey(
    val publicX: ByteArray,
    val publicY: ByteArray,
    val pinUvProtocol1Key: ByteArray,
    val pinUvProtocol2HMACKey: ByteArray,
    val pinUvProtocol2AESKey: ByteArray,
) {
    init {
        require(publicX.size == 32)
        require(publicY.size == 32)
        require(pinUvProtocol1Key.size == 32)
        require(pinUvProtocol2HMACKey.size == 32)
        require(pinUvProtocol2AESKey.size == 32)
    }

    /**
     * Returns the Platform public key as a [COSEKey].
     *
     * @return [COSEKey] representation of public key state
     */
    fun getCOSE(): COSEKey {
        return COSEKey(
            kty = 2,
            alg = -25,
            crv = 1,
            x = publicX,
            y = publicY,
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as KeyAgreementPlatformKey

        if (!publicX.contentEquals(other.publicX)) return false
        if (!publicY.contentEquals(other.publicY)) return false
        if (!pinUvProtocol1Key.contentEquals(other.pinUvProtocol1Key)) return false
        if (!pinUvProtocol2HMACKey.contentEquals(other.pinUvProtocol2HMACKey)) return false
        if (!pinUvProtocol2AESKey.contentEquals(other.pinUvProtocol2AESKey)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = publicX.contentHashCode()
        result = 31 * result + publicY.contentHashCode()
        result = 31 * result + pinUvProtocol1Key.contentHashCode()
        result = 31 * result + pinUvProtocol2HMACKey.contentHashCode()
        result = 31 * result + pinUvProtocol2AESKey.contentHashCode()
        return result
    }
}
