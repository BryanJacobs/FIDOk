package us.q3q.fidok.ctap.commands

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = ClientPinGetKeyAgreementResponseSerializer::class)
data class ClientPinGetKeyAgreementResponse(val key: COSEKey)

class ClientPinGetKeyAgreementResponseSerializer : KSerializer<ClientPinGetKeyAgreementResponse> {
    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("ClientPinGetKeyAgreementResponse") {
            element("key", COSEKey.serializer().descriptor)
        }

    override fun deserialize(decoder: Decoder): ClientPinGetKeyAgreementResponse {
        val composite = decoder.beginStructure(descriptor)
        val numItems = composite.decodeCollectionSize(descriptor)
        if (numItems != 1) {
            throw SerializationException("ClientPinGetKeyAgreementResponse had incorrect number of items in it: $numItems")
        }
        val param = composite.decodeIntElement(descriptor, 0)
        if (param != 0x01) {
            throw SerializationException("ClientPinGetKeyAgreementResponse contained unknown parameter: $param")
        }
        val key = composite.decodeSerializableElement(descriptor, 0, COSEKey.serializer())
        composite.endStructure(descriptor)
        return ClientPinGetKeyAgreementResponse(key)
    }

    override fun serialize(encoder: Encoder, value: ClientPinGetKeyAgreementResponse) {
        throw NotImplementedError("Cannot serialize a response")
    }
}
