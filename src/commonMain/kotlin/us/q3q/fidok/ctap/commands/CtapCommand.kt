package us.q3q.fidok.ctap.commands

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
@SerialName("ParameterValue")
sealed class ParameterValue {
    abstract val v: Any
}

@Serializable(with = UByteParameterSerializer::class)
data class UByteParameter(override val v: UByte) : ParameterValue()

class UByteParameterSerializer : KSerializer<UByteParameter> {
    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("UByteParameter") {
            element("value", UByte.serializer().descriptor)
        }

    override fun deserialize(decoder: Decoder): UByteParameter {
        return UByteParameter(decoder.decodeInt().toUByte())
    }

    override fun serialize(encoder: Encoder, value: UByteParameter) {
        encoder.encodeInt(value.v.toInt())
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ByteArrayParameter(@ByteString override val v: ByteArray) : ParameterValue() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ByteArrayParameter

        return v.contentEquals(other.v)
    }

    override fun hashCode(): Int {
        return v.contentHashCode()
    }
}

@Serializable
data class StringArrayParameter(override val v: Array<String>) : ParameterValue() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as StringArrayParameter

        return v.contentEquals(other.v)
    }

    override fun hashCode(): Int {
        return v.contentHashCode()
    }
}

@Serializable(with = MapParameterSerializer::class)
data class MapParameter(override val v: Map<UByte, @Polymorphic ParameterValue>) : ParameterValue()

@OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
class MapParameterSerializer : KSerializer<MapParameter> {
    override val descriptor: SerialDescriptor
        get() = buildSerialDescriptor("params", StructureKind.MAP)

    override fun deserialize(decoder: Decoder): MapParameter {
        val ret = decoder.decodeSerializableValue(
            MapSerializer(
                UByte.serializer(),
                ParameterValue.serializer(),
            ),
        )
        return MapParameter(ret)
    }

    override fun serialize(encoder: Encoder, value: MapParameter) {
        encoder.encodeSerializableValue(
            MapSerializer(
                UByte.serializer(),
                ParameterValue.serializer(),
            ),
            value.v,
        )
    }
}

@Serializable
data class StringParameter(override val v: String) : ParameterValue()

@Serializable
data class BooleanParameter(override val v: Boolean) : ParameterValue()

@Serializable
data class UIntParameter(override val v: UInt) : ParameterValue()

@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
class CtapCommandEncoder : KSerializer<CtapCommand> {
    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("CtapCommand") {
            element("className", String.serializer().descriptor)
            element("cmdByte", Byte.serializer().descriptor)
            element(
                "params",
                buildSerialDescriptor("params", StructureKind.MAP),
                isOptional = true,
            )
        }

    override fun deserialize(decoder: Decoder): CtapCommand {
        throw NotImplementedError()
    }

    override fun serialize(encoder: Encoder, value: CtapCommand) {
        val subEncoder = encoder.beginStructure(descriptor)
        // use index 1/2 because we don't bother putting in our class name
        subEncoder.encodeByteElement(descriptor, 1, value.cmdByte)
        val params = value.params
        if (params != null) {
            subEncoder.encodeSerializableElement(
                descriptor,
                2,
                MapSerializer(
                    UByte.serializer(),
                    ParameterValue.serializer(),
                ),
                params,
            )
        }
        subEncoder.endStructure(descriptor)
    }
}

@Serializable(with = CtapCommandEncoder::class)
@SerialName("CtapCommand")
sealed class CtapCommand {
    abstract val cmdByte: Byte
    abstract val params: Map<UByte, @Polymorphic ParameterValue>?

    fun getCBOR(): ByteArray {
        val encoder = CTAPCBOREncoder()
        encoder.encodeSerializableValue(serializer(), this)
        return encoder.getBytes()
    }
}
