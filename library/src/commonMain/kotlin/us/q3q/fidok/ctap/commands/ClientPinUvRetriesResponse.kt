package us.q3q.fidok.ctap.commands

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Response to a [ClientPinCommand.getUVRetries] request
 *
 * @property uvRetries The number of times onboard UV may be tried before the Authenticator will require a PIN
 *                      (or lock): could be zero
 */
@Serializable(with = ClientPinUvRetriesResponseSerializer::class)
data class ClientPinUvRetriesResponse(val uvRetries: UInt)

/**
 * Deserializes a [ClientPinUvRetriesResponse]
 */
class ClientPinUvRetriesResponseSerializer : KSerializer<ClientPinUvRetriesResponse> {
    override val descriptor: SerialDescriptor
        get() =
            buildClassSerialDescriptor("ClientPinUvRetriesResponse") {
                element("uvRetries", UInt.serializer().descriptor)
            }

    override fun deserialize(decoder: Decoder): ClientPinUvRetriesResponse {
        val composite = decoder.beginStructure(descriptor)
        val numItems = composite.decodeCollectionSize(descriptor)
        if (numItems != 1) {
            throw SerializationException("ClientPinUvRetriesResponse had incorrect number of items in it: $numItems")
        }
        val param = composite.decodeIntElement(descriptor, 0)
        if (param != 0x05) {
            throw SerializationException("ClientPinUvRetriesResponse had param $param instead of 0x05")
        }
        val uvRetries = composite.decodeIntElement(descriptor, 0).toUInt()

        composite.endStructure(descriptor)
        return ClientPinUvRetriesResponse(
            uvRetries = uvRetries,
        )
    }

    override fun serialize(
        encoder: Encoder,
        value: ClientPinUvRetriesResponse,
    ) {
        throw NotImplementedError("Cannot serialize a response")
    }
}
