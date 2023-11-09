package us.q3q.fidok

import us.q3q.fidok.ctap.CTAPClient

interface FIDOkCallbacks {
    suspend fun collectPin(client: CTAPClient?): String? = null

    suspend fun authenticatorNeeded() {}

    suspend fun authenticatorFound(client: CTAPClient): Boolean = true

    suspend fun authenticatorUnsuitable(client: CTAPClient) {}

    suspend fun authenticatorWaitingForSelection(client: CTAPClient) {}
}
