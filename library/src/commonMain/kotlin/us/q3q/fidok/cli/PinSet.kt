package us.q3q.fidok.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.terminal
import us.q3q.fidok.ctap.CTAPClient

class PinSet : CliktCommand(name = "set", help = "Set a new PIN") {

    private val client by requireObject<CTAPClient>()

    override fun run() {
        val newPin = terminal.prompt("Enter new PIN")
        if (newPin.isNullOrEmpty()) {
            echo("New PIN not gotten - exiting")
            return
        }

        client.setPIN(newPin)

        echo("PIN set.")
    }
}
