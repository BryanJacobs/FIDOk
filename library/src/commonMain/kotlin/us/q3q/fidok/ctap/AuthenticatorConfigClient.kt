package us.q3q.fidok.ctap

import us.q3q.fidok.crypto.PinUVProtocol
import us.q3q.fidok.ctap.commands.AuthenticatorConfigCommand

/**
 * Allows access to CTAP Authenticator Config commands, changing the state of the Authenticator itself.
 */
class AuthenticatorConfigClient internal constructor(private val client: CTAPClient) {

    private fun withPinParameters(pinProtocol: UByte?, pinUVToken: PinUVToken, f: (pp: PinUVProtocol) -> AuthenticatorConfigCommand) {
        val pp = client.getPinProtocol(pinProtocol)
        val command = f(pp)
        command.pinUvAuthParam = pp.authenticate(pinUVToken, command.getUvParamData())
        command.params = command.generateParams()
        client.xmit(command)
    }

    /**
     * Enables Enterprise Attestation.
     *
     * When this is on, the Authenticator is capable of returning uniquely self-identifying data in response
     * to certain commands. When off, the Authenticator might expose what _type_ of Authenticator it is, but won't
     * provide enough information for a Relying Party to determine that a single device created two different
     * Credentials.
     *
     * @param pinUVToken A PIN/UV token obtained from the Authenticator
     * @param pinProtocol The PIN/UV protocol in use
     */
    fun enableEnterpriseAttestation(pinUVToken: PinUVToken, pinProtocol: UByte? = null) {
        withPinParameters(pinProtocol, pinUVToken) {
            AuthenticatorConfigCommand.enableEnterpriseAttestation(
                pinUvAuthProtocol = it.getVersion(),
            )
        }
    }

    /**
     * Toggles the Always-Require-User-Verification option.
     *
     * When this is on, the Authenticator will require User Verification for every
     * [Credential creation][us.q3q.fidok.ctap.CTAPClient.makeCredential] and
     * [Assertion generation][us.q3q.fidok.ctap.CTAPClient.getAssertions] operation.
     *
     * After this call, if `alwaysUv` was off, it will be on, and if it was on, it will be off.
     *
     * @param pinUVToken A PIN/UV token obtained from the Authenticator
     * @param pinProtocol The PIN/UV protocol in use
     */
    fun toggleAlwaysUv(pinUVToken: PinUVToken, pinProtocol: UByte? = null) {
        withPinParameters(pinProtocol, pinUVToken) {
            AuthenticatorConfigCommand.toggleAlwaysUv(
                pinUvAuthProtocol = it.getVersion(),
            )
        }
    }

    /**
     * Manipulates the [MinPinLength extension][us.q3q.fidok.ctap.commands.MinPinLengthExtension] settings.
     *
     * This can be used to have the Authenticator enforce that PINs be at or above a certain length. It can also
     * force the user to change their PIN before being able to generate further Credentials, or change the set
     * of Relying Parties that can view the configured minimum PIN length.
     *
     * @param pinUVToken A PIN/UV token obtained from the Authenticator
     * @param pinProtocol The PIN/UV protocol in use
     * @param newMinPINLength The new minimum number of UTF-8 characters the user's PIN must contain. If provided,
     * must be equal to or greater than the Authenticator's previous value. If it is greater than the previous value,
     * [forceChangePin] will be implied
     * @param minPinLengthRPIDs The list of Relying Party IDs that are allowed to see the Authenticator's configured
     * minimum PIN length as part of an Attestation in response to creating a Credential
     * @param forceChangePin If true, the Authenticator will refuse to create new Credentials or generate Assertions
     * until the user changes their PIN
     */
    fun setMinPINLength(
        pinUVToken: PinUVToken,
        pinProtocol: UByte? = null,
        newMinPINLength: UInt? = null,
        minPinLengthRPIDs: Array<String>? = null,
        forceChangePin: Boolean? = null,
    ) {
        withPinParameters(pinProtocol, pinUVToken) {
            AuthenticatorConfigCommand.setMinPINLength(
                pinUvAuthProtocol = it.getVersion(),
                newMinPINLength = newMinPINLength,
                minPinLengthRPIDs = minPinLengthRPIDs,
                forceChangePin = forceChangePin,
            )
        }
    }
}
