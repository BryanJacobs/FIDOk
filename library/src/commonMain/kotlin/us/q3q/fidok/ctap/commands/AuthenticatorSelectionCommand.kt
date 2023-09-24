package us.q3q.fidok.ctap.commands

import kotlinx.serialization.Serializable

/**
 * A command to ask a particular Authenticator to request User Verification
 */
@Serializable
class AuthenticatorSelectionCommand : CtapCommand() {
    override val cmdByte: Byte = 0x0B
    override val params = null
}
