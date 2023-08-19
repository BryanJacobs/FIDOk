package us.q3q.fidok.ctap.commands

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

typealias ExtensionName = String

@Serializable(with = ExtensionParameterValueSerializer::class)
data class ExtensionParameterValue(override val v: Map<ExtensionName, @Polymorphic ExtensionParameters>) : ParameterValue()

class ExtensionParameterValueSerializer : KSerializer<ExtensionParameterValue> {
    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("ExtensionParameter")

    override fun deserialize(decoder: Decoder): ExtensionParameterValue {
        TODO("Not yet implemented")
    }

    override fun serialize(encoder: Encoder, value: ExtensionParameterValue) {
        encoder.encodeSerializableValue(
            MapSerializer(
                ExtensionName.serializer(),
                ExtensionParameters.serializer(),
            ),
            value.v,
        )
    }
}

val extensionSerializers = SerializersModule {
    polymorphic(ExtensionParameters::class) {
        subclass(BooleanExtensionParameter::class, BooleanExtensionParameter.serializer())
        subclass(StringExtensionParameter::class, StringExtensionParameter.serializer())
        subclass(ByteArrayExtensionParameter::class, ByteArrayExtensionParameter.serializer())
        subclass(IntExtensionParameter::class, IntExtensionParameter.serializer())
        subclass(ByteExtensionParameter::class, ByteExtensionParameter.serializer())
        subclass(MapExtensionParameter::class, MapExtensionParameter.serializer())
    }
}

@Serializable
@SerialName("ExtensionParameters")
sealed class ExtensionParameters

@Serializable
data class BooleanExtensionParameter(val v: Boolean) : ExtensionParameters()

@Serializable
data class StringExtensionParameter(val v: String) : ExtensionParameters()

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ByteArrayExtensionParameter(@ByteString val v: ByteArray) : ExtensionParameters() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ByteArrayExtensionParameter

        return v.contentEquals(other.v)
    }

    override fun hashCode(): Int {
        return v.contentHashCode()
    }
}

@Serializable
data class IntExtensionParameter(val v: Int) : ExtensionParameters()

@Serializable
data class ByteExtensionParameter(val v: Byte) : ExtensionParameters()

@Serializable(with = MapExtensionParameterValueSerializer::class)
data class MapExtensionParameter(val v: Map<String, @Polymorphic ExtensionParameters>) : ExtensionParameters()

class MapExtensionParameterValueSerializer : KSerializer<MapExtensionParameter> {
    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("MapExtensionParameter")

    override fun deserialize(decoder: Decoder): MapExtensionParameter {
        TODO("Not yet implemented")
    }

    override fun serialize(encoder: Encoder, value: MapExtensionParameter) {
        encoder.encodeSerializableValue(
            MapSerializer(
                String.serializer(),
                ExtensionParameters.serializer(),
            ),
            value.v,
        )
    }
}
