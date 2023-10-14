package us.q3q.fidok.webauthn

data class PublicKeyCredential(
    val id: ByteArray,
    val type: String = "",
    val rawId: ByteArray,
    val response: AuthenticatorResponse,
    val authenticatorAttachment: String? = null,
    val clientExtensionResults: AuthenticationExtensionsClientOutputs = mapOf(),
)
