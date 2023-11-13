package us.q3q.fidok.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.options.help
import us.q3q.fidok.ctap.FIDOkLibrary

class Info : CliktCommand(help = "Show information about an Authenticator") {
    private val library by requireObject<FIDOkLibrary>()

    override fun run() {
        val client = getSuitableClient(library) ?: return

        val info = client.getInfo()

        echo("Authenticator info for $client: $info")
    }
}
