package us.q3q.fidok.ctap.commands

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ByteString
import kotlin.jvm.JvmStatic

/**
 * Represents a CTAP `clientPin` subcommand.
 *
 * This class is difficult to create directly, and is best built through the static
 * methods here, or used from a [CTAPClient][us.q3q.fidok.ctap.CTAPClient].
 *
 * @property subCommand The CTAP number representing *which* `clientPin` command is being invoked
 * @property pinUvAuthProtocol The CTAP number for the PIN/UV auth protocol in use, if any
 * @property keyAgreement The Platform's public key, to be used for ECDH-secured communication
 * @property newPinEnc A new PIN, encrypted using the Authenticator-Platform secret
 * @property pinHashEnc (part of) the hash of an existing PIN, encrypted using the Authenticator-Platform secret
 * @property permissions A bitfield of the permissions being requested for a new PIN/UV token
 * @property rpId The identifier of a FIDO Relying Party, for requesting a PIN/UV token for that RP only
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

        /**
         * Get a command for knowing how many times a PIN can be retried before locking the Authenticator
         *
         * @return A [CtapCommand] object representing a request for PIN retries
         *
         * @see ClientPinGetRetriesResponse
         */
        @JvmStatic
        fun getPINRetries(): ClientPinCommand {
            return ClientPinCommand(subCommand = 0x01u)
        }

        /**
         * Get a command to retrieve the Authenticator's public key for an ECDH exchange
         *
         * @param pinUvAuthProtocol See [ClientPinCommand.pinUvAuthProtocol]
         * @return A [CtapCommand] object representing a Key Agreement request
         *
         * @see ClientPinGetKeyAgreementResponse
         */
        @JvmStatic
        fun getKeyAgreement(pinUvAuthProtocol: UByte): ClientPinCommand {
            return ClientPinCommand(subCommand = 0x02u, pinUvAuthProtocol = pinUvAuthProtocol)
        }

        /**
         * Get a command to set a PIN.
         *
         * This may only be used when there is *not* already a PIN set on the Authenticator.
         * This doesn't usually return anything (other than, potentially, an error)
         *
         * @param pinUvAuthProtocol See [ClientPinCommand.pinUvAuthProtocol]
         * @param keyAgreement See [ClientPinCommand.keyAgreement]
         * @param newPinEnc See [ClientPinCommand.newPinEnc]
         * @param pinUvAuthParam See [ClientPinCommand.pinUvAuthParam]
         * @return A [CtapCommand] object representing a set-PIN request
         */
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

        /**
         * Get a command to change an existing PIN.
         *
         * This may only be used where the Authenticator already has a PIN set.
         * This doesn't usually return anything (other than, potentially, an error)
         *
         * @param pinUvAuthProtocol See [ClientPinCommand.pinUvAuthProtocol]
         * @param keyAgreement See [ClientPinCommand.keyAgreement]
         * @param pinHashEnc See [ClientPinCommand.pinHashEnc]
         * @param newPinEnc See [ClientPinCommand.newPinEnc]
         * @param pinUvAuthParam See [ClientPinCommand.pinUvAuthParam]
         * @return A [CtapCommand] object representing a change-PIN request
         */
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

        /**
         * Gets the CTAP2.0 command to get a PIN/UV token (although in CTAP2.0 it was just a PIN token!). You
         * should use [getPinUvAuthTokenUsingPinWithPermissions] instead for more recent, safer Authenticators.
         *
         * @param pinUvAuthProtocol See [ClientPinCommand.pinUvAuthProtocol]
         * @param keyAgreement See [ClientPinCommand.keyAgreement]
         * @param pinHashEnc See [ClientPinCommand.pinHashEnc]
         *
         * @return A [CtapCommand] object representing a legacy get-PIN-token request
         *
         * @see [ClientPinGetTokenResponse]
         */
        @JvmStatic
        fun getPinToken(pinUvAuthProtocol: UByte, keyAgreement: COSEKey, pinHashEnc: ByteArray): ClientPinCommand {
            return ClientPinCommand(
                subCommand = 0x05u,
                pinUvAuthProtocol = pinUvAuthProtocol,
                keyAgreement = keyAgreement,
                pinHashEnc = pinHashEnc,
            )
        }

        /**
         * Gets a command to get a PIN/UV token via an Authenticator's onboard User Verification method.
         *
         * @param pinUvAuthProtocol See [ClientPinCommand.pinUvAuthProtocol]
         * @param keyAgreement See [ClientPinCommand.keyAgreement]
         * @param permissions See [ClientPinCommand.permissions]
         * @param rpId See [ClientPinCommand.rpId]
         *
         * @return A [CtapCommand] object representing a get-PIN-token request
         *
         * @see [ClientPinGetTokenResponse]
         */
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

        /**
         * Get a command for knowing how many times an Authenticator's onboard UV can be retried
         * before disabling itself, potentially restricting the Authenticator to only allowing
         * PINs or even locking it entirely.
         *
         * @return A [CtapCommand] object representing a request for UV retries
         *
         * @see ClientPinUvRetriesResponse
         */
        @JvmStatic
        fun getUVRetries(): ClientPinCommand {
            return ClientPinCommand(subCommand = 0x07u)
        }

        /**
         * Gets a command to get a PIN/UV token using a user's PIN.
         *
         * @param pinUvAuthProtocol See [ClientPinCommand.pinUvAuthProtocol]
         * @param keyAgreement See [ClientPinCommand.keyAgreement]
         * @param pinHashEnc See [ClientPinCommand.pinHashEnc]
         * @param permissions See [ClientPinCommand.permissions]
         * @param rpId See [ClientPinCommand.rpId]
         *
         * @return A [CtapCommand] object representing a get-PIN-token request
         *
         * @see [ClientPinGetTokenResponse]
         */
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
