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

/**
 * Represents one Authenticator response to a [GetAssertionCommand].
 *
 * @property credential The Credential to which this Assertion pertains
 * @property authData The portion of the Authenticator's response that is signed - authenticated
 * @property signature The Authenticator's returned signature. Meaning depends on the cryptographic algorithm in use
 * @property user The User associated with this Credential. Per the CTAP standards, may be null unless the Credential
 *                is Discoverable, and even for Discoverable Credentials doesn't necessarily contain fields other than
 *                the `id`.
 * @property numberOfCredentials How many Credentials matched the [GetAssertionCommand]. Will be set only on the first
 *                               response.
 * @property userSelected Whether the Credential being returned was explicitly chosen by the user, through some sort of
 *                        user interface built into the Authenticator.
 * @property largeBlobKey For reasons that don't appear to be extremely sane and likely relate to government regulatory
 * certifications, the [LargeBlobKeyExtension]'s return value is outside the [AuthenticatorData.extensions] and is
 * instead its own field in the [GetAssertionResponse]. This contains that value, and may be used for
 * encrypting/decrypting the portion of the Large Blob Array that pertains to this Credential
 */
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
        } else if (other.largeBlobKey != null) {
            return false
        }

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

/**
 * Deserializes a [GetAssertionResponse].
 */
class GetAssertionResponseSerializer : KSerializer<GetAssertionResponse> {
    override val descriptor: SerialDescriptor
        get() =
            buildClassSerialDescriptor("GetAssertionResponse") {
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

    override fun serialize(
        encoder: Encoder,
        value: GetAssertionResponse,
    ) {
        throw NotImplementedError("Cannot serialize a GetAssertionResponse")
    }
}
