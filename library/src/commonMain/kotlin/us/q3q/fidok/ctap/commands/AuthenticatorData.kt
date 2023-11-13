package us.q3q.fidok.ctap.commands

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import us.q3q.fidok.ctap.data.AttestedCredentialData
import us.q3q.fidok.ctap.data.FLAGS

/**
 * Represents a CTAP/Webauthn "authenticator data" object.
 *
 * This contains various data to do with either asserting a [newly created credential][MakeCredentialCommand]
 * or [an assertion with an existing credential][GetAssertionResponse]. The [attestedCredentialData]
 * field will be present only for newly created credentials.
 *
 * @property rawBytes The raw bytes representing this data object, exactly as they were received from
 *                    the authenticator. This is necessary for signature checking, or to parse out
 *                    unknown fields (such as enterprise attestations) from the `attestedCredentialData`
 * @property rpIdHash The SHA-256 hash of the Relying Party ID to which the assertion pertains
 * @property flags The raw CTAP flags received; use the `hasFlag` method to check the contents of this field
 * @property signCount The Authenticator's signature operations counter, measuring the number of assertions
 *                     (even as part of a `MakeCredential`) since the last Reset
 * @property attestedCredentialData The credential itself, including its public key. This field will be absent
 *                                  in `GetAssertion` calls using passed-in credentials, and present in
 *                                  `MakeCredential` calls or `GetAssertion` calls using discoverable credentials
 * @property extensions Any extension data sent back by the Authenticator. Keys are extension names, and values
 *                      (and their types) depend on the particular extension
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable(with = AuthenticatorDataSerializer::class)
data class AuthenticatorData(
    @ByteString val rawBytes: ByteArray,
    @ByteString val rpIdHash: ByteArray,
    val flags: UByte,
    val signCount: UInt,
    val attestedCredentialData: AttestedCredentialData? = null,
    val extensions: Map<ExtensionName, ExtensionParameters>? = null,
) {
    init {
        require(rpIdHash.size == 32)
    }

    fun hasFlag(flag: FLAGS): Boolean {
        return (flags and flag.value) != 0x00.toUByte()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as AuthenticatorData

        if (!rpIdHash.contentEquals(other.rpIdHash)) return false
        if (flags != other.flags) return false
        if (signCount != other.signCount) return false
        if (attestedCredentialData != other.attestedCredentialData) return false
        if (extensions != other.extensions) return false

        return true
    }

    override fun hashCode(): Int {
        var result = rpIdHash.contentHashCode()
        result = 31 * result + flags.toInt()
        result = 31 * result + signCount.hashCode()
        result = 31 * result + (attestedCredentialData?.hashCode() ?: 0)
        result = 31 * result + (extensions?.hashCode() ?: 0)
        return result
    }
}

/**
 * Serializes or deserializes an [AuthenticatorData] object
 */
class AuthenticatorDataSerializer : KSerializer<AuthenticatorData> {
    override val descriptor: SerialDescriptor
        get() =
            buildClassSerialDescriptor("AuthenticatorData") {
                element("rpIdHash", ByteArraySerializer().descriptor)
                element("flags", UByte.serializer().descriptor)
                element("signCount", UInt.serializer().descriptor)
                element("attestedCredentialData", AttestedCredentialData.serializer().descriptor, isOptional = true)
                element(
                    "extensions",
                    MapSerializer(
                        ExtensionName.serializer(),
                        ExtensionParameters.serializer(),
                    ).descriptor,
                    isOptional = true,
                )
            }

    override fun deserialize(decoder: Decoder): AuthenticatorData {
        val rawAuthData = decoder.decodeSerializableValue(ByteArraySerializer())

        val nestedDeserializer = CTAPCBORDecoder(rawAuthData)

        val rpIdHash = arrayListOf<Byte>()
        for (i in 1..32) {
            rpIdHash.add(nestedDeserializer.decodeByteElement(descriptor, 0))
        }
        val flags = nestedDeserializer.decodeByteElement(descriptor, 1).toUByte()
        val signCount =
            (nestedDeserializer.decodeByteElement(descriptor, 2).toUByte().toUInt() shl 24) +
                (nestedDeserializer.decodeByteElement(descriptor, 2).toUByte().toUInt() shl 16) +
                (nestedDeserializer.decodeByteElement(descriptor, 2).toUByte().toUInt() shl 8) +
                nestedDeserializer.decodeByteElement(descriptor, 2).toUByte().toUInt()
        var attestedCredentialData: AttestedCredentialData? = null
        if ((flags and FLAGS.ATTESTED.value) != 0.toUByte()) {
            attestedCredentialData = nestedDeserializer.decodeSerializableElement(descriptor, 3, AttestedCredentialData.serializer())
        }
        var extensions: Map<ExtensionName, ExtensionParameters>? = null
        if ((flags and FLAGS.EXTENSION_DATA.value) != 0.toUByte()) {
            val serializer =
                if (attestedCredentialData != null) {
                    CreationExtensionResultsSerializer()
                } else {
                    AssertionExtensionResultsSerializer()
                }
            val results =
                nestedDeserializer.decodeSerializableElement(
                    descriptor,
                    4,
                    serializer,
                )
            extensions = results.v
        }

        return AuthenticatorData(
            rawBytes = rawAuthData,
            rpIdHash = rpIdHash.toByteArray(),
            flags = flags,
            signCount = signCount,
            attestedCredentialData = attestedCredentialData,
            extensions = extensions,
        )
    }

    override fun serialize(
        encoder: Encoder,
        value: AuthenticatorData,
    ) {
        val composite =
            encoder.beginCollection(
                ByteArraySerializer().descriptor,
                32 + 1 + 4,
            )
        for (i in value.rpIdHash.indices) {
            composite.encodeByteElement(descriptor, 0, value.rpIdHash[i])
        }
        composite.encodeIntElement(descriptor, 1, value.flags.toInt())
        composite.encodeIntElement(descriptor, 2, value.signCount.toInt())
        if (value.attestedCredentialData != null) {
            composite.encodeSerializableElement(
                descriptor,
                3,
                AttestedCredentialData.serializer(),
                value.attestedCredentialData,
            )
        }
        if (value.extensions != null) {
            composite.encodeSerializableElement(
                descriptor,
                4,
                MapSerializer(
                    ExtensionName.serializer(),
                    ExtensionParameters.serializer(),
                ),
                value.extensions,
            )
        }
        composite.endStructure(ByteArraySerializer().descriptor)
    }
}
