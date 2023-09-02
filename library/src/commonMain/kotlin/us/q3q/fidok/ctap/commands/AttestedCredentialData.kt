package us.q3q.fidok.ctap.commands

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@OptIn(ExperimentalSerializationApi::class)
@Serializable(with = AttestedCredentialDataSerializer::class)
data class AttestedCredentialData(
    @ByteString val aaguid: ByteArray,
    @ByteString val credentialId: ByteArray,
    val credentialPublicKey: COSEKey,
) {
    init {
        require(aaguid.size == 16)
        require(credentialId.size <= 1023)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as AttestedCredentialData

        if (!aaguid.contentEquals(other.aaguid)) return false
        if (!credentialId.contentEquals(other.credentialId)) return false
        if (credentialPublicKey != other.credentialPublicKey) return false

        return true
    }

    override fun hashCode(): Int {
        var result = aaguid.contentHashCode()
        result = 31 * result + credentialId.contentHashCode()
        result = 31 * result + credentialPublicKey.hashCode()
        return result
    }
}

class AttestedCredentialDataSerializer : KSerializer<AttestedCredentialData> {
    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("AttestedCredentialData") {
            element("aaguid", ByteArraySerializer().descriptor)
            element("credentialIdLength", UShort.serializer().descriptor)
            element("credentialId", ByteArraySerializer().descriptor)
            element("credentialPublicKey", COSEKey.serializer().descriptor)
        }

    override fun deserialize(decoder: Decoder): AttestedCredentialData {
        // val composite = decoder.beginStructure(ByteArraySerializer().descriptor)
        // composite.decodeCollectionSize(ByteArraySerializer().descriptor)
        val aaguid = arrayListOf<Byte>()
        for (i in 1..16) {
            aaguid.add(decoder.decodeByte())
        }
        val credentialIdLengthHigh = decoder.decodeByte()
        val credentialIdLengthLow = decoder.decodeByte()
        val credentialIdLength = (credentialIdLengthHigh.toUByte().toInt() shl 8) + credentialIdLengthLow.toUByte().toInt()
        val credentialId = arrayListOf<Byte>()
        for (i in 1..credentialIdLength) {
            credentialId.add(decoder.decodeByte())
        }
        val credentialPublicKey = decoder.decodeSerializableValue(COSEKey.serializer())

        return AttestedCredentialData(
            aaguid = aaguid.toByteArray(),
            credentialId = credentialId.toByteArray(),
            credentialPublicKey = credentialPublicKey,
        )
    }

    override fun serialize(encoder: Encoder, value: AttestedCredentialData) {
        for (i in value.aaguid.indices) {
            encoder.encodeByte(value.aaguid[i])
        }
        encoder.encodeByte(((value.credentialId.size and 0x0000FF00) shr 8).toByte())
        encoder.encodeByte((value.credentialId.size and 0x000000FF).toByte())
        for (i in value.credentialId.indices) {
            encoder.encodeByte(value.credentialId[i])
        }
        encoder.encodeSerializableValue(COSEKey.serializer(), value.credentialPublicKey)
    }
}
