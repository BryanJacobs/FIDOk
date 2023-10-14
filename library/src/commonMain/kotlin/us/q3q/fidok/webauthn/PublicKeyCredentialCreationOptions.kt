package us.q3q.fidok.webauthn

import us.q3q.fidok.ctap.commands.COSEAlgorithmIdentifier
import us.q3q.fidok.ctap.commands.PublicKeyCredentialDescriptor
import us.q3q.fidok.ctap.commands.PublicKeyCredentialParameters
import us.q3q.fidok.ctap.commands.PublicKeyCredentialRpEntity
import us.q3q.fidok.ctap.commands.PublicKeyCredentialType
import us.q3q.fidok.ctap.commands.PublicKeyCredentialUserEntity

data class PublicKeyCredentialCreationOptions(
    val rp: PublicKeyCredentialRpEntity,
    val user: PublicKeyCredentialUserEntity,
    val challenge: ByteArray,
    val pubKeyCredParams: List<PublicKeyCredentialParameters> = listOf(
        PublicKeyCredentialParameters(
            alg = COSEAlgorithmIdentifier.ES256.value,
            type = PublicKeyCredentialType.PUBLIC_KEY.value,
        ),
        PublicKeyCredentialParameters(
            alg = COSEAlgorithmIdentifier.RS256.value,
            type = PublicKeyCredentialType.PUBLIC_KEY.value,
        ),
    ),
    val timeout: ULong? = null,
    val excludeCredentials: List<PublicKeyCredentialDescriptor> = listOf(),
    val authenticatorSelectionCriteria: AuthenticatorSelectionCriteria? = null,
    val hints: List<String> = listOf(),
    val attestation: String = AttestationConveyancePreference.NONE.value,
    val attestationFormats: List<String> = listOf(),
    val extensions: AuthenticationExtensionsClientInputs? = null,
) {
    init {
        require(rp.name != null)
        require(user.name != null)
        require(user.displayName != null)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as PublicKeyCredentialCreationOptions

        if (rp != other.rp) return false
        if (user != other.user) return false
        if (!challenge.contentEquals(other.challenge)) return false
        if (pubKeyCredParams != other.pubKeyCredParams) return false
        if (timeout != other.timeout) return false
        if (excludeCredentials != other.excludeCredentials) return false
        if (authenticatorSelectionCriteria != other.authenticatorSelectionCriteria) return false
        if (hints != other.hints) return false
        if (attestation != other.attestation) return false
        if (attestationFormats != other.attestationFormats) return false
        if (extensions != other.extensions) return false

        return true
    }

    override fun hashCode(): Int {
        var result = rp.hashCode()
        result = 31 * result + user.hashCode()
        result = 31 * result + challenge.contentHashCode()
        result = 31 * result + pubKeyCredParams.hashCode()
        result = 31 * result + (timeout?.hashCode() ?: 0)
        result = 31 * result + excludeCredentials.hashCode()
        result = 31 * result + (authenticatorSelectionCriteria?.hashCode() ?: 0)
        result = 31 * result + hints.hashCode()
        result = 31 * result + attestation.hashCode()
        result = 31 * result + attestationFormats.hashCode()
        result = 31 * result + (extensions?.hashCode() ?: 0)
        return result
    }
}
