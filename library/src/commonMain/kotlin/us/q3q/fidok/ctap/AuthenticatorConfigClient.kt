package us.q3q.fidok.ctap

import us.q3q.fidok.crypto.PinUVProtocol
import us.q3q.fidok.ctap.commands.AuthenticatorConfigCommand

class AuthenticatorConfigClient internal constructor(private val client: CTAPClient) {

    private fun withPinParameters(pinProtocol: UByte?, pinUVToken: PinUVToken, f: (pp: PinUVProtocol) -> AuthenticatorConfigCommand) {
        val pp = client.getPinProtocol(pinProtocol)
        val command = f(pp)
        command.pinUvAuthParam = pp.authenticate(pinUVToken, command.getUvParamData())
        command.params = command.generateParams()
        client.xmit(command)
    }

    fun enableEnterpriseAttestation(pinProtocol: UByte? = null, pinUVToken: PinUVToken) {
        withPinParameters(pinProtocol, pinUVToken) {
            AuthenticatorConfigCommand.enableEnterpriseAttestation(
                pinUvAuthProtocol = it.getVersion(),
            )
        }
    }

    fun toggleAlwaysUv(pinProtocol: UByte? = null, pinUVToken: PinUVToken) {
        withPinParameters(pinProtocol, pinUVToken) {
            AuthenticatorConfigCommand.toggleAlwaysUv(
                pinUvAuthProtocol = it.getVersion(),
            )
        }
    }

    fun setMinPINLength(
        pinProtocol: UByte? = null,
        pinUVToken: PinUVToken,
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
