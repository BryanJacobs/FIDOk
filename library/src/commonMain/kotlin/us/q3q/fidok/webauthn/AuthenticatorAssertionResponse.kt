package us.q3q.fidok.webauthn

data class AuthenticatorAssertionResponse(
    private val _clientDataJSON: ByteArray,
    val authenticatorData: ByteArray,
    val signature: ByteArray,
    val userHandle: ByteArray? = null,
    val attestationObject: ByteArray? = null,
) : AuthenticatorResponse(_clientDataJSON) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as AuthenticatorAssertionResponse

        if (!_clientDataJSON.contentEquals(other._clientDataJSON)) return false
        if (!authenticatorData.contentEquals(other.authenticatorData)) return false
        if (!signature.contentEquals(other.signature)) return false
        if (userHandle != null) {
            if (other.userHandle == null) return false
            if (!userHandle.contentEquals(other.userHandle)) return false
        } else if (other.userHandle != null) {
            return false
        }
        if (attestationObject != null) {
            if (other.attestationObject == null) return false
            if (!attestationObject.contentEquals(other.attestationObject)) return false
        } else if (other.attestationObject != null) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = _clientDataJSON.contentHashCode()
        result = 31 * result + authenticatorData.contentHashCode()
        result = 31 * result + signature.contentHashCode()
        result = 31 * result + (userHandle?.contentHashCode() ?: 0)
        result = 31 * result + (attestationObject?.contentHashCode() ?: 0)
        return result
    }
}
