package us.q3q.fidok.ctap.commands

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = ClientPinGetRetriesResponseSerializer::class)
data class ClientPinGetRetriesResponse(val pinRetries: UInt, val powerCycleState: Boolean?)

class ClientPinGetRetriesResponseSerializer : KSerializer<ClientPinGetRetriesResponse> {
    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("ClientPinGetRetriesResponse") {
            element("pinRetries", UInt.serializer().descriptor)
            element("powerCycleState", Boolean.serializer().descriptor, isOptional = true)
        }

    override fun deserialize(decoder: Decoder): ClientPinGetRetriesResponse {
        val composite = decoder.beginStructure(descriptor)
        val numItems = composite.decodeCollectionSize(descriptor)
        if (numItems != 1 && numItems != 2) {
            throw SerializationException("ClientPinGetRetriesResponse had incorrect number of items in it: $numItems")
        }
        val param = composite.decodeIntElement(descriptor, 0)
        if (param != 0x03) {
            throw SerializationException("ClientPinGetRetriesResponse had param $param instead of 0x03")
        }
        val pinRetries = composite.decodeIntElement(descriptor, 0).toUInt()
        var powerCycleState: Boolean? = null

        if (numItems == 2) {
            val nextParam = composite.decodeIntElement(descriptor, 1)
            if (nextParam != 0x04) {
                throw SerializationException("ClientPinGetRetriesResponse had param $param instead of 0x04")
            }
            powerCycleState = composite.decodeBooleanElement(descriptor, 1)
        }
        composite.endStructure(descriptor)
        return ClientPinGetRetriesResponse(
            pinRetries = pinRetries,
            powerCycleState = powerCycleState,
        )
    }

    override fun serialize(encoder: Encoder, value: ClientPinGetRetriesResponse) {
        throw NotImplementedError("Cannot serialize a response")
    }
}
