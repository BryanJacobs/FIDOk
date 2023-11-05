package us.q3q.fidok.cli

import us.q3q.fidok.ctap.CTAPClient

suspend fun cliPinCollection(client: CTAPClient?): String? {
    val suffix = if (client != null) " for $client" else ""
    print("Enter authenticator PIN$suffix: ")
    var pin = readlnOrNull() ?: return null
    if (pin.endsWith('\n')) {
        pin = pin.substring(0, pin.length - 1)
    }
    return pin
}
