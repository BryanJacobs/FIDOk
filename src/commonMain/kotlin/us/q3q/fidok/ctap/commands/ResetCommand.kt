package us.q3q.fidok.ctap.commands

import kotlinx.serialization.Serializable

@Serializable
class ResetCommand : CtapCommand() {
    override val cmdByte: Byte = 0x07
    override val params = null
}
