package us.q3q.fidok.ctap.commands

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
class CtapCommandEncoder : KSerializer<CtapCommand> {
    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("CtapCommand") {
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
        subEncoder.encodeByteElement(descriptor, 0, value.cmdByte)
        val params = value.params
        if (params != null) {
            subEncoder.encodeSerializableElement(
                descriptor,
                1,
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

    override fun toString(): String {
        return this::class.simpleName ?: super.toString()
    }
}
