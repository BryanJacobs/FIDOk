/**
 * CaBLE includes a facility for QR codes to communication initial keying material
 * for forming the bond between Authenticator and Platform.
 *
 * This file contains code for creating and decoding those codes.
 */

package us.q3q.fidok.cable

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
import us.q3q.fidok.ctap.commands.BooleanParameter
import us.q3q.fidok.ctap.commands.ByteArrayParameter
import us.q3q.fidok.ctap.commands.ParameterValue
import us.q3q.fidok.ctap.commands.StringParameter
import us.q3q.fidok.ctap.commands.UByteParameter
import us.q3q.fidok.ctap.commands.ULongParameter

/**
 * A hint about what type of operation is going to follow the CaBLE initial handshake.
 *
 * This is just a HINT, and not a mandate.
 */
enum class OperationHint(val v: String) {
    /**
     * The command following the handshake is more likely to be a CTAP `makeCredential`
     */
    MAKE_CREDENTIAL("mc"),

    /**
     * The command following the handshake is more likely to be a CTAP `getAssertion`
     */
    GET_ASSERTION("ga"),
}

/**
 * This class represents the data inside a CaBLE code, generally encoded as a QRCode image.
 *
 * @property publicKey The public key used by the side of the exchange generating the code
 *                     (usually the platform). Should be 33 bytes of X9.62-encoded compressed
 *                     public key from the secp256r1 (NIST P-256) elliptic curve
 * @property secret The "secret" used for the CaBLE exchange. Not a private key, just 16 bytes
 *                  of randomness
 * @property knownTunnelServerDomains How many fixed-assignment tunnel server domains the
 *                                    side of the exchange generating the code is aware of
 * @property currentEpochSeconds The "current" time, as the number of seconds from the UNIX epoch,
 *                               to write into the code. This allows the code to be rejected if too
 *                               old
 * @property canPerformStateAssist True if the side of the exchange generating the code is capable
 *                                 of making use of persistent state from an initial CaBLE handshake,
 *                                 and thus skipping the reconnection on future communication from the
 *                                 same remote side
 * @property operationHint A hint (ignorable) about what type of operation is likely to follow the CaBLE
 *                         handshake
 * @constructor Creates a new CaBLE code, ready for serialization
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable(with = CaBLECodeSerializer::class)
data class CaBLECode(
    @ByteString val publicKey: ByteArray,
    @ByteString val secret: ByteArray,
    val knownTunnelServerDomains: UByte = CaBLESupport.KNOWN_TUNNEL_SERVER_DOMAINS,
    val currentEpochSeconds: ULong? = null,
    val canPerformStateAssist: Boolean? = null,
    val operationHint: OperationHint = OperationHint.GET_ASSERTION,
) {
    init {
        require(publicKey.size == 33)
        require(secret.size == 16)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as CaBLECode

        if (!publicKey.contentEquals(other.publicKey)) return false
        if (!secret.contentEquals(other.secret)) return false
        if (knownTunnelServerDomains != other.knownTunnelServerDomains) return false
        if (currentEpochSeconds != other.currentEpochSeconds) return false
        if (canPerformStateAssist != other.canPerformStateAssist) return false
        if (operationHint != other.operationHint) return false

        return true
    }

    override fun hashCode(): Int {
        var result = publicKey.contentHashCode()
        result = 31 * result + secret.contentHashCode()
        result = 31 * result + knownTunnelServerDomains.hashCode()
        result = 31 * result + (currentEpochSeconds?.hashCode() ?: 0)
        result = 31 * result + (canPerformStateAssist?.hashCode() ?: 0)
        result = 31 * result + operationHint.hashCode()
        return result
    }
}

/**
 * Encodes/decodes a CaBLE Code to/from standards-compliant CBOR, when used with
 * the right [encoder][us.q3q.fidok.ctap.commands.CTAPCBOREncoder] or
 * [decoder][us.q3q.fidok.ctap.commands.CTAPCBORDecoder].
 */
class CaBLECodeSerializer : KSerializer<CaBLECode> {
    override val descriptor: SerialDescriptor
        get() =
            buildClassSerialDescriptor("CaBLECode") {
                element("publicKey", ByteArraySerializer().descriptor)
                element("secret", ByteArraySerializer().descriptor)
                element("knownTunnelServerDomains", UByte.serializer().descriptor)
                element("currentEpochSeconds", Int.serializer().descriptor, isOptional = true)
                element("canPerformStateAssist", Boolean.serializer().descriptor, isOptional = true)
                element("operationHint", String.serializer().descriptor)
            }

    override fun deserialize(decoder: Decoder): CaBLECode {
        val composite = decoder.beginStructure(descriptor)
        val numParams = composite.decodeCollectionSize(descriptor)

        var publicKey: ByteArray? = null
        var secret: ByteArray? = null
        var knownTunnelServerDomains: UByte? = null
        var currentEpochSeconds: ULong? = null
        var canPerformStateAssist: Boolean? = null
        var operationHint: OperationHint? = null

        for (i in 1..numParams) {
            val idx = composite.decodeElementIndex(descriptor)
            when (idx) {
                0x00 -> {
                    publicKey = composite.decodeSerializableElement(descriptor, idx, ByteArraySerializer())
                }
                0x01 -> {
                    secret = composite.decodeSerializableElement(descriptor, idx, ByteArraySerializer())
                }
                0x02 -> {
                    knownTunnelServerDomains = composite.decodeIntElement(descriptor, idx).toUByte()
                }
                0x03 -> {
                    currentEpochSeconds = composite.decodeLongElement(descriptor, idx).toULong()
                }
                0x04 -> {
                    canPerformStateAssist = composite.decodeBooleanElement(descriptor, idx)
                }
                0x05 -> {
                    val hintStr = composite.decodeStringElement(descriptor, idx)
                    operationHint = OperationHint.entries.find { it.v == hintStr } // Ignore unknown hints
                }
                else -> {
                    // Ignore - unknown property
                }
            }
        }

        if (publicKey == null || secret == null || knownTunnelServerDomains == null) {
            throw SerializationException("CaBLECode is missing required properties")
        }
        if (operationHint == null) {
            operationHint = OperationHint.GET_ASSERTION
        }

        composite.endStructure(descriptor)

        return CaBLECode(
            publicKey = publicKey,
            secret = secret,
            knownTunnelServerDomains = knownTunnelServerDomains,
            currentEpochSeconds = currentEpochSeconds,
            canPerformStateAssist = canPerformStateAssist,
            operationHint = operationHint,
        )
    }

    override fun serialize(
        encoder: Encoder,
        value: CaBLECode,
    ) {
        val params =
            hashMapOf(
                0x00.toUByte() to ByteArrayParameter(value.publicKey),
                0x01.toUByte() to ByteArrayParameter(value.secret),
                0x02.toUByte() to UByteParameter(value.knownTunnelServerDomains),
                0x05.toUByte() to StringParameter(value.operationHint.v),
            )
        if (value.currentEpochSeconds != null) {
            params[0x03.toUByte()] = ULongParameter(value.currentEpochSeconds)
        }
        if (value.canPerformStateAssist != null) {
            params[0x04.toUByte()] = BooleanParameter(value.canPerformStateAssist)
        }
        encoder.encodeSerializableValue(
            MapSerializer(UByte.serializer(), ParameterValue.serializer()),
            params,
        )
    }
}
