package us.q3q.fidok.ctap.commands

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
@Serializable(with = GetAssertionResponseSerializer::class)
data class GetAssertionResponse(
    val credential: PublicKeyCredentialDescriptor,
    val authData: AuthenticatorData,
    @ByteString val signature: ByteArray,
    val user: PublicKeyCredentialUserEntity?,
    val numberOfCredentials: Int?,
    val userSelected: Boolean?,
    @ByteString val largeBlobKey: ByteArray?,
) {

    init {
        require((numberOfCredentials ?: 1) == 1 || userSelected == null)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as GetAssertionResponse

        if (credential != other.credential) return false
        if (authData != other.authData) return false
        if (!signature.contentEquals(other.signature)) return false
        if (user != other.user) return false
        if (numberOfCredentials != other.numberOfCredentials) return false
        if (userSelected != other.userSelected) return false
        if (largeBlobKey != null) {
            if (other.largeBlobKey == null) return false
            if (!largeBlobKey.contentEquals(other.largeBlobKey)) return false
        } else if (other.largeBlobKey != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = credential.hashCode()
        result = 31 * result + authData.hashCode()
        result = 31 * result + signature.contentHashCode()
        result = 31 * result + (user?.hashCode() ?: 0)
        result = 31 * result + (numberOfCredentials ?: 0)
        result = 31 * result + (userSelected?.hashCode() ?: 0)
        result = 31 * result + (largeBlobKey?.contentHashCode() ?: 0)
        return result
    }
}

class GetAssertionResponseSerializer : KSerializer<GetAssertionResponse> {
    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("GetAssertionResponse") {
            element("credential", PublicKeyCredentialDescriptor.serializer().descriptor)
            element("authData", AuthenticatorData.serializer().descriptor)
            element("signature", ByteArraySerializer().descriptor)
            element("user", PublicKeyCredentialUserEntity.serializer().descriptor, isOptional = true)
            element("numberOfCredentials", Int.serializer().descriptor, isOptional = true)
            element("userSelected", Boolean.serializer().descriptor, isOptional = true)
            element("largeBlobKey", ByteArraySerializer().descriptor, isOptional = true)
        }

    override fun deserialize(decoder: Decoder): GetAssertionResponse {
        val composite = decoder.beginStructure(descriptor)
        val numParams = composite.decodeCollectionSize(descriptor)

        var credential: PublicKeyCredentialDescriptor? = null
        var authData: AuthenticatorData? = null
        var signature: ByteArray? = null
        var user: PublicKeyCredentialUserEntity? = null
        var numberOfCredentials: Int? = null
        var userSelected: Boolean? = null
        var largeBlobKey: ByteArray? = null

        for (i in 0..<numParams) {
            val idx = composite.decodeElementIndex(descriptor)
            when (idx) {
                0x01 ->
                    credential = composite.decodeSerializableElement(descriptor, idx - 1, PublicKeyCredentialDescriptor.serializer())
                0x02 ->
                    authData = composite.decodeSerializableElement(descriptor, idx - 1, AuthenticatorData.serializer())
                0x03 ->
                    signature = composite.decodeSerializableElement(descriptor, idx - 1, ByteArraySerializer())
                0x04 ->
                    user = composite.decodeSerializableElement(descriptor, idx - 1, PublicKeyCredentialUserEntity.serializer())
                0x05 ->
                    numberOfCredentials = composite.decodeIntElement(descriptor, idx - 1)
                0x06 ->
                    userSelected = composite.decodeBooleanElement(descriptor, idx - 1)
                0x07 ->
                    largeBlobKey = composite.decodeSerializableElement(descriptor, idx - 1, ByteArraySerializer())
                else -> {
                    // ignore
                }
            }
        }

        composite.endStructure(descriptor)

        if (credential == null || authData == null || signature == null) {
            throw SerializationException("GetAssertionResponse missing required field(s)")
        }

        return GetAssertionResponse(
            credential = credential,
            authData = authData,
            signature = signature,
            user = user,
            numberOfCredentials = numberOfCredentials,
            userSelected = userSelected,
            largeBlobKey = largeBlobKey,
        )
    }

    override fun serialize(encoder: Encoder, value: GetAssertionResponse) {
        throw NotImplementedError("Cannot serialize a GetAssertionResponse")
    }
}
