package us.q3q.fidok.cli

import us.q3q.fidok.ctap.CTAPClient

suspend fun cliPinCollection(client: CTAPClient): String? {
    print("Enter authenticator PIN for $client: ")
    var pin = readlnOrNull() ?: return null
    if (pin.endsWith('\n')) {
        pin = pin.substring(0, pin.length - 1)
    }
    return pin
}
