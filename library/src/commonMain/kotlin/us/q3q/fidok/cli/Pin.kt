package us.q3q.fidok.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.subcommands
import us.q3q.fidok.ctap.FIDOkLibrary

class Pin : CliktCommand(help = "Manage Authenticator PINs (user passwords)") {

    init {
        subcommands(PinSet(), PinChange())
    }

    private val library by requireObject<FIDOkLibrary>()

    override fun run() {
        val client = getSuitableClient(library)
        currentContext.obj = client
    }
}
