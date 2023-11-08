package us.q3q.fidok.ctap.commands

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Algorithms that may be used to produce a [FIDO Credential][PublicKeyCredentialDescriptor] or
 * [Attestation][AttestationStatement].
 *
 * @property value COSE's canonical integer representing the algorithm
 */
enum class COSEAlgorithmIdentifier(val value: Long) {
    /**
     * ECDSA with a SHA-256 hash
     */
    ES256(-7),

    /**
     * Using Twisted Edwards (generally curve Ed25519)
     */
    EdDSA(-8),

    /**
     * ECDSA with a SHA-384 hash
     */
    ES384(-35),

    /**
     * ECDSA with a SHA-512 hash
     */
    ES512(-36),

    /**
     * RSASSA, PKCS1-v1_5 padding, with a SHA-256 hash
     */
    RS256(-257),
}

/**
 * The different "types" of [FIDO Credential][PublicKeyCredentialDescriptor].
 *
 * At the time of this writing, this is always "public-key".
 *
 * @property value COSE's canonical identifier for the Credential type
 */
enum class PublicKeyCredentialType(val value: String) {
    PUBLIC_KEY("public-key"),
}

/**
 * The cryptographic algorithms considered acceptable for a Credential.
 *
 * @property type The [PublicKeyCredentialType] for this entry
 * @property alg The [COSEAlgorithmIdentifier] for this entry
 */
@Serializable(with = PublicKeyCredentialsParametersSerializer::class)
data class PublicKeyCredentialParameters(
    val type: String,
    val alg: Long,
) {
    constructor(alg: COSEAlgorithmIdentifier) : this(PublicKeyCredentialType.PUBLIC_KEY, alg)
    constructor(type: PublicKeyCredentialType, alg: COSEAlgorithmIdentifier) : this(type.value, alg.value)

    constructor(type: String, alg: COSEAlgorithmIdentifier) : this(type, alg.value)

    constructor(type: PublicKeyCredentialType, alg: Long) : this(type.value, alg)

    override fun toString(): String {
        if (type != PublicKeyCredentialType.PUBLIC_KEY.value) {
            return super.toString()
        }
        val matchedAlg = COSEAlgorithmIdentifier.entries.find { it.value == alg } ?: return "unknown:$alg"
        return matchedAlg.name
    }
}

/**
 * Serializes and deserializes [PublicKeyCredentialParameters] to/from CTAP CBOR
 */
@OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
class PublicKeyCredentialsParametersSerializer : KSerializer<PublicKeyCredentialParameters> {
    override val descriptor: SerialDescriptor
        get() = buildSerialDescriptor("PublicKeyCredentialsParameters", StructureKind.MAP) {
            element("className", String.serializer().descriptor)
            element("alg_key", String.serializer().descriptor)
            element("alg", Int.serializer().descriptor)
            element("type_key", String.serializer().descriptor)
            element("type", String.serializer().descriptor)
        }

    override fun deserialize(decoder: Decoder): PublicKeyCredentialParameters {
        val composite = decoder.beginStructure(descriptor)
        val numItems = composite.decodeCollectionSize(descriptor)
        if (numItems != 2) {
            throw SerializationException("Incorrect item count in PublicKeyCredentialParameters: $numItems")
        }
        val algKey = composite.decodeStringElement(descriptor, 1)
        if (algKey != "alg") {
            throw SerializationException("Did not find alg element in PublicKeyCredentialParameters")
        }
        val alg = composite.decodeLongElement(descriptor, 2)
        val typeKey = composite.decodeStringElement(descriptor, 3)
        if (typeKey != "type") {
            throw SerializationException("Did not find type element in PublicKeyCredentialParameters")
        }
        val type = composite.decodeStringElement(descriptor, 4)
        composite.endStructure(descriptor)
        return PublicKeyCredentialParameters(alg = alg, type = type)
    }

    override fun serialize(encoder: Encoder, value: PublicKeyCredentialParameters) {
        val subEncoder = encoder.beginCollection(descriptor, 2)
        subEncoder.encodeStringElement(descriptor, 1, "alg")
        subEncoder.encodeLongElement(descriptor, 2, value.alg)
        subEncoder.encodeStringElement(descriptor, 3, "type")
        subEncoder.encodeStringElement(descriptor, 4, value.type)
        subEncoder.endStructure(descriptor)
    }
}

@Serializable
data class PublicKeyCredentialsParametersParameter(override val v: List<PublicKeyCredentialParameters>) : ParameterValue()
