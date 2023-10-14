package us.q3q.fidok.ctap.commands

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * An encoding of a key that represents its mathematical parameters.
 *
 * Currently, only holds ECDSA keys on NIST P-256.
 *
 * @property kty The "key type", loosely describing the algorithm used for this key
 * @property alg The signature algorithm with which the key is used
 * @property crv For an EC key, the curve number on which points reside
 * @property x The X-coordinate of the EC point, as a 32-byte array
 * @property y The Y-coordinate of the EC point, as a 32-byte array
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable(with = COSEKeySerializer::class)
data class COSEKey(val kty: Int, val alg: Long, val crv: Int, @ByteString val x: ByteArray, @ByteString val y: ByteArray) {
    init {
        require(kty == 2)
        require(alg == -7L || alg == -25L)
        require(crv == 1)
        require(x.size == 32)
        require(y.size == 32)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as COSEKey

        if (kty != other.kty) return false
        if (alg != other.alg) return false
        if (crv != other.crv) return false
        if (!x.contentEquals(other.x)) return false
        if (!y.contentEquals(other.y)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = kty
        result = 31 * result + alg.hashCode()
        result = 31 * result + crv
        result = 31 * result + x.contentHashCode()
        result = 31 * result + y.contentHashCode()
        return result
    }
}

/**
 * Serializes and deserializes [COSEKey] instances
 */
@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
class COSEKeySerializer : KSerializer<COSEKey> {
    override val descriptor: SerialDescriptor
        get() = buildSerialDescriptor("COSEKey", StructureKind.MAP) {
            element("kty", Int.serializer().descriptor)
            element("alg", Int.serializer().descriptor)
            element("crv", Int.serializer().descriptor)
            element("x", ByteArraySerializer().descriptor)
            element("y", ByteArraySerializer().descriptor)
        }

    override fun deserialize(decoder: Decoder): COSEKey {
        val composite = decoder.beginStructure(descriptor)
        val numItems = composite.decodeCollectionSize(descriptor)

        if (numItems != 5) {
            throw SerializationException("COSEKey had $numItems items in it; expected five")
        }

        var kty: Int? = null
        var alg: Long? = null
        var crv: Int? = null
        var x: ByteArray? = null
        var y: ByteArray? = null

        for (i in 1..numItems) {
            val idx = composite.decodeIntElement(descriptor, 0)
            when (idx) {
                1 ->
                    kty = composite.decodeIntElement(descriptor, 0)
                3 ->
                    alg = composite.decodeLongElement(descriptor, 1)
                -1 ->
                    crv = composite.decodeIntElement(descriptor, 2)
                -2 ->
                    x = composite.decodeSerializableElement(descriptor, 3, ByteArraySerializer())
                -3 ->
                    y = composite.decodeSerializableElement(descriptor, 3, ByteArraySerializer())
                else ->
                    throw SerializationException("Unknown COSEKey element $idx")
            }
        }

        composite.endStructure(descriptor)

        require(kty != null)
        require(alg != null)
        require(crv != null)
        require(x != null)
        require(y != null)

        return COSEKey(kty = kty, alg = alg, crv = crv, x = x, y = y)
    }

    override fun serialize(encoder: Encoder, value: COSEKey) {
        val composite = encoder.beginCollection(descriptor, 5)
        composite.encodeIntElement(descriptor, 0, 1)
        composite.encodeIntElement(descriptor, 0, value.kty)
        composite.encodeIntElement(descriptor, 1, 3)
        composite.encodeLongElement(descriptor, 1, value.alg)
        composite.encodeIntElement(descriptor, 2, -1)
        composite.encodeIntElement(descriptor, 2, value.crv)
        composite.encodeIntElement(descriptor, 3, -2)
        composite.encodeSerializableElement(descriptor, 3, ByteArraySerializer(), value.x)
        composite.encodeIntElement(descriptor, 4, -3)
        composite.encodeSerializableElement(descriptor, 4, ByteArraySerializer(), value.y)
        composite.endStructure(descriptor)
    }
}
