package us.q3q.fidok.ctap.commands

import kotlinx.serialization.Serializable

@Serializable
class GetNextAssertionCommand : CtapCommand() {
    override val cmdByte: Byte = 0x08
    override val params = null
}
