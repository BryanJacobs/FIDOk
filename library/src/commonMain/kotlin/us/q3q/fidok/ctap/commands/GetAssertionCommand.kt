package us.q3q.fidok.ctap.commands

import kotlinx.serialization.Serializable

/**
 * CTAP options valid for a [GetAssertionCommand]
 *
 * @property value The canonical string representing the option
 */
enum class GetAssertionOption(val value: String) {
    /**
     * User presence check requested
     */
    UP("up"),

    /**
     * Onboard user verification (fingerprint, voice, iris scan, whatever) requested
     */
    UV("uv"),
}

@Serializable
data class AssertionOptionParameter(override val v: Map<String, Boolean>) : ParameterValue()

/**
 * Represents a request to an Authenticator to create an Assertion - the fundamental purpose of the FIDO standards.
 *
 * @property rpId The identifier of a Relying Party, as a string
 * @property clientDataHash A SHA-256 hash of the webauthn "client data" - an arbitrary challenge parameter. This will
 *                          become part of the signed response from the Authenticator
 * @property allowList A list of credentials previously obtained from [MakeCredentialCommand]s. If this is absent,
 *                     results from Discoverable Credentials may still be returned
 * @property extensions Extension data to pass to the Authenticator, as a map from the extension's name to its
 *                      parameters
 * @property options Any options to pass to the Authenticator
 * @property pinUvAuthParam The result of appropriately authenticating this request using a PIN/UV auth token, as
 *                          per the CTAP standards
 * @property pinUvAuthProtocol The version of the PIN/UV auth protocol in use
 */
@Serializable
class GetAssertionCommand(
    private val rpId: String,
    private val clientDataHash: ByteArray,
    private val allowList: List<PublicKeyCredentialDescriptor>? = null,
    private val extensions: Map<ExtensionName, ExtensionParameters>? = null,
    private val options: Map<GetAssertionOption, Boolean>? = null,
    private val pinUvAuthParam: ByteArray? = null,
    private val pinUvAuthProtocol: UByte? = null,
) : CtapCommand() {
    override val cmdByte: Byte = 0x02
    override val params =
        HashMap<UByte, ParameterValue>().apply {
            this[0x01u] = StringParameter(rpId)
            this[0x02u] = ByteArrayParameter(clientDataHash)
            if (allowList != null) {
                this[0x03u] = PublicKeyCredentialListParameter(allowList)
            }
            if (extensions != null) {
                this[0x04u] = ExtensionParameterValues(extensions)
            }
            if (options != null) {
                val m = hashMapOf<String, Boolean>()
                options.keys.forEach {
                    m[it.value] = options[it]!!
                }
                this[0x05u] = CreationOptionParameter(m)
            }
            if (pinUvAuthParam != null) {
                this[0x06u] = ByteArrayParameter(pinUvAuthParam)
            }
            if (pinUvAuthProtocol != null) {
                this[0x07u] = UByteParameter(pinUvAuthProtocol)
            }
        }

    init {
        require(clientDataHash.size == 32)
        require(pinUvAuthProtocol == null || pinUvAuthProtocol == 1u.toUByte() || pinUvAuthProtocol == 2u.toUByte())
        require(pinUvAuthParam == null || pinUvAuthParam.size == 32 || pinUvAuthParam.size == 48)
    }
}
