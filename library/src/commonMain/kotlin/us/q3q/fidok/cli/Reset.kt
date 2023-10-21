package us.q3q.fidok.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import us.q3q.fidok.ctap.FIDOkLibrary

class Reset : CliktCommand(help = "PERMANENTLY reset an Authenticator, restoring it to fresh") {

    private val library by requireObject<FIDOkLibrary>()

    private val confirmation by option("--yes")
        .flag()
        .help("Actually, permanently reset the Authenticator without confirmation")

    override fun run() {
        val client = getSuitableClient(library) ?: return

        if (!confirmation) {
            echo("Type YES (all capitals) to reset $client: ", trailingNewline = false, err = true)
            val confirm = readlnOrNull()
            if (confirm != "YES") {
                echo("... chickening out.", err = true)
                return
            }
        }

        client.authenticatorReset()

        echo("Reset complete.")
    }
}
