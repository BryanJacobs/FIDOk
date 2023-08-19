package us.q3q.fidok.ctap

import us.q3q.fidok.crypto.PinProtocol
import us.q3q.fidok.ctap.commands.AuthenticatorConfigCommand

class AuthenticatorConfigClient internal constructor(private val client: CTAPClient) {

    private fun withPinParameters(pinProtocol: UByte?, pinToken: PinToken, f: (pp: PinProtocol) -> AuthenticatorConfigCommand) {
        val pp = client.getPinProtocol(pinProtocol)
        val command = f(pp)
        command.pinUvAuthParam = pp.authenticate(pinToken, command.getUvParamData())
        command.params = command.generateParams()
        client.xmit(command)
    }

    fun enableEnterpriseAttestation(pinProtocol: UByte? = null, pinToken: PinToken) {
        withPinParameters(pinProtocol, pinToken) {
            AuthenticatorConfigCommand.enableEnterpriseAttestation(
                pinUvAuthProtocol = it.getVersion(),
            )
        }
    }

    fun toggleAlwaysUv(pinProtocol: UByte? = null, pinToken: PinToken) {
        withPinParameters(pinProtocol, pinToken) {
            AuthenticatorConfigCommand.toggleAlwaysUv(
                pinUvAuthProtocol = it.getVersion(),
            )
        }
    }

    fun setMinPINLength(
        pinProtocol: UByte? = null,
        pinToken: PinToken,
        newMinPINLength: UInt? = null,
        minPinLengthRPIDs: Array<String>? = null,
        forceChangePin: Boolean? = null,
    ) {
        withPinParameters(pinProtocol, pinToken) {
            AuthenticatorConfigCommand.setMinPINLength(
                pinUvAuthProtocol = it.getVersion(),
                newMinPINLength = newMinPINLength,
                minPinLengthRPIDs = minPinLengthRPIDs,
                forceChangePin = forceChangePin,
            )
        }
    }
}
