package us.q3q.fidok.ctap.commands

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

@Serializable
class AuthenticatorConfigCommand private constructor(
    val subCommand: UByte,
    val subCommandParams: Map<UByte, @Polymorphic ParameterValue>? = null,
    val pinUvAuthProtocol: UByte? = null,
    var pinUvAuthParam: ByteArray? = null,
) : CtapCommand() {
    override val cmdByte: Byte = 0x0D

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
            ByteArray(32) { 0xFF.toByte() }.toList() + listOf(
                0x0D.toByte(), subCommand.toByte(),
            ) + parameterBytes.toList()
            ).toByteArray()
    }

    companion object {
        fun enableEnterpriseAttestation(pinUvAuthProtocol: UByte? = null, pinUvAuthParam: ByteArray? = null): AuthenticatorConfigCommand {
            return AuthenticatorConfigCommand(
                subCommand = 0x01u,
                pinUvAuthProtocol = pinUvAuthProtocol,
                pinUvAuthParam = pinUvAuthParam,
            )
        }

        fun toggleAlwaysUv(pinUvAuthProtocol: UByte? = null, pinUvAuthParam: ByteArray? = null): AuthenticatorConfigCommand {
            return AuthenticatorConfigCommand(
                subCommand = 0x02u,
                pinUvAuthProtocol = pinUvAuthProtocol,
                pinUvAuthParam = pinUvAuthParam,
            )
        }

        fun setMinPINLength(
            pinUvAuthProtocol: UByte?,
            pinUvAuthParam: ByteArray? = null,
            newMinPINLength: UInt? = null,
            minPinLengthRPIDs: Array<String>? = null,
            forceChangePin: Boolean? = null,
        ): AuthenticatorConfigCommand {
            return AuthenticatorConfigCommand(
                subCommand = 0x03u,
                pinUvAuthProtocol = pinUvAuthProtocol,
                pinUvAuthParam = pinUvAuthParam,
                subCommandParams = HashMap<UByte, ParameterValue>().apply {
                    if (newMinPINLength != null) {
                        this[0x01u] = UIntParameter(newMinPINLength)
                    }
                    if (minPinLengthRPIDs != null) {
                        this[0x02u] = StringArrayParameter(minPinLengthRPIDs)
                    }
                    if (forceChangePin != null) {
                        this[0x03u] = BooleanParameter(forceChangePin)
                    }
                },
            )
        }
    }

    internal fun generateParams(): Map<UByte, ParameterValue> {
        return HashMap<UByte, ParameterValue>().apply {
            this[0x01u] = UByteParameter(subCommand)
            if (subCommandParams != null) {
                this[0x02u] = MapParameter(subCommandParams)
            }
            if (pinUvAuthProtocol != null) {
                this[0x03u] = UByteParameter(pinUvAuthProtocol)
            }
            val puv = pinUvAuthParam
            if (puv != null) {
                this[0x04u] = ByteArrayParameter(puv)
            }
        }
    }

    override var params = generateParams()
}
