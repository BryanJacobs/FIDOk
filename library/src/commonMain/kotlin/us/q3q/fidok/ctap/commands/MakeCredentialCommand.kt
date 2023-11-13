package us.q3q.fidok.ctap.commands

import kotlinx.serialization.Serializable

/**
 * Options to a CTAP [MakeCredentialCommand].
 *
 * @property value The canonical string representation of the option
 */
enum class CredentialCreationOption(val value: String) {
    /**
     * Create a Discoverable Credential.
     *
     * Because Discoverable Credentials were originally called "Resident Keys", this is the `RK` option.
     */
    RK("rk"),

    /**
     * Require user presence prior to creating the credential
     */
    UP("up"),

    /**
     * Verify the user through an on-board method (heart rate cadence, blood test, karaoke recording, whatever)
     */
    UV("uv"),
}

@Serializable
data class CreationOptionParameter(override val v: Map<String, Boolean>) : ParameterValue()

/**
 * A command to create a [CTAP Credential][PublicKeyCredentialDescriptor].
 *
 * This will be sent to an Authenticator to get back something usable for a [getting an Assertion][GetAssertionCommand].
 *
 * @property clientDataHash A SHA-256 hash of the webauthn "client data" - an arbitrary challenge parameter. This will
 *                          become part of the signed response from the Authenticator
 * @property rp Information on the Relying Party for which the Credential is being created
 * @property user The user to which the Credential is attached. Note that an Authenticator will try not to store
 *                two Discoverable Credentials for the same user ID and the same Relying Party
 * @property pubKeyCredParams The acceptable cryptographic algorithms to use in generating the Credential
 * @property excludeList A list of previously-generated credentials. If the Authenticator recognizes one, it will return
 * [CREDENTIAL_EXCLUDED][us.q3q.fidok.ctap.CredentialExcludedError] instead of creating the Credential. This is used
 * to avoid accidentally creating an extra Credential on the same Authenticator, which could otherwise happen for
 * non-Discoverable Credentials.
 * @property options Any options to pass to the Authenticator along with the creation command
 * @property pinUvAuthParam An authentication of the command parameters using a PIN/UV token, as per the CTAP standard
 * @property pinUvAuthProtocol The PIN/UV protocol version in use
 * @property enterpriseAttestation Enterprise Attestation type to request, if any. Enterprise Attestations uniquely
 * identify a particular Authenticator in a proprietary fashion. Generally this should be
 * [a valid Enterprise Attestation level][us.q3q.fidok.ctap.EnterpriseAttestationLevel]
 */
@Serializable
class MakeCredentialCommand(
    private val clientDataHash: ByteArray,
    private val rp: PublicKeyCredentialRpEntity,
    private val user: PublicKeyCredentialUserEntity,
    private val pubKeyCredParams: List<PublicKeyCredentialParameters>,
    private val excludeList: List<PublicKeyCredentialDescriptor>? = null,
    private val extensions: Map<ExtensionName, ExtensionParameters>? = null,
    private val options: Map<CredentialCreationOption, Boolean>? = null,
    private val pinUvAuthParam: ByteArray? = null,
    private val pinUvAuthProtocol: UByte? = null,
    private val enterpriseAttestation: UInt? = null,
) : CtapCommand() {
    override val cmdByte: Byte = 0x01
    override val params =
        HashMap<UByte, ParameterValue>().apply {
            this[0x01u] = ByteArrayParameter(clientDataHash)
            this[0x02u] = PublicKeyCredentialRpEntityParameter(rp)
            this[0x03u] = PublicKeyCredentialUserEntityParameter(user)
            this[0x04u] = PublicKeyCredentialsParametersParameter(pubKeyCredParams)
            if (!excludeList.isNullOrEmpty()) {
                this[0x05u] = PublicKeyCredentialListParameter(excludeList)
            }
            if (!extensions.isNullOrEmpty()) {
                this[0x06u] = ExtensionParameterValues(extensions)
            }
            if (!options.isNullOrEmpty()) {
                val m = hashMapOf<String, Boolean>()
                options.keys.forEach {
                    m[it.value] = options[it]!!
                }
                this[0x07u] = CreationOptionParameter(m)
            }
            if (pinUvAuthParam != null) {
                this[0x08u] = ByteArrayParameter(pinUvAuthParam)
            }
            if (pinUvAuthProtocol != null) {
                this[0x09u] = UByteParameter(pinUvAuthProtocol)
            }
            if (enterpriseAttestation != null) {
                this[0x0Au] = UIntParameter(enterpriseAttestation)
            }
        }

    init {
        require(clientDataHash.size == 32)
        require(pubKeyCredParams.isNotEmpty())
        require(pinUvAuthProtocol == null || pinUvAuthProtocol == 1u.toUByte() || pinUvAuthProtocol == 2u.toUByte())
        require(pinUvAuthParam == null || pinUvAuthParam.size == 16 || pinUvAuthParam.size == 32)
        require(enterpriseAttestation == null || enterpriseAttestation == 1u || enterpriseAttestation == 2u)
    }
}
