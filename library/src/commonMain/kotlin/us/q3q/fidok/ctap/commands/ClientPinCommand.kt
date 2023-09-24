package us.q3q.fidok.ctap.commands

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ByteString
import kotlin.jvm.JvmStatic

/**
 * Represents a CTAP `clientPin` subcommand.
 *
 * This class is difficult to create directly, and is best built through the static
 * methods here.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
class ClientPinCommand private constructor(
    val subCommand: UByte,
    val pinUvAuthProtocol: UByte? = null,
    val keyAgreement: COSEKey? = null,
    @ByteString val pinUvAuthParam: ByteArray? = null,
    @ByteString val newPinEnc: ByteArray? = null,
    @ByteString val pinHashEnc: ByteArray? = null,
    val permissions: UByte? = null,
    val rpId: String? = null,
) : CtapCommand() {
    override val cmdByte: Byte = 0x06

    companion object {

        @JvmStatic
        fun getPINRetries(pinUvAuthProtocol: UByte): ClientPinCommand {
            return ClientPinCommand(subCommand = 0x01u, pinUvAuthProtocol = pinUvAuthProtocol)
        }

        @JvmStatic
        fun getKeyAgreement(pinUvAuthProtocol: UByte): ClientPinCommand {
            return ClientPinCommand(subCommand = 0x02u, pinUvAuthProtocol = pinUvAuthProtocol)
        }

        @JvmStatic
        fun setPIN(
            pinUvAuthProtocol: UByte,
            keyAgreement: COSEKey,
            newPinEnc: ByteArray,
            pinUvAuthParam: ByteArray,
        ): ClientPinCommand {
            return ClientPinCommand(
                subCommand = 0x03u,
                pinUvAuthProtocol = pinUvAuthProtocol,
                keyAgreement = keyAgreement,
                newPinEnc = newPinEnc,
                pinUvAuthParam = pinUvAuthParam,
            )
        }

        @JvmStatic
        fun changePIN(
            pinUvAuthProtocol: UByte,
            keyAgreement: COSEKey,
            pinHashEnc: ByteArray,
            newPinEnc: ByteArray,
            pinUvAuthParam: ByteArray,
        ): ClientPinCommand {
            return ClientPinCommand(
                subCommand = 0x04u,
                pinUvAuthProtocol = pinUvAuthProtocol,
                keyAgreement = keyAgreement,
                pinHashEnc = pinHashEnc,
                newPinEnc = newPinEnc,
                pinUvAuthParam = pinUvAuthParam,
            )
        }

        @JvmStatic
        fun getPinToken(pinUvAuthProtocol: UByte, keyAgreement: COSEKey, pinHashEnc: ByteArray): ClientPinCommand {
            return ClientPinCommand(
                subCommand = 0x05u,
                pinUvAuthProtocol = pinUvAuthProtocol,
                keyAgreement = keyAgreement,
                pinHashEnc = pinHashEnc,
            )
        }

        @JvmStatic
        fun getPinUvAuthTokenUsingUvWithPermissions(
            pinUvAuthProtocol: UByte,
            keyAgreement: COSEKey,
            permissions: UByte,
            rpId: String?,
        ): ClientPinCommand {
            return ClientPinCommand(
                subCommand = 0x06u,
                pinUvAuthProtocol = pinUvAuthProtocol,
                keyAgreement = keyAgreement,
                permissions = permissions,
                rpId = rpId,
            )
        }

        @JvmStatic
        fun getUVRetries(): ClientPinCommand {
            return ClientPinCommand(subCommand = 0x07u)
        }

        @JvmStatic
        fun getPinUvAuthTokenUsingPinWithPermissions(
            pinUvAuthProtocol: UByte,
            keyAgreement: COSEKey,
            pinHashEnc: ByteArray,
            permissions: UByte,
            rpId: String?,
        ): ClientPinCommand {
            return ClientPinCommand(
                subCommand = 0x09u,
                pinUvAuthProtocol = pinUvAuthProtocol,
                keyAgreement = keyAgreement,
                pinHashEnc = pinHashEnc,
                permissions = permissions,
                rpId = rpId,
            )
        }
    }

    override val params = HashMap<UByte, ParameterValue>().apply {
        if (pinUvAuthProtocol != null) {
            this[0x01u] = UByteParameter(pinUvAuthProtocol)
        }
        this[0x02u] = UByteParameter(subCommand)
        if (keyAgreement != null) {
            this[0x03u] = COSEKeyParameter(keyAgreement)
        }
        if (pinUvAuthParam != null) {
            this[0x04u] = ByteArrayParameter(pinUvAuthParam)
        }
        if (newPinEnc != null) {
            this[0x05u] = ByteArrayParameter(newPinEnc)
        }
        if (pinHashEnc != null) {
            this[0x06u] = ByteArrayParameter(pinHashEnc)
        }
        if (permissions != null) {
            this[0x09u] = UByteParameter(permissions)
        }
        if (rpId != null) {
            this[0x0Au] = StringParameter(rpId)
        }
    }
}

@Serializable
data class COSEKeyParameter(override val v: COSEKey) : ParameterValue()
