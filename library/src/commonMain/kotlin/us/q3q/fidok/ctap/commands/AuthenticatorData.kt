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
import kotlin.experimental.and

enum class FLAGS(val value: Byte) {
    UP(0x01),
    UV(0x04),
    BE(0x08),
    BS(0x10),
    AT(0x40),
    ED(0x80.toByte()),
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable(with = AuthenticatorDataSerializer::class)
data class AuthenticatorData(
    @ByteString val rawBytes: ByteArray,
    @ByteString val rpIdHash: ByteArray,
    val flags: Byte,
    val signCount: UInt,
    val attestedCredentialData: AttestedCredentialData? = null,
    val extensions: Map<ExtensionName, ExtensionParameters>? = null,
) {
    init {
        require(rpIdHash.size == 32)
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
        result = 31 * result + flags
        result = 31 * result + signCount.hashCode()
        result = 31 * result + (attestedCredentialData?.hashCode() ?: 0)
        result = 31 * result + (extensions?.hashCode() ?: 0)
        return result
    }
}

class AuthenticatorDataSerializer : KSerializer<AuthenticatorData> {
    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("AuthenticatorData") {
            element("rpIdHash", ByteArraySerializer().descriptor)
            element("flags", Byte.serializer().descriptor)
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
        val flags = nestedDeserializer.decodeByteElement(descriptor, 1)
        val signCount = (nestedDeserializer.decodeByteElement(descriptor, 2).toUByte().toUInt() shl 24) +
            (nestedDeserializer.decodeByteElement(descriptor, 2).toUByte().toUInt() shl 16) +
            (nestedDeserializer.decodeByteElement(descriptor, 2).toUByte().toUInt() shl 8) +
            nestedDeserializer.decodeByteElement(descriptor, 2).toUByte().toUInt()
        var attestedCredentialData: AttestedCredentialData? = null
        if ((flags and FLAGS.AT.value) != 0.toByte()) {
            attestedCredentialData = nestedDeserializer.decodeSerializableElement(descriptor, 3, AttestedCredentialData.serializer())
        }
        var extensions: Map<ExtensionName, ExtensionParameters>? = null
        if ((flags and FLAGS.ED.value) != 0.toByte()) {
            val results = nestedDeserializer.decodeSerializableElement(
                descriptor,
                4,
                CreationExtensionResultsSerializer(),
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

    override fun serialize(encoder: Encoder, value: AuthenticatorData) {
        val composite = encoder.beginCollection(
            ByteArraySerializer().descriptor,
            32 + 1 + 4,
        )
        for (i in value.rpIdHash.indices) {
            composite.encodeByteElement(descriptor, 0, value.rpIdHash[i])
        }
        composite.encodeByteElement(descriptor, 1, value.flags)
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
