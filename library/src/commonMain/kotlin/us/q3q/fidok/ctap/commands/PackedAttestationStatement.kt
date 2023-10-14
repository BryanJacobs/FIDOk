package us.q3q.fidok.ctap.commands

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ArraySerializer
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@OptIn(ExperimentalSerializationApi::class)
@Serializable(with = PackedAttestationSerializer::class)
data class PackedAttestationStatement(
    val alg: Long,
    @ByteString val sig: ByteArray,
    val x5c: Array<ByteArray>? = null,
) : AttestatationStatement() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as PackedAttestationStatement

        if (alg != other.alg) return false
        if (!sig.contentEquals(other.sig)) return false
        if (x5c != null) {
            if (other.x5c == null) return false
            if (!x5c.contentDeepEquals(other.x5c)) return false
        } else if (other.x5c != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = alg.hashCode()
        result = 31 * result + sig.contentHashCode()
        result = 31 * result + (x5c?.contentDeepHashCode() ?: 0)
        return result
    }
}

@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
class PackedAttestationSerializer : KSerializer<PackedAttestationStatement> {
    override val descriptor: SerialDescriptor
        get() = buildSerialDescriptor("PackedAttestationStatement", StructureKind.MAP) {
            element("alg_key", String.serializer().descriptor)
            element("alg", Long.serializer().descriptor)
            element("sig_key", String.serializer().descriptor)
            element("sig", ByteArraySerializer().descriptor)
            element("x5c_key", String.serializer().descriptor, isOptional = true)
            element("x5c", ArraySerializer(ByteArray::class, ByteArraySerializer()).descriptor, isOptional = true)
        }

    override fun deserialize(decoder: Decoder): PackedAttestationStatement {
        val composite = decoder.beginStructure(descriptor)
        val numItems = composite.decodeCollectionSize(descriptor)
        if (numItems != 2 && numItems != 3) {
            throw SerializationException("Incorrect number of items in Packed attestation statement")
        }
        composite.decodeStringElement(descriptor, 0)
        val alg = composite.decodeLongElement(descriptor, 1)
        composite.decodeStringElement(descriptor, 2)
        val sig = composite.decodeSerializableElement(descriptor, 3, ByteArraySerializer())

        var x5c: List<ByteArray>? = null
        if (numItems == 3) {
            composite.decodeStringElement(descriptor, 4)
            x5c = composite.decodeSerializableElement(descriptor, 5, ListSerializer(ByteArraySerializer()))
        }
        return PackedAttestationStatement(alg = alg, sig = sig, x5c = x5c?.toTypedArray())
    }

    override fun serialize(encoder: Encoder, value: PackedAttestationStatement) {
        val composite = encoder.beginCollection(descriptor, if (value.x5c == null) 2 else 3)
        composite.encodeStringElement(descriptor, 0, "alg")
        composite.encodeLongElement(descriptor, 1, value.alg)
        composite.encodeStringElement(descriptor, 2, "sig")
        composite.encodeSerializableElement(descriptor, 3, ByteArraySerializer(), value.sig)
        val x5c = value.x5c
        if (x5c != null) {
            composite.encodeStringElement(descriptor, 4, "x5c")
            composite.encodeSerializableElement(
                descriptor,
                5,
                ArraySerializer(ByteArray::class, ByteArraySerializer()),
                x5c,
            )
        }
        composite.endStructure(descriptor)
    }
}
