package us.q3q.fidok.ctap.commands

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.jvm.JvmStatic

/**
 * Represents a command to manage discoverable credentials on an Authenticator.
 *
 * This is best created via [us.q3q.fidok.ctap.CTAPClient.credentialManagement] or the
 * static methods provided.
 *
 * @param subCommand The particular type of credential management operation
 * @param pinUvAuthProtocol CTAP PIN/UV protocol identifier
 * @param pinUvAuthParam Authentication of sent data using a PIN/UV token - depends on the subcommand
 * @param subCommandParams Parameters for a particular subcommand
 * @param ctap21Implementation If true, use the CTAP2.1-compatible version of the commands herein;
 *                             if false, the prerelease versions will be used instead. The different
 *                             versions work the same way
 */
@Serializable
class CredentialManagementCommand private constructor(
    private val subCommand: UByte,
    private val pinUvAuthProtocol: UByte? = null,
    internal var pinUvAuthParam: ByteArray? = null,
    private val subCommandParams: Map<UByte, @Polymorphic ParameterValue>? = null,
    private val ctap21Implementation: Boolean = true,
) : CtapCommand() {
    override val cmdByte: Byte = if (ctap21Implementation) 0x0A else 0x41
    override var params = generateParams()

    internal fun generateParams(): Map<UByte, ParameterValue> {
        return HashMap<UByte, ParameterValue>().apply {
            this[0x01u] = UByteParameter(subCommand)
            if (subCommandParams != null) {
                this[0x02u] = MapParameter(subCommandParams)
            }
            if (pinUvAuthProtocol != null) {
                this[0x03u] = UByteParameter(pinUvAuthProtocol)
            }
            val uv = pinUvAuthParam
            if (uv != null) {
                this[0x04u] = ByteArrayParameter(uv)
            }
        }
    }

    fun getUvParamData(): ByteArray {
        val parameterBytes = if (subCommandParams != null) {
            val encoder = CTAPCBOREncoder()
            encoder.encodeSerializableValue(
                MapSerializer(UByte.serializer(), ParameterValue.serializer()),
                subCommandParams,
            )
            encoder.getBytes()
        } else {
            byteArrayOf()
        }

        return (
            listOf(
                subCommand.toByte(),
            ) + parameterBytes.toList()
            ).toByteArray()
    }

    init {
        require(
            (pinUvAuthParam == null) ||
                (pinUvAuthProtocol == 1u.toUByte() && pinUvAuthParam?.size == 16) ||
                (pinUvAuthProtocol == 2u.toUByte() && pinUvAuthParam?.size == 32),
        )
    }

    companion object {
        /**
         * Create a get-creds-metadata command
         *
         * @param pinUvAuthProtocol See [CredentialManagementCommand.pinUvAuthProtocol]
         * @param pinUvAuthParam See [CredentialManagementCommand.pinUvAuthParam]
         * @param ctap21Implementation See [CredentialManagementCommand.ctap21Implementation]
         * @return A command to get credential metadata from the Authenticator
         *
         * @see [CredentialManagementGetMetadataResponse]
         */
        @JvmStatic
        fun getCredsMetadata(
            pinUvAuthProtocol: UByte,
            pinUvAuthParam: ByteArray,
            ctap21Implementation: Boolean = true,
        ): CredentialManagementCommand {
            return CredentialManagementCommand(
                subCommand = 0x01u,
                pinUvAuthProtocol = pinUvAuthProtocol,
                pinUvAuthParam = pinUvAuthParam,
                ctap21Implementation = ctap21Implementation,
            )
        }

        /**
         * Get a command to start enumerating the discoverable Relying Parties on an Authenticator
         *
         * @param pinUvAuthProtocol See [CredentialManagementCommand.pinUvAuthProtocol]
         * @param pinUvAuthParam See [CredentialManagementCommand.pinUvAuthParam]
         * @param ctap21Implementation See [CredentialManagementCommand.ctap21Implementation]
         * @return A command to enumerate RPs
         *
         * @see [EnumerateRPsResponse]
         */
        @JvmStatic
        fun enumerateRPsBegin(
            pinUvAuthProtocol: UByte,
            pinUvAuthParam: ByteArray,
            ctap21Implementation: Boolean = true,
        ): CredentialManagementCommand {
            return CredentialManagementCommand(
                subCommand = 0x02u,
                pinUvAuthProtocol = pinUvAuthProtocol,
                pinUvAuthParam = pinUvAuthParam,
                ctap21Implementation = ctap21Implementation,
            )
        }

        /**
         * Get a command to get the next Relying Party after an already-begun iteration.
         *
         * Generally must be called immediately after [enumerateRPsBegin].
         *
         * @param ctap21Implementation See [CredentialManagementCommand.ctap21Implementation]
         * @return A command to continue enumerating RPs
         *
         * @see [EnumerateRPsResponse]
         */
        @JvmStatic
        fun enumerateRPsGetNextRP(ctap21Implementation: Boolean = true): CredentialManagementCommand {
            return CredentialManagementCommand(
                subCommand = 0x03u,
                ctap21Implementation = ctap21Implementation,
            )
        }

        /**
         * Get a command to begin enumerating discoverable credentials for a particular Relying Party.
         *
         * @param pinUvAuthProtocol See [CredentialManagementCommand.pinUvAuthProtocol]
         * @param pinUvAuthParam See [CredentialManagementCommand.pinUvAuthParam]
         * @param rpIDHash: The SHA-256 hash of the Relying Party ID
         * @param ctap21Implementation See [CredentialManagementCommand.ctap21Implementation]
         * @return A command to enumerate credentials
         *
         * @see [EnumerateCredentialsResponse]
         */
        @JvmStatic
        fun enumerateCredentialsBegin(
            pinUvAuthProtocol: UByte,
            pinUvAuthParam: ByteArray? = null,
            rpIDHash: ByteArray,
            ctap21Implementation: Boolean = true,
        ): CredentialManagementCommand {
            return CredentialManagementCommand(
                subCommand = 0x04u,
                pinUvAuthProtocol = pinUvAuthProtocol,
                pinUvAuthParam = pinUvAuthParam,
                ctap21Implementation = ctap21Implementation,
                subCommandParams = mapOf(
                    0x01u.toUByte() to ByteArrayParameter(rpIDHash),
                ),
            )
        }

        /**
         * Get a command to get the next Credential after an already-begun iteration.
         *
         * Generally must be called immediately after [enumerateCredentialsBegin].
         *
         * @param ctap21Implementation See [CredentialManagementCommand.ctap21Implementation]
         * @return A command to continue enumerating credentials
         *
         * @see [EnumerateCredentialsResponse]
         */
        @JvmStatic
        fun enumerateCredentialsGetNextCredential(ctap21Implementation: Boolean = true): CredentialManagementCommand {
            return CredentialManagementCommand(
                subCommand = 0x05u,
                ctap21Implementation = ctap21Implementation,
            )
        }

        /**
         * Get a command to delete a discoverable credential from the Authenticator.
         *
         * This will invalidate the credential permanently, even if it is passed back to
         * [a makeCredential call][us.q3q.fidok.ctap.CTAPClient.makeCredential].
         *
         * This operation will not return anything except a possible error.
         *
         * @param pinUvAuthProtocol See [CredentialManagementCommand.pinUvAuthProtocol]
         * @param pinUvAuthParam See [CredentialManagementCommand.pinUvAuthParam]
         * @param credentialId The credential to be deleted
         * @param ctap21Implementation See [CredentialManagementCommand.ctap21Implementation]
         * @return A command to delete a credential from the Authenticator
         */
        @JvmStatic
        fun deleteCredential(
            pinUvAuthProtocol: UByte,
            pinUvAuthParam: ByteArray? = null,
            credentialId: PublicKeyCredentialDescriptor,
            ctap21Implementation: Boolean = true,
        ): CredentialManagementCommand {
            return CredentialManagementCommand(
                subCommand = 0x06u,
                pinUvAuthProtocol = pinUvAuthProtocol,
                pinUvAuthParam = pinUvAuthParam,
                subCommandParams = mapOf(
                    0x02u.toUByte() to PublicKeyCredentialDescriptorParameter(credentialId),
                ),
                ctap21Implementation = ctap21Implementation,
            )
        }

        /**
         * Get a command to update the user ID (and possibly other fields) stored with a particular
         * discoverable credential.
         *
         * This operation will not return anything except a possible error.
         *
         * @param pinUvAuthProtocol See [CredentialManagementCommand.pinUvAuthProtocol]
         * @param pinUvAuthParam See [CredentialManagementCommand.pinUvAuthParam]
         * @param credentialId The credential to have its user replaced
         * @param user New user details to associate with the stored credential
         * @param ctap21Implementation See [CredentialManagementCommand.ctap21Implementation]
         * @return A command to update stored user information
         */
        @JvmStatic
        fun updateUserInformation(
            pinUvAuthProtocol: UByte,
            pinUvAuthParam: ByteArray? = null,
            credentialId: PublicKeyCredentialDescriptor,
            user: PublicKeyCredentialUserEntity,
            ctap21Implementation: Boolean = true,
        ): CredentialManagementCommand {
            return CredentialManagementCommand(
                subCommand = 0x07u,
                pinUvAuthProtocol = pinUvAuthProtocol,
                pinUvAuthParam = pinUvAuthParam,
                subCommandParams = mapOf(
                    0x02u.toUByte() to PublicKeyCredentialDescriptorParameter(credentialId),
                    0x03u.toUByte() to PublicKeyCredentialUserEntityParameter(user),
                ),
                ctap21Implementation = ctap21Implementation,
            )
        }
    }
}
