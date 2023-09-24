package us.q3q.fidok.ctap.commands

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = ClientPinGetTokenResponseSerializer::class)
data class ClientPinGetTokenResponse(val pinUvAuthToken: ByteArray) {
    init {
        require(pinUvAuthToken.size == 32 || pinUvAuthToken.size == 48)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ClientPinGetTokenResponse

        return pinUvAuthToken.contentEquals(other.pinUvAuthToken)
    }

    override fun hashCode(): Int {
        return pinUvAuthToken.contentHashCode()
    }
}

class ClientPinGetTokenResponseSerializer : KSerializer<ClientPinGetTokenResponse> {
    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("ClientPinGetTokenResponse") {
            element("pinUvAuthToken", ByteArraySerializer().descriptor)
        }

    override fun deserialize(decoder: Decoder): ClientPinGetTokenResponse {
        val composite = decoder.beginStructure(descriptor)
        val numItems = composite.decodeCollectionSize(descriptor)
        if (numItems != 1) {
            throw SerializationException("ClientPinGetTokenResponse had incorrect number of items in it: $numItems")
        }
        val param = composite.decodeIntElement(descriptor, 0)
        if (param != 0x02) {
            throw SerializationException("ClientPinGetTokenResponse contained unknown parameter: $param")
        }
        val pinUvAuthToken = composite.decodeSerializableElement(descriptor, 0, ByteArraySerializer())
        composite.endStructure(descriptor)
        return ClientPinGetTokenResponse(pinUvAuthToken)
    }

    override fun serialize(encoder: Encoder, value: ClientPinGetTokenResponse) {
        throw NotImplementedError("Cannot serialize a response")
    }
}
