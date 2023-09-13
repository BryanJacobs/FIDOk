package us.q3q.fidok.ctap.commands

import kotlinx.serialization.Serializable

enum class GetAssertionOption(val value: String) {
    UP("up"),
    UV("uv"),
}

@Serializable
data class AssertionOptionParameter(override val v: Map<String, Boolean>) : ParameterValue()

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
    override val params = HashMap<UByte, ParameterValue>().apply {
        this[0x01u] = StringParameter(rpId)
        this[0x02u] = ByteArrayParameter(clientDataHash)
        if (allowList != null) {
            this[0x03u] = PublicKeyCredentialListParameter(allowList)
        }
        if (extensions != null) {
            this[0x04u] = ExtensionParameterValue(extensions)
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
