package us.q3q.fidok.webauthn

class AuthenticatorAttestationResponse(
    private val _clientDataJSON: ByteArray,
    val attestationObject: ByteArray,
    val publicKeyAlgorithm: Long,
    val transports: List<String> = listOf(),
) : AuthenticatorResponse(_clientDataJSON) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as AuthenticatorAttestationResponse

        if (!_clientDataJSON.contentEquals(other._clientDataJSON)) return false
        if (!attestationObject.contentEquals(other.attestationObject)) return false
        if (transports != other.transports) return false

        return true
    }

    override fun hashCode(): Int {
        var result = _clientDataJSON.contentHashCode()
        result = 31 * result + attestationObject.contentHashCode()
        result = 31 * result + transports.hashCode()
        return result
    }
}
