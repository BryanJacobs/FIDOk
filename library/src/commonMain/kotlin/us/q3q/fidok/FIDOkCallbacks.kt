package us.q3q.fidok

import us.q3q.fidok.ctap.CTAPClient

/**
 * Set of callbacks the library can use to inform and query an application.
 */
interface FIDOkCallbacks {
    /**
     * Called when an Authenticator requires a PIN to identify the user.
     *
     * @param client [CTAPClient] object connected to the Authenticator for which the PIN is requested
     * @return The collected PIN, or null if it could not be obtained
     */
    suspend fun collectPin(client: CTAPClient?): String? = null

    /**
     * Called when an Authenticator is required but not present.
     */
    suspend fun authenticatorNeeded() {}

    /**
     * Called when an Authenticator is newly found while waiting for one.
     *
     * Should dismiss the [authenticatorNeeded] state if the Authenticator is suitable for use.
     *
     * @param client [CTAPClient] object connected to the discovered Authenticator
     * @return true if the Authenticator should be used; false if it should be ignored
     */
    suspend fun authenticatorFound(client: CTAPClient): Boolean = true

    /**
     * Called when the library determines an Authenticator is unsuitable for use.
     *
     * For example, the Authenticator might be missing a needed feature for the requested operation.
     *
     * @param client [CTAPClient] object connected to the discarded Authenticator
     */
    suspend fun authenticatorUnsuitable(client: CTAPClient) {}

    /**
     * Called when an Authenticator is waiting for the user to use/confirm it.
     *
     * @param client [CTAPClient] object connected to the pending Authenticator
     */
    suspend fun authenticatorWaitingForSelection(client: CTAPClient) {}

    /**
     * Called when the library encounters a problem of any sort
     *
     * @param ex The exception that happened
     * @return true if the exception should be suppressed/marked as handled
     */
    suspend fun exceptionEncountered(ex: Exception): Boolean = false
}
