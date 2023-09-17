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
import kotlin.reflect.KClass

enum class AttestationTypes(val value: String, val klass: KClass<out Any>? = null) {
    PACKED("packed", PackedAttestationStatement::class),
    TPM("tpm"),
    ANDROID_KEY("android-key"),
    ANDROID_SAFETYNET("android-safetynet"),
    FIDO_U2F("fido-u2f"),
    NONE("none"),
    APPLE("apple"), ;

    override fun toString(): String {
        return "AttestationType($value)"
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable(with = MakeCredentialResponseSerializer::class)
data class MakeCredentialResponse(
    val fmt: String,
    @ByteString val authData: AuthenticatorData,
    val attStmt: Map<Any, Any>,
    val epAtt: Boolean?,
    @ByteString val largeBlobKey: ByteArray?,
) {

    fun getCredentialID(): ByteArray {
        return authData.attestedCredentialData?.credentialId
            ?: throw IllegalStateException("MakeCredentialResponse has no credential in it")
    }

    fun getCredentialPublicKey(): COSEKey {
        return authData.attestedCredentialData?.credentialPublicKey
            ?: throw IllegalStateException("MakeCredentialResponse has no credential in it")
    }

    fun getPackedAttestationStatement(): PackedAttestationStatement {
        if (fmt != AttestationTypes.PACKED.value) {
            throw IllegalStateException("Not using packed attestation")
        }

        val arrUncast = attStmt["x5c"] as Array<*>?

        return PackedAttestationStatement(
            alg = attStmt["alg"] as Int,
            sig = attStmt["sig"] as ByteArray,
            x5c = arrUncast?.map { it as ByteArray }?.toTypedArray(),
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as MakeCredentialResponse

        if (fmt != other.fmt) return false
        if (authData != other.authData) return false
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
        result = 31 * result + attStmt.hashCode()
        result = 31 * result + (epAtt?.hashCode() ?: 0)
        result = 31 * result + (largeBlobKey?.contentHashCode() ?: 0)
        return result
    }
}

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
                            val gottenMap = composite.decodeSerializableElement(
                                descriptor,
                                idx - 1,
                                MapSerializer(String.serializer(), String.serializer()),
                            )
                            if (gottenMap.isNotEmpty()) {
                                throw SerializationException("None attestationtype has non-empty data")
                            }
                            attStmt = hashMapOf()
                        }
                        else -> {
                            TODO("handle as byte array")
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

        if (fmt == null || authData == null || attStmt == null) {
            throw SerializationException("MakeCredentialsResponse missing required properties")
        }

        return MakeCredentialResponse(
            fmt = fmt,
            authData = authData,
            attStmt = attStmt,
            epAtt = epAtt,
            largeBlobKey = largeBlobKey,
        )
    }

    override fun serialize(encoder: Encoder, value: MakeCredentialResponse) {
        throw NotImplementedError()
    }
}
