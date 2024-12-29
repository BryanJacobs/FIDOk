package us.q3q.fidok.webauthn

class AuthenticatorAttestationResponse(
    private val _clientDataJSON: ByteArray,
    val attestationObject: ByteArray?,
    val publicKeyAlgorithm: Long,
    val publicKey: ByteArray?,
    val authenticatorData: ByteArray?,
    val signature: ByteArray?,
    val transports: List<String> = listOf(),
) : AuthenticatorResponse(_clientDataJSON) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as AuthenticatorAttestationResponse

        if (!_clientDataJSON.contentEquals(other._clientDataJSON)) return false
        if (!attestationObject.contentEquals(other.attestationObject)) return false
        if (!publicKey.contentEquals(other.publicKey)) return false
        if (!authenticatorData.contentEquals(other.authenticatorData)) return false
        if (!signature.contentEquals(other.signature)) return false
        if (transports != other.transports) return false

        return true
    }

    override fun hashCode(): Int {
        var result = _clientDataJSON.contentHashCode()
        result = 31 * result + attestationObject.contentHashCode()
        result = 31 * result + transports.hashCode()
        result = 31 * result + publicKey.contentHashCode()
        result = 31 * result + publicKeyAlgorithm.hashCode()
        result = 31 * result + authenticatorData.contentHashCode()
        result = 31 * result + signature.contentHashCode()
        return result
    }
}
