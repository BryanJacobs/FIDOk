package us.q3q.fidok.ctap.commands

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = CredentialManagementGetMetadataResponseSerializer::class)
data class CredentialManagementGetMetadataResponse(
    val existingResidentCredentialsCount: UInt,
    val maxPossibleRemainingCredentialsCount: UInt,
)

class CredentialManagementGetMetadataResponseSerializer : KSerializer<CredentialManagementGetMetadataResponse> {
    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("CredentialManagementGetMetadataResponse") {
            element("existingResidentCredentialsCount", UInt.serializer().descriptor)
            element("maxPossibleRemainingCredentialsCount", UInt.serializer().descriptor)
        }

    override fun deserialize(decoder: Decoder): CredentialManagementGetMetadataResponse {
        val composite = decoder.beginStructure(descriptor)
        val numItems = composite.decodeCollectionSize(descriptor)
        if (numItems != 2) {
            throw RuntimeException("CredentialManagementGetMetadataResponse had incorrect number of items in it: $numItems")
        }
        val param = composite.decodeIntElement(descriptor, 0)
        if (param != 0x01) {
            throw RuntimeException("CredentialManagementGetMetadataResponse had param $param instead of 0x01")
        }
        val existingResidentCredentialsCount = composite.decodeIntElement(descriptor, 0).toUInt()
        val nextParam = composite.decodeIntElement(descriptor, 1)
        if (nextParam != 0x02) {
            throw RuntimeException("CredentialManagementGetMetadataResponse had param $nextParam instead of 0x02")
        }
        val maxPossibleRemainingCredentialsCount = composite.decodeIntElement(descriptor, 1).toUInt()

        composite.endStructure(descriptor)
        return CredentialManagementGetMetadataResponse(
            existingResidentCredentialsCount = existingResidentCredentialsCount,
            maxPossibleRemainingCredentialsCount = maxPossibleRemainingCredentialsCount,
        )
    }

    override fun serialize(encoder: Encoder, value: CredentialManagementGetMetadataResponse) {
        throw NotImplementedError("Cannot serialize a response")
    }
}
