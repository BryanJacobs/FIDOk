package us.q3q.fidok.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import kotlinx.coroutines.runBlocking
import us.q3q.fidok.ctap.AuthenticatorTransport
import us.q3q.fidok.ctap.FIDOkLibrary
import us.q3q.fidok.gateway.HIDGateway

class HID : CliktCommand(help = "Pretend to be an HID authenticator") {

    val library by requireObject<FIDOkLibrary>()

    override fun run() {
        echo("Starting HID gateway")

        // Loopbacks would be Not Fun
        library.disableTransport(AuthenticatorTransport.USB)

        val gw = HIDGateway()

        runBlocking {
            gw.listenForever(library)
        }
    }
}
