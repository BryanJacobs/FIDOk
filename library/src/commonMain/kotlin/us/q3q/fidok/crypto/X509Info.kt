package us.q3q.fidok.crypto

/**
 * Captures information about an X.509 certificate with an ECDSA key on the NIST P-256 curve
 *
 * @property publicX X-coordinate of the certificate's ECDSA public key
 * @property publicY Y-coordinate of the certificate's ECDSA public key
 * @property aaguid An optional bound AAGUID. If set, this certificate is only valid for
 *                  an authenticator having that AAGUID
 */
class X509Info(val publicX: ByteArray, val publicY: ByteArray, val aaguid: ByteArray?) {
    init {
        require(publicX.size == 32)
        require(publicY.size == 32)
        require(aaguid == null || aaguid.size == 16)
    }
}
