package us.q3q.fidok.cli

import co.touchlab.kermit.Logger
import us.q3q.fidok.FIDOkCallbacks
import us.q3q.fidok.ctap.CTAPClient

open class DefaultCliCallbacks : FIDOkCallbacks {
    override suspend fun collectPin(client: CTAPClient?): String? = cliPinCollection(client)

    override suspend fun authenticatorNeeded() {
        Logger.a { "Waiting for Authenticator..." }
    }

    override suspend fun authenticatorFound(client: CTAPClient): Boolean {
        Logger.i { "Authenticator found: $client" }
        return true
    }

    override suspend fun authenticatorUnsuitable(client: CTAPClient) {
        Logger.d { "Authenticator $client is unsuitable" }
    }
}
