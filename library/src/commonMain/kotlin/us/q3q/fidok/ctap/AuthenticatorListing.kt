package us.q3q.fidok.ctap

/**
 * A provider of [AuthenticatorDevice] instances.
 */
interface AuthenticatorListing {
    /**
     * Get Authenticators currently attached.
     *
     * @return A list of available Authenticators
     */
    fun listDevices(library: FIDOkLibrary): List<AuthenticatorDevice>

    /**
     * Get the types of [transport][AuthenticatorTransport] for which this listing object can provide Authenticators.
     *
     * @return A list of [AuthenticatorTransport]s
     */
    fun providedTransports(): List<AuthenticatorTransport> = listOf()
}
