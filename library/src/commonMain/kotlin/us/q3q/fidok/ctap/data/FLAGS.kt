package us.q3q.fidok.ctap.data

/**
 * Represents the possible "flag bits" returned in response to a
 * [credential creation][us.q3q.fidok.ctap.commands.MakeCredentialResponse] or
 * [assertion][us.q3q.fidok.ctap.commands.GetAssertionResponse] command.
 *
 * @property value The CTAP standard's value for the flag
 */
enum class FLAGS(val value: UByte) {
    /**
     * The presence of some human is requested to be, or has been, checked
     */
    USER_PRESENCE(0x01u),

    /**
     * The identity of the human interacting with the Authenticator should be, or has been, verified
     */
    USER_VERIFICATION(0x04u),

    /**
     * The Authenticator allows this Credential to be exported in some fashion
     */
    BACKUP_ELIGIBLE(0x08u),

    /**
     * This Credential actually *has been* exported from the Authenticator and could have a copy elsewhere
     */
    BACKUP_STATE(0x10u),

    /**
     * This command response includes an [Attestation][us.q3q.fidok.ctap.commands.AttestationStatement]
     */
    ATTESTED(0x40u),

    /**
     * This command response includes data for at least one [extension][us.q3q.fidok.ctap.commands.Extension]
     */
    EXTENSION_DATA(0x80u),
}
