package us.q3q.fidok.ctap.commands

import kotlinx.serialization.Serializable

/**
 * When a [GetAssertionCommand] returns [GetAssertionResponse.numberOfCredentials] greater than one, the "other"
 * Assertions can be accessed by sending this command repeatedly.
 *
 * This command can only be sent IMMEDIATELY after a [GetAssertionCommand]; the Authenticator won't keep state
 * otherwise.
 *
 * This command doesn't have its own specific response; the Authenticator will reply with another
 * [GetAssertionResponse].
 */
@Serializable
class GetNextAssertionCommand : CtapCommand() {
    override val cmdByte: Byte = 0x08
    override val params = null
}
