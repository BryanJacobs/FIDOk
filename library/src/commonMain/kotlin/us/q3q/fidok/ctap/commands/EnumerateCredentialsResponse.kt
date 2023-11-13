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
 * One response to a credential enumeration request - represents a single credential.
 *
 * Could be returned by [beginning][CredentialManagementCommand.enumerateCredentialsBegin] or
 * [continuing][CredentialManagementCommand.enumerateCredentialsGetNextCredential] CTAP
 * credentials enumeration.
 *
 * @property user The user associated with this particular credential
 * @property credentialID The credential's handle for use in [getting assertions][us.q3q.fidok.ctap.CTAPClient.getAssertions]
 * @property publicKey The public key associated with the credential, for verifying assertions
 * @property totalCredentials The number of credentials to be returned by one round of iteration.
 *                            Will only be set on the first response, not on GetNextCredential calls.
 * @property credProtect The credential protection level associated with this credential
 * @property largeBlobKey A key used for accessing the [large blob array][LargeBlobKeyExtension]
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable(with = EnumerateCredentialsResponseSerializer::class)
data class EnumerateCredentialsResponse(
    val user: PublicKeyCredentialUserEntity,
    val credentialID: PublicKeyCredentialDescriptor,
    val publicKey: COSEKey,
    val totalCredentials: UInt? = null,
    val credProtect: UByte? = null,
    @ByteString val largeBlobKey: ByteArray? = null,
) {
    init {
        require(largeBlobKey == null || largeBlobKey.size == 32)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as EnumerateCredentialsResponse

        if (user != other.user) return false
        if (credentialID != other.credentialID) return false
        if (publicKey != other.publicKey) return false
        if (totalCredentials != other.totalCredentials) return false
        if (credProtect != other.credProtect) return false
        if (largeBlobKey != null) {
            if (other.largeBlobKey == null) return false
            if (!largeBlobKey.contentEquals(other.largeBlobKey)) return false
        } else if (other.largeBlobKey != null) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = user.hashCode()
        result = 31 * result + credentialID.hashCode()
        result = 31 * result + publicKey.hashCode()
        result = 31 * result + (totalCredentials?.hashCode() ?: 0)
        result = 31 * result + (credProtect?.hashCode() ?: 0)
        result = 31 * result + (largeBlobKey?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * Deserializes an [EnumerateCredentialsResponse]
 */
class EnumerateCredentialsResponseSerializer : KSerializer<EnumerateCredentialsResponse> {
    override val descriptor: SerialDescriptor
        get() =
            buildClassSerialDescriptor("EnumerateRPsResponse") {
                element("user", PublicKeyCredentialUserEntity.serializer().descriptor)
                element("credentialID", ByteArraySerializer().descriptor)
                element("publicKey", COSEKey.serializer().descriptor)
                element("totalCredentials", UInt.serializer().descriptor, isOptional = true)
                element("credProtect", UByte.serializer().descriptor, isOptional = true)
                element("largeBlobKey", ByteArraySerializer().descriptor, isOptional = true)
            }

    override fun deserialize(decoder: Decoder): EnumerateCredentialsResponse {
        val composite = decoder.beginStructure(descriptor)
        val numElements = composite.decodeCollectionSize(descriptor)
        if (numElements > 6 || numElements < 3) {
            throw SerializationException("Incorrect element count in EnumerateCredentialsResponse: $numElements")
        }

        var user: PublicKeyCredentialUserEntity? = null
        var credentialID: PublicKeyCredentialDescriptor? = null
        var publicKey: COSEKey? = null
        var totalCredentials: UInt? = null
        var credProtect: UByte? = null
        var largeBlobKey: ByteArray? = null

        for (i in 1..numElements) {
            when (val idx = composite.decodeElementIndex(descriptor)) {
                0x06 ->
                    user = composite.decodeSerializableElement(descriptor, 0, PublicKeyCredentialUserEntity.serializer())
                0x07 ->
                    credentialID = composite.decodeSerializableElement(descriptor, 1, PublicKeyCredentialDescriptor.serializer())
                0x08 ->
                    publicKey = composite.decodeSerializableElement(descriptor, 2, COSEKey.serializer())
                0x09 ->
                    totalCredentials = composite.decodeIntElement(descriptor, 3).toUInt()
                0x0A ->
                    credProtect = composite.decodeIntElement(descriptor, 4).toUByte()
                0x0B ->
                    largeBlobKey = composite.decodeSerializableElement(descriptor, 5, ByteArraySerializer())
                else ->
                    throw SerializationException("Unknown element index in EnumerateCredentialsResponse: $idx")
            }
        }

        composite.endStructure(descriptor)

        if (user == null || credentialID == null || publicKey == null) {
            throw SerializationException("Missing attribute in EnumerateCredentialsResponse")
        }

        return EnumerateCredentialsResponse(
            user = user,
            credentialID = credentialID,
            publicKey = publicKey,
            totalCredentials = totalCredentials,
            credProtect = credProtect,
            largeBlobKey = largeBlobKey,
        )
    }

    override fun serialize(
        encoder: Encoder,
        value: EnumerateCredentialsResponse,
    ) {
        throw NotImplementedError("Cannot serialize a response")
    }
}
