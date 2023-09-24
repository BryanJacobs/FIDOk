package us.q3q.fidok.ctap.commands

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.jvm.JvmStatic

/**
 * A CTAP command to change an Authenticator's settings.
 * This class should generally be built by a [us.q3q.fidok.ctap.AuthenticatorConfigClient];
 * or through the provided static methods: it's difficult to use directly.
 *
 * @param subCommand The CTAP identifier of the particular type of configuration command being invoked
 * @param subCommandParams Parameter map, depending on the subcommand involved
 * @param pinUvAuthProtocol PIN/UV auth protocol number
 * @param pinUvAuthParam Validation of the command being issued, per the PIN/UV protocol in use
 */
@Serializable
class AuthenticatorConfigCommand private constructor(
    val subCommand: UByte,
    val subCommandParams: Map<UByte, @Polymorphic ParameterValue>? = null,
    val pinUvAuthProtocol: UByte? = null,
    var pinUvAuthParam: ByteArray? = null,
) : CtapCommand() {
    override val cmdByte: Byte = 0x0D

    /**
     * Get the data over which UV validation is supposed to be performed
     *
     * @return Bytes included in the PIN/UV verification process
     */
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
            ByteArray(32) { 0xFF.toByte() }.toList() + listOf(
                cmdByte, subCommand.toByte(),
            ) + parameterBytes.toList()
            ).toByteArray()
    }

    companion object {

        /**
         * Get a command to enable Enterprise Attestation.
         *
         * Enterprise Attestation cannot be disabled (except by a Reset), only enabled. When enabled, certain
         * Relying Parties (determined by the specifics of the Authenticator) will receive extra information about
         * the Authenticator in the [responses][MakeCredentialResponse] (and [flag][MakeCredentialResponse.epAtt]) to
         * [creating credentials][MakeCredentialCommand]. The exact details of what extra they get back is, again,
         * up to the Authenticator.
         *
         * The current state can be viewed in the [options][GetInfoResponse.options] of [GetInfoResponse].
         *
         * @param pinUvAuthProtocol as [AuthenticatorConfigCommand.pinUvAuthProtocol]
         * @param pinUvAuthParam as [AuthenticatorConfigCommand.pinUvAuthParam]
         * @return A command for enabling Enterprise Attestation
         */
        @JvmStatic
        fun enableEnterpriseAttestation(pinUvAuthProtocol: UByte? = null, pinUvAuthParam: ByteArray? = null): AuthenticatorConfigCommand {
            return AuthenticatorConfigCommand(
                subCommand = 0x01u,
                pinUvAuthProtocol = pinUvAuthProtocol,
                pinUvAuthParam = pinUvAuthParam,
            )
        }

        /**
         * Get a command to enable or disable the Always UV feature.
         *
         * When enabled, the Authenticator will require that all [makeCredential][MakeCredentialCommand] and
         * [getAssertion][GetAssertionCommand] operations require User Verification. This command will toggle
         * the `alwaysUv` state, turning it off if on and on if previously off. The current state can be viewed
         * in the [options][GetInfoResponse.options] of [GetInfoResponse].
         *
         * @param pinUvAuthProtocol as [AuthenticatorConfigCommand.pinUvAuthProtocol]
         * @param pinUvAuthParam as [AuthenticatorConfigCommand.pinUvAuthParam]
         * @return A command for toggling the Always UV state
         */
        @JvmStatic
        fun toggleAlwaysUv(pinUvAuthProtocol: UByte? = null, pinUvAuthParam: ByteArray? = null): AuthenticatorConfigCommand {
            return AuthenticatorConfigCommand(
                subCommand = 0x02u,
                pinUvAuthProtocol = pinUvAuthProtocol,
                pinUvAuthParam = pinUvAuthParam,
            )
        }

        /**
         * Gets a command for setting the minimum PIN length the Authenticator permits.
         *
         * The PIN length can only be made longer, not shorter. This command can also change the list of
         * Relying Parties allowed to view the configured minimum PIN length in the [response][MakeCredentialResponse]
         * via the [MinPinLengthExtension].
         *
         * @param pinUvAuthProtocol as [AuthenticatorConfigCommand.pinUvAuthProtocol]
         * @param pinUvAuthParam as [AuthenticatorConfigCommand.pinUvAuthParam]
         * @param newMinPINLength If set, the new number of UTF-8 code points the authenticator will accept. Practically
         *                        must be less than 64, as a PIN can be at most 63 bytes long and each UTF-8 code point
         *                        is at least one byte. Must be longer than the Authenticator's current configured
         *                        `minPinLength` value
         * @param minPinLengthRPIDs An array of Relying Party IDs allowed to view the configured minimum PIN length,
         *                          via the `minPinLength` extension. Replaces any existing RP IDs set through this
         *                          command, but does not replace any such RP IDs built into the Authenticator itself
         * @param forceChangePin If true, force the user to change their PIN after the operation before obtaining a new
         *                       PIN/UV auth token. This is ignored (and treated as `true`) if the `newMinPINLength` has
         *                       increased from its previous value
         * @return A command for performing the requested min-PIN-length action(s)
         */
        @JvmStatic
        fun setMinPINLength(
            pinUvAuthProtocol: UByte?,
            pinUvAuthParam: ByteArray? = null,
            newMinPINLength: UInt? = null,
            minPinLengthRPIDs: Array<String>? = null,
            forceChangePin: Boolean? = null,
        ): AuthenticatorConfigCommand {
            return AuthenticatorConfigCommand(
                subCommand = 0x03u,
                pinUvAuthProtocol = pinUvAuthProtocol,
                pinUvAuthParam = pinUvAuthParam,
                subCommandParams = HashMap<UByte, ParameterValue>().apply {
                    if (newMinPINLength != null) {
                        this[0x01u] = UIntParameter(newMinPINLength)
                    }
                    if (minPinLengthRPIDs != null) {
                        this[0x02u] = StringArrayParameter(minPinLengthRPIDs)
                    }
                    if (forceChangePin != null) {
                        this[0x03u] = BooleanParameter(forceChangePin)
                    }
                },
            )
        }
    }

    internal fun generateParams(): Map<UByte, ParameterValue> {
        return HashMap<UByte, ParameterValue>().apply {
            this[0x01u] = UByteParameter(subCommand)
            if (subCommandParams != null) {
                this[0x02u] = MapParameter(subCommandParams)
            }
            if (pinUvAuthProtocol != null) {
                this[0x03u] = UByteParameter(pinUvAuthProtocol)
            }
            val puv = pinUvAuthParam
            if (puv != null) {
                this[0x04u] = ByteArrayParameter(puv)
            }
        }
    }

    override var params = generateParams()
}
