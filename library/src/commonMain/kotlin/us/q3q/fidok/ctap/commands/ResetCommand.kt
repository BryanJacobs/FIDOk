package us.q3q.fidok.ctap.commands

import kotlinx.serialization.Serializable

/**
 * A command to totally reset an Authenticator, deleting all data and starting factory-fresh.
 */
@Serializable
class ResetCommand : CtapCommand() {
    override val cmdByte: Byte = 0x07
    override val params = null
}
