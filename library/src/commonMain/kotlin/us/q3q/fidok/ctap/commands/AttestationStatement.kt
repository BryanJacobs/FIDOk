package us.q3q.fidok.ctap.commands

/**
 * Represents SOME type of attestation statement.
 *
 * These are returned by an Authenticator when [creating a new Credential][MakeCredentialResponse]
 * (or, in CTAP 2.2, sometimes also in a [GetAssertionResponse]). This class is useless on its
 * own, as in order to meaningfully assess an Attestation one needs to know what type of
 * Attestation it is - the subclass.
 */
open class AttestationStatement
