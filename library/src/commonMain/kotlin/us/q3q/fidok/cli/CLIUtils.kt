package us.q3q.fidok.cli

import co.touchlab.kermit.Logger
import com.github.ajalt.clikt.parameters.options.OptionTransformContext
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
    val devices = library.listDevices()
    if (devices.isEmpty()) {
        Logger.e("No devices found!")
        return null
    }

    val device = devices[0]

    return library.ctapClient(device)
}
