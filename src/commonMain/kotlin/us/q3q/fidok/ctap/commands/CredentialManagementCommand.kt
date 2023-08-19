package us.q3q.fidok.ctap.commands

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

@Serializable
class CredentialManagementCommand private constructor(
    private val subCommand: UByte,
    private val pinUvAuthProtocol: UByte? = null,
    internal var pinUvAuthParam: ByteArray? = null,
    private val subCommandParams: Map<UByte, @Polymorphic ParameterValue>? = null,
    private val ctap21Implementation: Boolean = true,
) : CtapCommand() {
    override val cmdByte: Byte = if (ctap21Implementation) 0x0A else 0x41
    override var params = generateParams()

    internal fun generateParams(): Map<UByte, ParameterValue> {
        return HashMap<UByte, ParameterValue>().apply {
            this[0x01u] = UByteParameter(subCommand)
            if (subCommandParams != null) {
                this[0x02u] = MapParameter(subCommandParams)
            }
            if (pinUvAuthProtocol != null) {
                this[0x03u] = UByteParameter(pinUvAuthProtocol)
            }
            val uv = pinUvAuthParam
            if (uv != null) {
                this[0x04u] = ByteArrayParameter(uv)
            }
        }
    }

    fun getUvParamData(): ByteArray {
        val parameterBytes = if (subCommandParams != null) {
            val encoder = CTAPCBOREncoder()
            encoder.encodeSerializableValue(
                MapSerializer(UByte.serializer(), ParameterValue.serializer()),
                subCommandParams,
            )
            encoder.getBytes()
        } else {
            byteArrayOf()
        }

        return (
            listOf(
                subCommand.toByte(),
            ) + parameterBytes.toList()
            ).toByteArray()
    }

    init {
        require(
            (pinUvAuthParam == null) ||
                (pinUvAuthProtocol == 1u.toUByte() && pinUvAuthParam?.size == 16) ||
                (pinUvAuthProtocol == 2u.toUByte() && pinUvAuthParam?.size == 32),
        )
    }

    companion object {
        fun getCredsMetadata(
            pinUvAuthProtocol: UByte,
            pinUvAuthParam: ByteArray,
            ctap21Implementation: Boolean = true,
        ): CredentialManagementCommand {
            return CredentialManagementCommand(
                subCommand = 0x01u,
                pinUvAuthProtocol = pinUvAuthProtocol,
                pinUvAuthParam = pinUvAuthParam,
                ctap21Implementation = ctap21Implementation,
            )
        }

        fun enumerateRPsBegin(
            pinUvAuthProtocol: UByte,
            pinUvAuthParam: ByteArray,
            ctap21Implementation: Boolean = true,
        ): CredentialManagementCommand {
            return CredentialManagementCommand(
                subCommand = 0x02u,
                pinUvAuthProtocol = pinUvAuthProtocol,
                pinUvAuthParam = pinUvAuthParam,
                ctap21Implementation = ctap21Implementation,
            )
        }

        fun enumerateRPsGetNextRP(ctap21Implementation: Boolean = true): CredentialManagementCommand {
            return CredentialManagementCommand(
                subCommand = 0x03u,
                ctap21Implementation = ctap21Implementation,
            )
        }

        fun enumerateCredentialsBegin(
            pinUvAuthProtocol: UByte,
            pinUvAuthParam: ByteArray? = null,
            rpIDHash: ByteArray,
            ctap21Implementation: Boolean = true,
        ): CredentialManagementCommand {
            return CredentialManagementCommand(
                subCommand = 0x04u,
                pinUvAuthProtocol = pinUvAuthProtocol,
                pinUvAuthParam = pinUvAuthParam,
                ctap21Implementation = ctap21Implementation,
                subCommandParams = mapOf(
                    0x01u.toUByte() to ByteArrayParameter(rpIDHash),
                ),
            )
        }

        fun enumerateCredentialsGetNextCredential(ctap21Implementation: Boolean = true): CredentialManagementCommand {
            return CredentialManagementCommand(
                subCommand = 0x05u,
                ctap21Implementation = ctap21Implementation,
            )
        }

        fun deleteCredential(
            pinUvAuthProtocol: UByte,
            pinUvAuthParam: ByteArray? = null,
            credentialId: PublicKeyCredentialDescriptor,
            ctap21Implementation: Boolean = true,
        ): CredentialManagementCommand {
            return CredentialManagementCommand(
                subCommand = 0x06u,
                pinUvAuthProtocol = pinUvAuthProtocol,
                pinUvAuthParam = pinUvAuthParam,
                subCommandParams = mapOf(
                    0x02u.toUByte() to PublicKeyCredentialDescriptorParameter(credentialId),
                ),
                ctap21Implementation = ctap21Implementation,
            )
        }

        fun updateUserInformation(
            pinUvAuthProtocol: UByte,
            pinUvAuthParam: ByteArray? = null,
            credentialId: PublicKeyCredentialDescriptor,
            user: PublicKeyCredentialUserEntity,
            ctap21Implementation: Boolean = true,
        ): CredentialManagementCommand {
            return CredentialManagementCommand(
                subCommand = 0x07u,
                pinUvAuthProtocol = pinUvAuthProtocol,
                pinUvAuthParam = pinUvAuthParam,
                subCommandParams = mapOf(
                    0x02u.toUByte() to PublicKeyCredentialDescriptorParameter(credentialId),
                    0x03u.toUByte() to PublicKeyCredentialUserEntityParameter(user),
                ),
                ctap21Implementation = ctap21Implementation,
            )
        }
    }
}
