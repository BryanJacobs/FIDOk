package us.q3q.fidok.ctap.commands

import kotlinx.serialization.Serializable

enum class CredentialCreationOption(val value: String) {
    RK("rk"),
    UP("up"),
    UV("uv"),
}

@Serializable
data class CreationOptionParameter(override val v: Map<String, Boolean>) : ParameterValue()

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
    override val params = HashMap<UByte, ParameterValue>().apply {
        this[0x01u] = ByteArrayParameter(clientDataHash)
        this[0x02u] = PublicKeyCredentialRpEntityParameter(rp)
        this[0x03u] = PublicKeyCredentialUserEntityParameter(user)
        this[0x04u] = PublicKeyCredentialsParametersParameter(pubKeyCredParams)
        if (excludeList != null) {
            this[0x05u] = PublicKeyCredentialListParameter(excludeList)
        }
        if (extensions != null) {
            this[0x06u] = ExtensionParameterValue(extensions)
        }
        if (options?.isNotEmpty() == true) {
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
