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
@Serializable(with = EnumerateCredentialsResponseSerializer::class)
data class EnumerateCredentialsResponse(
    val user: PublicKeyCredentialUserEntity,
    val credentialID: PublicKeyCredentialDescriptor,
    val publicKey: COSEKey,
    val totalCredentials: UInt? = null,
    val credProtect: UByte? = null,
    @ByteString val largeBlobKey: ByteArray? = null,
)

class EnumerateCredentialsResponseSerializer : KSerializer<EnumerateCredentialsResponse> {
    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("EnumerateRPsResponse") {
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

    override fun serialize(encoder: Encoder, value: EnumerateCredentialsResponse) {
        throw NotImplementedError("Cannot serialize a response")
    }
}
