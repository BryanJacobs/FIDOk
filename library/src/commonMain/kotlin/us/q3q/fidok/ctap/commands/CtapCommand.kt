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

/**
 * Base CTAP command class: pairs a command type byte with optional parameters.
 */
@Serializable(with = CtapCommandSerializer::class)
@SerialName("CtapCommand")
sealed class CtapCommand {
    /**
     * CTAP command number
     */
    abstract val cmdByte: Byte

    /**
     * Any and all parameters for this command.
     *
     * In CTAP, parameters always use positive integers as keys.
     */
    abstract val params: Map<UByte, @Polymorphic ParameterValue>?

    /**
     * Serialize the command to CTAP CBOR.
     *
     * @return A byte array representing the serialized command, suitable for delivery
     *         to an Authenticator
     */
    fun getCBOR(): ByteArray {
        val encoder = CTAPCBOREncoder()
        encoder.encodeSerializableValue(serializer(), this)
        return encoder.getBytes()
    }

    override fun toString(): String {
        return this::class.simpleName ?: super.toString()
    }
}

/**
 * Serializes [CtapCommand] instances
 */
@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
class CtapCommandSerializer : KSerializer<CtapCommand> {
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
