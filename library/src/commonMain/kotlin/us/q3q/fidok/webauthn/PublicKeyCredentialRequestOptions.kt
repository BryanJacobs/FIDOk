package us.q3q.fidok.webauthn

import us.q3q.fidok.ctap.commands.PublicKeyCredentialDescriptor

data class PublicKeyCredentialRequestOptions(
    val challenge: ByteArray,
    val timeout: ULong? = null,
    val rpId: String? = null,
    val allowCredentials: List<PublicKeyCredentialDescriptor> = listOf(),
    val userVerification: String = UserVerificationRequirement.PREFERRED.value,
    val hints: List<String> = listOf(),
    val attestation: String = AttestationConveyancePreference.NONE.value,
    val attestationFormats: List<String> = listOf(),
    val extensions: AuthenticationExtensionsClientInputs? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as PublicKeyCredentialRequestOptions

        if (!challenge.contentEquals(other.challenge)) return false
        if (timeout != other.timeout) return false
        if (rpId != other.rpId) return false
        if (allowCredentials != other.allowCredentials) return false
        if (userVerification != other.userVerification) return false
        if (hints != other.hints) return false
        if (attestation != other.attestation) return false
        if (attestationFormats != other.attestationFormats) return false
        if (extensions != other.extensions) return false

        return true
    }

    override fun hashCode(): Int {
        var result = challenge.contentHashCode()
        result = 31 * result + (timeout?.hashCode() ?: 0)
        result = 31 * result + (rpId?.hashCode() ?: 0)
        result = 31 * result + allowCredentials.hashCode()
        result = 31 * result + userVerification.hashCode()
        result = 31 * result + hints.hashCode()
        result = 31 * result + attestation.hashCode()
        result = 31 * result + attestationFormats.hashCode()
        result = 31 * result + (extensions?.hashCode() ?: 0)
        return result
    }
}
