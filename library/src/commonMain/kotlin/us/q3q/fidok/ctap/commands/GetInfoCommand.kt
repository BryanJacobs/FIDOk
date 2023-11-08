package us.q3q.fidok.ctap.commands

import kotlinx.serialization.Serializable

/**
 * A request to the Authenticator to describe itself and its supported features.
 */
@Serializable
class GetInfoCommand : CtapCommand() {
    override val cmdByte: Byte = 0x04
    override val params = null
}
