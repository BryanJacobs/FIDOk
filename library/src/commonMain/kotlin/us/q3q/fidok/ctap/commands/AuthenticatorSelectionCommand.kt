package us.q3q.fidok.ctap.commands

import kotlinx.serialization.Serializable

@Serializable
class AuthenticatorSelectionCommand : CtapCommand() {
    override val cmdByte: Byte = 0x0B
    override val params = null
}
