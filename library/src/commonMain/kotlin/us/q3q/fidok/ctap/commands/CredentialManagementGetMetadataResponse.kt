package us.q3q.fidok.ctap.commands

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Represents the response to a [getCredsMetadata][CredentialManagementCommand.getCredsMetadata] operation.
 *
 * @property existingDiscoverableCredentialsCount The number of discoverable credentials present on the device
 * @property maxPossibleRemainingCredentialsCount The APPROXIMATE number of discoverable credentials that could
 *                                                be created, assuming each one uses maximal storage space
 */
@Serializable(with = CredentialManagementGetMetadataResponseSerializer::class)
data class CredentialManagementGetMetadataResponse(
    val existingDiscoverableCredentialsCount: UInt,
    val maxPossibleRemainingCredentialsCount: UInt,
)

/**
 * Deserializes a [CredentialManagementGetMetadataResponse]
 */
class CredentialManagementGetMetadataResponseSerializer : KSerializer<CredentialManagementGetMetadataResponse> {
    override val descriptor: SerialDescriptor
        get() =
            buildClassSerialDescriptor("CredentialManagementGetMetadataResponse") {
                element("existingDiscoverableCredentialsCount", UInt.serializer().descriptor)
                element("maxPossibleRemainingCredentialsCount", UInt.serializer().descriptor)
            }

    override fun deserialize(decoder: Decoder): CredentialManagementGetMetadataResponse {
        val composite = decoder.beginStructure(descriptor)
        val numItems = composite.decodeCollectionSize(descriptor)
        if (numItems != 2) {
            throw SerializationException("CredentialManagementGetMetadataResponse had incorrect number of items in it: $numItems")
        }
        val param = composite.decodeIntElement(descriptor, 0)
        if (param != 0x01) {
            throw SerializationException("CredentialManagementGetMetadataResponse had param $param instead of 0x01")
        }
        val existingDiscoverableCredentialsCount = composite.decodeIntElement(descriptor, 0).toUInt()
        val nextParam = composite.decodeIntElement(descriptor, 1)
        if (nextParam != 0x02) {
            throw SerializationException("CredentialManagementGetMetadataResponse had param $nextParam instead of 0x02")
        }
        val maxPossibleRemainingCredentialsCount = composite.decodeIntElement(descriptor, 1).toUInt()

        composite.endStructure(descriptor)
        return CredentialManagementGetMetadataResponse(
            existingDiscoverableCredentialsCount = existingDiscoverableCredentialsCount,
            maxPossibleRemainingCredentialsCount = maxPossibleRemainingCredentialsCount,
        )
    }

    override fun serialize(
        encoder: Encoder,
        value: CredentialManagementGetMetadataResponse,
    ) {
        throw NotImplementedError("Cannot serialize a response")
    }
}
