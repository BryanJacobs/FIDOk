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

@OptIn(ExperimentalSerializationApi::class, ExperimentalStdlibApi::class)
@Serializable
data class ByteArrayParameter(
    @ByteString override val v: ByteArray,
) : ParameterValue() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ByteArrayParameter

        return v.contentEquals(other.v)
    }

    override fun hashCode(): Int {
        return v.contentHashCode()
    }

    override fun toString(): String {
        return "ByteArrayParameter(${v.toHexString()})"
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
        val ret =
            decoder.decodeSerializableValue(
                MapSerializer(
                    UByte.serializer(),
                    ParameterValue.serializer(),
                ),
            )
        return MapParameter(ret)
    }

    override fun serialize(
        encoder: Encoder,
        value: MapParameter,
    ) {
        encoder.encodeSerializableValue(
            MapSerializer(
                UByte.serializer(),
                ParameterValue.serializer(),
            ),
            value.v,
        )
    }
}

@Serializable(with = UByteParameterSerializer::class)
data class UByteParameter(override val v: UByte) : ParameterValue()

class UByteParameterSerializer : KSerializer<UByteParameter> {
    override val descriptor: SerialDescriptor
        get() =
            buildClassSerialDescriptor("UByteParameter") {
                element("value", UByte.serializer().descriptor)
            }

    override fun deserialize(decoder: Decoder): UByteParameter {
        return UByteParameter(decoder.decodeInt().toUByte())
    }

    override fun serialize(
        encoder: Encoder,
        value: UByteParameter,
    ) {
        encoder.encodeInt(value.v.toInt())
    }
}

@Serializable
data class StringParameter(override val v: String) : ParameterValue()

@Serializable
data class BooleanParameter(override val v: Boolean) : ParameterValue()

@Serializable
data class UIntParameter(override val v: UInt) : ParameterValue()

@Serializable
data class ULongParameter(override val v: ULong) : ParameterValue()
