package us.q3q.fidok.webauthn

/**
 * Describes the means by which Authenticators are chosen - and their requirements.
 *
 * This object is directly described in the Webauthn standard.
 *
 * @property authenticatorAttachment Ideally a member of [AuthenticatorAttachment] - how the Authenticator must connect
 *                                   to the Platform
 * @property residentKey Ideally a member of [ResidentKeyRequirement] - whether the Credential will be discoverable
 * @property requireResidentKey Used if [residentKey] is not set - true for discoverable, false otherwise
 * @property userVerification Ideally a member of [UserVerificationRequirement]: describes whether users need to be
 *                            verified (through entering a PIN or through onboard UV)
 */
data class AuthenticatorSelectionCriteria(
    val authenticatorAttachment: String? = null,
    val residentKey: String? = null,
    val requireResidentKey: Boolean = false,
    val userVerification: String = UserVerificationRequirement.PREFERRED.value,
)
