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
 * Currently, only holds some types of key:
 * - ES256
 * - ES384
 * - ES512
 * - EdDSA
 *
 * @property kty The "key type", loosely describing the algorithm used for this key
 * @property alg The signature algorithm with which the key is used
 * @property crv For an EC key, the curve number on which points reside
 * @property x The X-coordinate of the EC point, as a 32-byte array
 * @property y The Y-coordinate of the EC point, as a 32-byte array. Null for EdDSA keys.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable(with = COSEKeySerializer::class)
data class COSEKey(
    val kty: Int,
    val alg: Long,
    val crv: Int,
    @ByteString val x: ByteArray,
    @ByteString val y: ByteArray?,
) {
    init {
        require(crv == 1 || (alg != -7L && alg != -25L)) // ES256 mandatory curve
        require(crv == 2 || alg != -35L) // ES384 mandatory curve
        require(crv == 3 || alg != -36L) // ES512 mandatory curve
        require(crv == 6 || alg != -8L) // edDSA mandatory curve
        require((crv != 1 && crv != 2 && crv != 3) || (x.size == 32 && y?.size == 32 && kty == 2)) // basic ecdsa reqs
        require(alg != -8L || kty == 1) // basic eddsa reqs
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as COSEKey

        if (kty != other.kty) return false
        if (alg != other.alg) return false
        if (crv != other.crv) return false
        if (!x.contentEquals(other.x)) return false
        if (y != null) {
            if (other.y == null) return false
            if (!y.contentEquals(other.y)) return false
        } else if (other.y != null) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = kty
        result = 31 * result + alg.hashCode()
        result = 31 * result + crv
        result = 31 * result + x.contentHashCode()
        result = 31 * result + (y?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * Serializes and deserializes [COSEKey] instances
 */
@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
class COSEKeySerializer : KSerializer<COSEKey> {
    override val descriptor: SerialDescriptor
        get() =
            buildSerialDescriptor("COSEKey", StructureKind.MAP) {
                element("kty", Int.serializer().descriptor)
                element("alg", Int.serializer().descriptor)
                element("crv", Int.serializer().descriptor)
                element("x", ByteArraySerializer().descriptor)
                element("y", ByteArraySerializer().descriptor, isOptional = true)
            }

    override fun deserialize(decoder: Decoder): COSEKey {
        val composite = decoder.beginStructure(descriptor)
        val numItems = composite.decodeCollectionSize(descriptor)

        if (numItems != 4 && numItems != 5) {
            throw SerializationException("COSEKey had $numItems items in it; expected four or five")
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

        return COSEKey(kty = kty, alg = alg, crv = crv, x = x, y = y)
    }

    override fun serialize(
        encoder: Encoder,
        value: COSEKey,
    ) {
        val composite = encoder.beginCollection(descriptor, if (value.y != null) 5 else 4)
        composite.encodeIntElement(descriptor, 0, 1)
        composite.encodeIntElement(descriptor, 0, value.kty)
        composite.encodeIntElement(descriptor, 1, 3)
        composite.encodeLongElement(descriptor, 1, value.alg)
        composite.encodeIntElement(descriptor, 2, -1)
        composite.encodeIntElement(descriptor, 2, value.crv)
        composite.encodeIntElement(descriptor, 3, -2)
        composite.encodeSerializableElement(descriptor, 3, ByteArraySerializer(), value.x)
        if (value.y != null) {
            composite.encodeIntElement(descriptor, 4, -3)
            composite.encodeSerializableElement(descriptor, 4, ByteArraySerializer(), value.y)
        }
        composite.endStructure(descriptor)
    }
}
