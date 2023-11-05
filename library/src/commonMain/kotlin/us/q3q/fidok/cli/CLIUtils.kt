package us.q3q.fidok.cli

import co.touchlab.kermit.Logger
import com.github.ajalt.clikt.parameters.options.OptionTransformContext
import kotlinx.coroutines.runBlocking
import us.q3q.fidok.ctap.AuthenticatorNotFoundException
import us.q3q.fidok.ctap.CTAPClient
import us.q3q.fidok.ctap.FIDOkLibrary

fun OptionTransformContext.checkHex(s: String) {
    if (s.length % 2 != 0) {
        fail("Must be an even number of hex characters")
    }
    if (s.count { c ->
            "0123456789abcdefABCDEF".indexOf(c) != -1
        } != s.length
    ) {
        fail("Must only contain hexadecimal characters")
    }
}

fun getSuitableClient(library: FIDOkLibrary): CTAPClient? {
    try {
        return runBlocking {
            library.waitForUsableAuthenticator()
        }
    } catch (e: AuthenticatorNotFoundException) {
        Logger.e("No devices found!")
    }

    return null
}
