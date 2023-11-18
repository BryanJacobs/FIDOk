package us.q3q.fidok.webauthn

data class PublicKeyCredential(
    val id: String,
    val type: String = "",
    val rawId: ByteArray,
    val response: AuthenticatorResponse,
    val authenticatorAttachment: String? = null,
    val clientExtensionResults: AuthenticationExtensionsClientOutputs = mapOf(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as PublicKeyCredential

        if (id != other.id) return false
        if (type != other.type) return false
        if (!rawId.contentEquals(other.rawId)) return false
        if (response != other.response) return false
        if (authenticatorAttachment != other.authenticatorAttachment) return false
        if (clientExtensionResults != other.clientExtensionResults) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + rawId.contentHashCode()
        result = 31 * result + response.hashCode()
        result = 31 * result + (authenticatorAttachment?.hashCode() ?: 0)
        result = 31 * result + clientExtensionResults.hashCode()
        return result
    }
}
