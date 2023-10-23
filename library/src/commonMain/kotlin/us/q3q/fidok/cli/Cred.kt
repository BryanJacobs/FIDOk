package us.q3q.fidok.cli

import com.github.ajalt.clikt.core.Abort
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.subcommands
import us.q3q.fidok.ctap.FIDOkLibrary

class Cred : CliktCommand("Manage discoverable credentials") {

    init {
        subcommands(CredList(), Delete())
    }

    private val library by requireObject<FIDOkLibrary>()

    override fun run() {
        val client = getSuitableClient(library)

        if (client == null) {
            echo("No devices found", err = true)
            throw Abort()
        }

        currentContext.obj = client
    }
}
