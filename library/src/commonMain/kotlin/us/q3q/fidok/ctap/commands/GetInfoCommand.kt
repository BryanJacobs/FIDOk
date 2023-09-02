package us.q3q.fidok.ctap.commands

import kotlinx.serialization.Serializable

@Serializable
class GetInfoCommand : CtapCommand() {
    override val cmdByte: Byte = 0x04
    override val params = null
}
