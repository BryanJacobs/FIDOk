package us.q3q.fidok.ctap.commands

import co.touchlab.kermit.Logger
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@OptIn(ExperimentalSerializationApi::class)
@Serializable(with = EnumerateRPsResponseSerializer::class)
data class EnumerateRPsResponse(
    val rp: PublicKeyCredentialRpEntity?,
    @ByteString val rpIDHash: ByteArray?,
    val totalRPs: UInt? = null,
)

class EnumerateRPsResponseSerializer : KSerializer<EnumerateRPsResponse> {
    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("EnumerateRPsResponse") {
            element("rp", PublicKeyCredentialRpEntity.serializer().descriptor)
            element("rpIDHash", ByteArraySerializer().descriptor)
            element("totalRPs", UInt.serializer().descriptor, isOptional = true)
        }

    override fun deserialize(decoder: Decoder): EnumerateRPsResponse {
        val composite = decoder.beginStructure(descriptor)
        val numElements = composite.decodeCollectionSize(descriptor)
        if (numElements == 0 || numElements > 3) {
            throw SerializationException("Incorrect element count in EnumerateRPsResponse: $numElements")
        }

        var rp: PublicKeyCredentialRpEntity? = null
        var rpIDHash: ByteArray? = null
        var totalRPs: UInt? = null

        for (i in 1..numElements) {
            when (val idx = composite.decodeElementIndex(descriptor)) {
                0x03 ->
                    rp = composite.decodeSerializableElement(descriptor, 0, PublicKeyCredentialRpEntity.serializer())
                0x04 ->
                    rpIDHash = composite.decodeSerializableElement(descriptor, 1, ByteArraySerializer())
                0x05 ->
                    totalRPs = composite.decodeIntElement(descriptor, 2).toUInt()
                else ->
                    throw SerializationException("Unknown element index in EnumerateRPsResponse: $idx")
            }
        }

        composite.endStructure(descriptor)

        if (totalRPs == 0u) {
            Logger.w { "Misbehaving authenticator: returned zero RPs instead of NO_CREDENTIALS" }
        } else if (rp == null || rpIDHash == null) {
            throw SerializationException("Missing attribute in EnumerateRPsResponse")
        }

        return EnumerateRPsResponse(rp = rp, rpIDHash = rpIDHash, totalRPs = totalRPs)
    }

    override fun serialize(encoder: Encoder, value: EnumerateRPsResponse) {
        throw NotImplementedError("Cannot serialize a response")
    }
}
