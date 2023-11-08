package us.q3q.fidok.ctap.commands

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import us.q3q.fidok.webauthn.NoneAttestationStatement
import kotlin.reflect.KClass

/**
 * Represents a particular format/type of [AttestationStatement].
 *
 * @property value The canonical CTAP/Webauthn representation of the Attestation format
 * @property klass The FIDOk class that represents Attestations of this type, if any
 */
enum class AttestationTypes(val value: String, val klass: KClass<out Any>? = null) {
    /**
     * The most common Attestation type - a map with up to three fields.
     */
    PACKED("packed", PackedAttestationStatement::class),

    /**
     * An Attestation coming from a Trusted Platform Module - a chip built into certain computers.
     */
    TPM("tpm"),

    /**
     * Google likes to do things its own way, so this is an Attestation that absolutely COULD be [PACKED], but is
     * incompatible instead.
     */
    ANDROID_KEY("android-key"),

    /**
     * This represents the raw result of a call to Google's "Safety Net" API, and is useless to anyone who can't
     * communicate with that. Please don't ask why this is part of a nominally vendor-neutral standard; I doubt you'll
     * like the answer.
     */
    ANDROID_SAFETYNET("android-safetynet"),

    /**
     * This represents a CTAP1 (U2F) Attestation repackaged in CTAP2.
     */
    FIDO_U2F("fido-u2f"),

    /**
     * Whether the absence of an Attestation is an Attestation format is a question for the philosophers.
     */
    NONE("none", NoneAttestationStatement::class),

    /**
     * When Google did proprietary things, Apple did too. So there's an Apple-specific Attestation format.
     */
    APPLE("apple"), ;

    override fun toString(): String {
        return "AttestationType($value)"
    }
}

/**
 * Represents the response to a [MakeCredentialCommand].
 *
 * @property fmt The [type of Attestation provided][AttestationTypes]
 * @property authData The portion of the Authenticator's response that is signed - authenticated
 * @property rawAttStmt The Attestation Statement - the Authenticator declaring which manufacturer or whatever
 * pinky-promises that the key is securely stored - as Kotlin object. In order to interpret this, [fmt] needs to be
 * consulted as well
 * @property attStmt The Attestation Statement as a map of fields and their values. Which fields will be present
 * depends on the [type of attestation][fmt]
 * @property epAtt True if an Enterprise Attestation was provided. What that means... isn't standardized
 * @property largeBlobKey For reasons that don't appear to be extremely sane and likely relate to government regulatory
 * certifications, the [LargeBlobKeyExtension]'s return value is outside the [AuthenticatorData.extensions] and is
 * instead its own field in the [MakeCredentialResponse]. This contains that value, and may be used for
 * encrypting/decrypting the portion of the Large Blob Array that pertains to this Credential
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable(with = MakeCredentialResponseSerializer::class)
data class MakeCredentialResponse(
    val fmt: String,
    @ByteString val authData: AuthenticatorData,
    val rawAttStmt: AttestationStatement,
    val attStmt: Map<Any, Any>,
    val epAtt: Boolean?,
    @ByteString val largeBlobKey: ByteArray?,
) {

    /**
     * Access the Credential itself.
     *
     * @return Byte array representing the CTAP "Credential ID"
     * @throws IllegalStateException If no Credential is inside this object
     */
    fun getCredentialID(): ByteArray {
        return authData.attestedCredentialData?.credentialId
            ?: throw IllegalStateException("MakeCredentialResponse has no credential in it")
    }

    /**
     * Access the Credential's public key quickly.
     *
     * This key is necessary if one wishes to validate any [assertions][GetAssertionResponse] the Authenticator
     * produces for this Credential.
     *
     * @return The public key associated with the Credential
     * @throws IllegalStateException If no Credential is inside this object
     */
    fun getCredentialPublicKey(): COSEKey {
        return authData.attestedCredentialData?.credentialPublicKey
            ?: throw IllegalStateException("MakeCredentialResponse has no credential in it")
    }

    /**
     * Return the Attestation Statement as an instance of [PackedAttestationStatement].
     *
     * @throws IllegalStateException If [fmt] is not [AttestationTypes.PACKED.value]
     * @return Attestation Statement object
     */
    fun getPackedAttestationStatement(): PackedAttestationStatement {
        if (fmt != AttestationTypes.PACKED.value) {
            throw IllegalStateException("Not using packed attestation")
        }

        return rawAttStmt as PackedAttestationStatement
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as MakeCredentialResponse

        if (fmt != other.fmt) return false
        if (authData != other.authData) return false
        if (rawAttStmt != other.rawAttStmt) return false
        if (attStmt != other.attStmt) return false
        if (epAtt != other.epAtt) return false
        if (largeBlobKey != null) {
            if (other.largeBlobKey == null) return false
            if (!largeBlobKey.contentEquals(other.largeBlobKey)) return false
        } else if (other.largeBlobKey != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = fmt.hashCode()
        result = 31 * result + authData.hashCode()
        result = 31 * result + rawAttStmt.hashCode()
        result = 31 * result + attStmt.hashCode()
        result = 31 * result + (epAtt?.hashCode() ?: 0)
        result = 31 * result + (largeBlobKey?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * Deserializes a CBOR [MakeCredentialResponse] into a usable object.
 */
class MakeCredentialResponseSerializer : KSerializer<MakeCredentialResponse> {
    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("MakeCredentialResponse") {
            element("fmt", String.serializer().descriptor)
            element("authData", AuthenticatorData.serializer().descriptor)
            element("attStmt", MapSerializer(String.serializer(), ParameterValue.serializer()).descriptor, isOptional = true)
            element("epAtt", Boolean.serializer().descriptor, isOptional = true)
            element("largeBlobKey", ByteArraySerializer().descriptor, isOptional = true)
        }

    override fun deserialize(decoder: Decoder): MakeCredentialResponse {
        val composite = decoder.beginStructure(descriptor)
        val numParams = composite.decodeCollectionSize(descriptor)

        var fmt: String? = null
        var authData: AuthenticatorData? = null
        var attStmt: Map<Any, Any>? = null
        var rawAttStmt: AttestationStatement? = null
        var epAtt: Boolean? = null
        var largeBlobKey: ByteArray? = null

        for (i in 1..numParams) {
            val idx = composite.decodeElementIndex(descriptor)
            when (idx) {
                0x01 -> {
                    fmt = composite.decodeStringElement(descriptor, idx - 1)
                }
                0x02 -> {
                    authData = composite.decodeSerializableElement(
                        descriptor,
                        idx - 1,
                        AuthenticatorData.serializer(),
                        null,
                    )
                }
                0x03 -> {
                    if (fmt == null || authData == null) {
                        throw SerializationException(
                            "Got to makeCredentialsResponse attestation statement " +
                                "without finding fmt or authData",
                        )
                    }
                    val knownAttestationType = AttestationTypes.entries.find { it.value == fmt }
                    when (knownAttestationType) {
                        AttestationTypes.PACKED -> {
                            val att = composite.decodeSerializableElement(
                                descriptor,
                                idx - 1,
                                PackedAttestationStatement.serializer(),
                            )
                            rawAttStmt = att
                            attStmt = if (att.x5c != null) {
                                hashMapOf(
                                    "alg" to att.alg,
                                    "sig" to att.sig,
                                    "x5c" to att.x5c,
                                )
                            } else {
                                hashMapOf(
                                    "alg" to att.alg,
                                    "sig" to att.sig,
                                )
                            }
                        }
                        AttestationTypes.NONE -> {
                            rawAttStmt = NoneAttestationStatement()
                            val gottenMap = composite.decodeSerializableElement(
                                descriptor,
                                idx - 1,
                                MapSerializer(String.serializer(), String.serializer()),
                            )
                            if (gottenMap.isNotEmpty()) {
                                throw SerializationException("None attestation type has non-empty data")
                            }
                            attStmt = hashMapOf()
                        }
                        else -> {
                            TODO("handle as byte array?")
                        }
                    }
                }
                0x04 -> {
                    epAtt = composite.decodeBooleanElement(descriptor, idx - 1)
                }
                0x05 -> {
                    largeBlobKey = composite.decodeSerializableElement(
                        ByteArraySerializer().descriptor,
                        idx - 1,
                        ByteArraySerializer(),
                        null,
                    )
                }
                else -> {
                    // Ignore - unknown element
                }
            }
        }
        composite.endStructure(descriptor)

        if (fmt == null || authData == null || attStmt == null || rawAttStmt == null) {
            throw SerializationException("MakeCredentialsResponse missing required properties")
        }

        return MakeCredentialResponse(
            fmt = fmt,
            authData = authData,
            rawAttStmt = rawAttStmt,
            attStmt = attStmt,
            epAtt = epAtt,
            largeBlobKey = largeBlobKey,
        )
    }

    override fun serialize(encoder: Encoder, value: MakeCredentialResponse) {
        throw NotImplementedError()
    }
}
