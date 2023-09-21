package us.q3q.fidok.cable

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import us.q3q.fidok.ctap.commands.BooleanParameter
import us.q3q.fidok.ctap.commands.ByteArrayParameter
import us.q3q.fidok.ctap.commands.ParameterValue
import us.q3q.fidok.ctap.commands.StringParameter
import us.q3q.fidok.ctap.commands.UByteParameter
import us.q3q.fidok.ctap.commands.ULongParameter

enum class OperationHint(val v: String) {
    MAKE_CREDENTIAL("mc"),
    GET_ASSERTION("ga"),
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable(with = CaBLECodeSerializer::class)
data class CaBLECode(
    @ByteString val publicKey: ByteArray,
    @ByteString val secret: ByteArray,
    val knownTunnelServerDomains: UByte,
    val currentEpochSeconds: ULong? = null,
    val canPerformStateAssist: Boolean? = null,
    val operationHint: OperationHint = OperationHint.GET_ASSERTION,
) {
    init {
        require(publicKey.size == 33)
        require(secret.size == 16)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as CaBLECode

        if (!publicKey.contentEquals(other.publicKey)) return false
        if (!secret.contentEquals(other.secret)) return false
        if (knownTunnelServerDomains != other.knownTunnelServerDomains) return false
        if (currentEpochSeconds != other.currentEpochSeconds) return false
        if (canPerformStateAssist != other.canPerformStateAssist) return false
        if (operationHint != other.operationHint) return false

        return true
    }

    override fun hashCode(): Int {
        var result = publicKey.contentHashCode()
        result = 31 * result + secret.contentHashCode()
        result = 31 * result + knownTunnelServerDomains.hashCode()
        result = 31 * result + (currentEpochSeconds?.hashCode() ?: 0)
        result = 31 * result + (canPerformStateAssist?.hashCode() ?: 0)
        result = 31 * result + operationHint.hashCode()
        return result
    }
}

class CaBLECodeSerializer : KSerializer<CaBLECode> {
    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("CaBLECode") {
            element("publicKey", ByteArraySerializer().descriptor)
            element("secret", ByteArraySerializer().descriptor)
            element("knownTunnelServerDomains", UByte.serializer().descriptor)
            element("currentEpochSeconds", Int.serializer().descriptor, isOptional = true)
            element("canPerformStateAssist", Boolean.serializer().descriptor, isOptional = true)
            element("operationHint", String.serializer().descriptor)
        }

    override fun deserialize(decoder: Decoder): CaBLECode {
        val composite = decoder.beginStructure(descriptor)
        val numParams = composite.decodeCollectionSize(descriptor)

        var publicKey: ByteArray? = null
        var secret: ByteArray? = null
        var knownTunnelServerDomains: UByte? = null
        var currentEpochSeconds: ULong? = null
        var canPerformStateAssist: Boolean? = null
        var operationHint: OperationHint? = null

        for (i in 1..numParams) {
            val idx = composite.decodeElementIndex(descriptor)
            when (idx) {
                0x00 -> {
                    publicKey = composite.decodeSerializableElement(descriptor, idx, ByteArraySerializer())
                }
                0x01 -> {
                    secret = composite.decodeSerializableElement(descriptor, idx, ByteArraySerializer())
                }
                0x02 -> {
                    knownTunnelServerDomains = composite.decodeIntElement(descriptor, idx).toUByte()
                }
                0x03 -> {
                    currentEpochSeconds = composite.decodeLongElement(descriptor, idx).toULong()
                }
                0x04 -> {
                    canPerformStateAssist = composite.decodeBooleanElement(descriptor, idx)
                }
                0x05 -> {
                    val hintStr = composite.decodeStringElement(descriptor, idx)
                    operationHint = OperationHint.entries.find { it.v == hintStr } // Ignore unknown hints
                }
                else -> {
                    // Ignore - unknown property
                }
            }
        }

        if (publicKey == null || secret == null || knownTunnelServerDomains == null) {
            throw SerializationException("CaBLECode is missing required properties")
        }
        if (operationHint == null) {
            operationHint = OperationHint.GET_ASSERTION
        }

        composite.endStructure(descriptor)

        return CaBLECode(
            publicKey = publicKey,
            secret = secret,
            knownTunnelServerDomains = knownTunnelServerDomains,
            currentEpochSeconds = currentEpochSeconds,
            canPerformStateAssist = canPerformStateAssist,
            operationHint = operationHint,
        )
    }

    override fun serialize(encoder: Encoder, value: CaBLECode) {
        val params = hashMapOf(
            0x00.toUByte() to ByteArrayParameter(value.publicKey),
            0x01.toUByte() to ByteArrayParameter(value.secret),
            0x02.toUByte() to UByteParameter(value.knownTunnelServerDomains),
            0x05.toUByte() to StringParameter(value.operationHint.v),
        )
        if (value.currentEpochSeconds != null) {
            params[0x03.toUByte()] = ULongParameter(value.currentEpochSeconds)
        }
        if (value.canPerformStateAssist != null) {
            params[0x04.toUByte()] = BooleanParameter(value.canPerformStateAssist)
        }
        encoder.encodeSerializableValue(
            MapSerializer(UByte.serializer(), ParameterValue.serializer()),
            params,
        )
    }
}
