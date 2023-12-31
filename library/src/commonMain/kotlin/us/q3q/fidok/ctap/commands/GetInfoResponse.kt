package us.q3q.fidok.ctap.commands

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ArraySerializer
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Represents the response to a [GetInfoCommand], describing the status and capabilities of an Authenticator.
 *
 * @property versions The CTAP specification version(s) supported by the Authenticator
 * @property extensions Any CTAP/Webauthn extension(s) supported
 * @property aaguid A unique identifier for the Authenticator MODEL - not the individual physical Authenticator.
 *                  May be all zeroes if Basic attestation is unsupported
 * @property options A map, with keys being a [option identifiers][us.q3q.fidok.ctap.CTAPOption] and values being
 *                   whether that option is supported/enabled or not
 * @property maxMsgSize The size, in bytes, of the longest allowable single command to the Authenticator
 * @property pinUvAuthProtocols Which PIN/UV authentication protocol(s) the Authenticator supports
 * @property maxCredentialCountInList How many Credential IDs may be passed to the Authenticator in a single allowList
 * @property maxCredentialIdLength The length, in bytes, of the longest Credential this Authenticator will produce
 *                                 (and thus the longest one it will successfully use as well)
 * @property transports The different [transports][us.q3q.fidok.ctap.AuthenticatorTransport] by which the
 *                      Authenticator may be connected to the Platform
 * @property algorithms Which cryptographic algorithm(s) the Authenticator can use in generating Credentials
 * @property maxSerializedLargeBlobArray How long the large-blob store for the Authenticator can be, in bytes
 * @property forcePINChange If true, the user's PIN must be changed prior to creating or using Credentials
 * @property minPINLength How long, in UTF-8 code points, the user's PIN must be at minimum
 * @property firmwareVersion A vendor-specific version number for the Authenticator
 * @property maxCredBlobLength How long a single [credBlob][CredBlobExtension] may be.
 * @property maxRPIDsForSetMinPINLength How many Relying Party identifiers may be passed to the Authenticator for
 * [the setMinPINLength extension][us.q3q.fidok.ctap.AuthenticatorConfigClient.setMinPINLength]
 * @property preferredPlatformUvAttempts How many times the Authenticator would prefer the Platform attempt on-board
 * User Verification before falling back to trying a PIN (or failing user verification entirely)
 * @property certifications Any certifications the Authenticator has obtained, such as FIDO Alliance or CC-EAL. Keys in
 * the map are certification types, and values are certification-type-specific integers denoting levels
 * @property remainingDiscoverableCredentials APPROXIMATELY how many Discoverable Credentials can be created in the
 * remaining space of the Authenticator. May be an underestimate. If circumstances change, may end up being an
 * overestimate.
 * @property vendorPrototypeConfigCommands Any vendor-specific [config commands][AuthenticatorConfigCommand.subCommand]
 * supported by the Authenticator
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable(with = GetInfoResponseSerializer::class)
data class GetInfoResponse(
    val versions: Array<String>,
    val extensions: Array<String>? = null,
    @ByteString val aaguid: ByteArray,
    val options: Map<String, Boolean>? = null,
    val maxMsgSize: UInt? = null,
    val pinUvAuthProtocols: Array<UInt>? = null,
    val maxCredentialCountInList: UInt? = null,
    val maxCredentialIdLength: UInt? = null,
    val transports: Array<String>? = null,
    val algorithms: Array<PublicKeyCredentialParameters>? = null,
    val maxSerializedLargeBlobArray: UInt? = null,
    val forcePINChange: Boolean? = null,
    val minPINLength: UInt? = null,
    val firmwareVersion: UInt? = null,
    val maxCredBlobLength: UInt? = null,
    val maxRPIDsForSetMinPINLength: UInt? = null,
    val preferredPlatformUvAttempts: UInt? = null,
    val uvModality: UInt? = null,
    val certifications: Map<String, UInt>? = null,
    val remainingDiscoverableCredentials: UInt? = null,
    val vendorPrototypeConfigCommands: Array<UInt>? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as GetInfoResponse

        if (!versions.contentEquals(other.versions)) return false
        if (extensions != null) {
            if (other.extensions == null) return false
            if (!extensions.contentEquals(other.extensions)) return false
        } else if (other.extensions != null) {
            return false
        }
        if (!aaguid.contentEquals(other.aaguid)) return false
        if (options != other.options) return false
        if (maxMsgSize != other.maxMsgSize) return false
        if (pinUvAuthProtocols != null) {
            if (other.pinUvAuthProtocols == null) return false
            if (!pinUvAuthProtocols.contentEquals(other.pinUvAuthProtocols)) return false
        } else if (other.pinUvAuthProtocols != null) {
            return false
        }
        if (maxCredentialCountInList != other.maxCredentialCountInList) return false
        if (maxCredentialIdLength != other.maxCredentialIdLength) return false
        if (transports != null) {
            if (other.transports == null) return false
            if (!transports.contentEquals(other.transports)) return false
        } else if (other.transports != null) {
            return false
        }
        if (algorithms != null) {
            if (other.algorithms == null) return false
            if (!algorithms.contentEquals(other.algorithms)) return false
        } else if (other.algorithms != null) {
            return false
        }
        if (maxSerializedLargeBlobArray != other.maxSerializedLargeBlobArray) return false
        if (forcePINChange != other.forcePINChange) return false
        if (minPINLength != other.minPINLength) return false
        if (firmwareVersion != other.firmwareVersion) return false
        if (maxCredBlobLength != other.maxCredBlobLength) return false
        if (maxRPIDsForSetMinPINLength != other.maxRPIDsForSetMinPINLength) return false
        if (preferredPlatformUvAttempts != other.preferredPlatformUvAttempts) return false
        if (uvModality != other.uvModality) return false
        if (certifications != other.certifications) return false
        if (remainingDiscoverableCredentials != other.remainingDiscoverableCredentials) return false
        if (vendorPrototypeConfigCommands != null) {
            if (other.vendorPrototypeConfigCommands == null) return false
            if (!vendorPrototypeConfigCommands.contentEquals(other.vendorPrototypeConfigCommands)) return false
        } else if (other.vendorPrototypeConfigCommands != null) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = versions.contentHashCode()
        result = 31 * result + (extensions?.contentHashCode() ?: 0)
        result = 31 * result + aaguid.contentHashCode()
        result = 31 * result + (options?.hashCode() ?: 0)
        result = 31 * result + (maxMsgSize?.hashCode() ?: 0)
        result = 31 * result + (pinUvAuthProtocols?.contentHashCode() ?: 0)
        result = 31 * result + (maxCredentialCountInList?.hashCode() ?: 0)
        result = 31 * result + (maxCredentialIdLength?.hashCode() ?: 0)
        result = 31 * result + (transports?.contentHashCode() ?: 0)
        result = 31 * result + (algorithms?.contentHashCode() ?: 0)
        result = 31 * result + (maxSerializedLargeBlobArray?.hashCode() ?: 0)
        result = 31 * result + (forcePINChange?.hashCode() ?: 0)
        result = 31 * result + (minPINLength?.hashCode() ?: 0)
        result = 31 * result + (firmwareVersion?.hashCode() ?: 0)
        result = 31 * result + (maxCredBlobLength?.hashCode() ?: 0)
        result = 31 * result + (maxRPIDsForSetMinPINLength?.hashCode() ?: 0)
        result = 31 * result + (preferredPlatformUvAttempts?.hashCode() ?: 0)
        result = 31 * result + (uvModality?.hashCode() ?: 0)
        result = 31 * result + (certifications?.hashCode() ?: 0)
        result = 31 * result + (remainingDiscoverableCredentials?.hashCode() ?: 0)
        result = 31 * result + (vendorPrototypeConfigCommands?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * Deserializes a [GetInfoResponse]
 */
@OptIn(ExperimentalSerializationApi::class)
class GetInfoResponseSerializer : KSerializer<GetInfoResponse> {
    override val descriptor: SerialDescriptor
        get() =
            buildClassSerialDescriptor("GetInfoResponse") {
                element("versions", ArraySerializer(String.serializer()).descriptor)
                element("extensions", ArraySerializer(String.serializer()).descriptor, isOptional = true)
                element("aaguid", ByteArraySerializer().descriptor)
                element("options", MapSerializer(String.serializer(), Boolean.serializer()).descriptor, isOptional = true)
                element("maxMsgSize", UInt.serializer().descriptor, isOptional = true)
                element("pinUvAuthProtocols", ArraySerializer(UInt.serializer()).descriptor, isOptional = true)
                element("maxCredentialCountInList", UInt.serializer().descriptor, isOptional = true)
                element("transports", ArraySerializer(String.serializer()).descriptor, isOptional = true)
                element("algorithms", ArraySerializer(PublicKeyCredentialDescriptor.serializer()).descriptor, isOptional = true)
                element("maxSerializedLargeBlobArray", UInt.serializer().descriptor, isOptional = true)
                element("forcePINChange", Boolean.serializer().descriptor, isOptional = true)
                element("minPINLength", UInt.serializer().descriptor, isOptional = true)
                element("firmwareVersion", UInt.serializer().descriptor, isOptional = true)
                element("maxCredBlobLength", UInt.serializer().descriptor, isOptional = true)
                element("maxRPIDsForSetMinPINLength", UInt.serializer().descriptor, isOptional = true)
                element("preferredPlatformUvAttempts", UInt.serializer().descriptor, isOptional = true)
                element("uvModality", UInt.serializer().descriptor, isOptional = true)
                element("certifications", MapSerializer(String.serializer(), Unit.serializer()).descriptor, isOptional = true)
                element("remainingDiscoverableCredentials", UInt.serializer().descriptor, isOptional = true)
                element("vendorPrototypeConfigCommands", ArraySerializer(UInt.serializer()).descriptor, isOptional = true)
            }

    private fun deserializeStringArray(
        decoder: CompositeDecoder,
        idx: Int,
    ): Array<String> {
        val ser = ArraySerializer(String.serializer())
        return decoder.decodeSerializableElement(
            ser.descriptor,
            idx - 1,
            ser,
            null,
        )
    }

    override fun deserialize(decoder: Decoder): GetInfoResponse {
        val composite = decoder.beginStructure(descriptor)
        val numParams = composite.decodeCollectionSize(descriptor)

        var versions: Array<String> = arrayOf()
        var extensions: Array<String>? = null
        var aaguid: ByteArray = byteArrayOf()
        var options: Map<String, Boolean>? = null
        var maxMsgSize: UInt? = null
        var pinUvAuthProtocols: Array<UInt>? = null
        var maxCredentialCountInList: UInt? = null
        var maxCredentialIdLength: UInt? = null
        var transports: Array<String>? = null
        var algorithms: Array<PublicKeyCredentialParameters>? = null
        var maxSerializedLargeBlobArray: UInt? = null
        var forcePINChange: Boolean? = null
        var minPINLength: UInt? = null
        var firmwareVersion: UInt? = null
        var maxCredBlobLength: UInt? = null
        var maxRPIDsForSetMinPINLength: UInt? = null
        var preferredPlatformUvAttempts: UInt? = null
        var uvModality: UInt? = null
        var certifications: Map<String, UInt>? = null
        var remainingDiscoverableCredentials: UInt? = null
        var vendorPrototypeConfigCommands: Array<UInt>? = null

        for (i in 1..numParams) {
            val idx = composite.decodeElementIndex(descriptor)
            when (idx) {
                0x01 -> {
                    versions = deserializeStringArray(composite, idx)
                }
                0x02 -> {
                    extensions = deserializeStringArray(composite, idx)
                }
                0x03 -> {
                    aaguid =
                        composite.decodeSerializableElement(
                            ByteArraySerializer().descriptor,
                            idx - 1,
                            ByteArraySerializer(),
                            null,
                        )
                }
                0x04 -> {
                    options =
                        composite.decodeSerializableElement(
                            descriptor.getElementDescriptor(idx - 1),
                            idx - 1,
                            MapSerializer(
                                String.serializer(),
                                Boolean.serializer(),
                            ),
                            null,
                        )
                }
                0x05 -> {
                    maxMsgSize = composite.decodeIntElement(descriptor, idx - 1).toUInt()
                }
                0x06 -> {
                    pinUvAuthProtocols =
                        composite.decodeSerializableElement(
                            ArraySerializer(UInt.serializer()).descriptor,
                            idx - 1,
                            ArraySerializer(UInt.serializer()),
                            null,
                        )
                }
                0x07 -> {
                    maxCredentialCountInList = composite.decodeIntElement(descriptor, idx - 1).toUInt()
                }
                0x08 -> {
                    maxCredentialIdLength = composite.decodeIntElement(descriptor, idx - 1).toUInt()
                }
                0x09 -> {
                    transports = deserializeStringArray(composite, idx)
                }
                0x0A -> {
                    algorithms =
                        composite.decodeSerializableElement(
                            ArraySerializer(PublicKeyCredentialParameters.serializer()).descriptor,
                            idx - 1,
                            ArraySerializer(PublicKeyCredentialParameters.serializer()),
                            null,
                        )
                }
                0x0B -> {
                    maxSerializedLargeBlobArray = composite.decodeIntElement(descriptor, idx - 1).toUInt()
                }
                0x0C -> {
                    forcePINChange = composite.decodeBooleanElement(descriptor, idx - 1)
                }
                0x0D -> {
                    minPINLength = composite.decodeIntElement(descriptor, idx - 1).toUInt()
                }
                0x0E -> {
                    firmwareVersion = composite.decodeIntElement(descriptor, idx - 1).toUInt()
                }
                0x0F -> {
                    maxCredBlobLength = composite.decodeIntElement(descriptor, idx - 1).toUInt()
                }
                0x10 -> {
                    maxRPIDsForSetMinPINLength = composite.decodeIntElement(descriptor, idx - 1).toUInt()
                }
                0x11 -> {
                    preferredPlatformUvAttempts = composite.decodeIntElement(descriptor, idx - 1).toUInt()
                }
                0x12 -> {
                    uvModality = composite.decodeIntElement(descriptor, idx - 1).toUInt()
                }
                0x13 -> {
                    certifications =
                        composite.decodeSerializableElement(
                            descriptor.getElementDescriptor(idx - 1),
                            idx - 1,
                            MapSerializer(
                                String.serializer(),
                                UInt.serializer(),
                            ),
                            null,
                        )
                }
                0x14 -> {
                    remainingDiscoverableCredentials = composite.decodeIntElement(descriptor, idx - 1).toUInt()
                }
                0x15 -> {
                    vendorPrototypeConfigCommands =
                        composite.decodeSerializableElement(
                            ArraySerializer(UInt.serializer()).descriptor,
                            idx - 1,
                            ArraySerializer(UInt.serializer()),
                            null,
                        )
                }
                else -> {
                    // Ignore - unknown element
                }
            }
        }
        composite.endStructure(descriptor)

        return GetInfoResponse(
            versions = versions,
            extensions = extensions,
            aaguid = aaguid,
            options = options,
            maxMsgSize = maxMsgSize,
            pinUvAuthProtocols = pinUvAuthProtocols,
            maxCredentialCountInList = maxCredentialCountInList,
            maxCredentialIdLength = maxCredentialIdLength,
            transports, algorithms, maxSerializedLargeBlobArray,
            forcePINChange, minPINLength, firmwareVersion, maxCredBlobLength,
            maxRPIDsForSetMinPINLength, preferredPlatformUvAttempts, uvModality,
            certifications, remainingDiscoverableCredentials, vendorPrototypeConfigCommands,
        )
    }

    override fun serialize(
        encoder: Encoder,
        value: GetInfoResponse,
    ) {
        throw NotImplementedError()
    }
}
