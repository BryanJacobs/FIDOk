package us.q3q.fidok.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import kotlinx.coroutines.runBlocking
import us.q3q.fidok.ctap.CTAPClient
import us.q3q.fidok.ctap.CTAPOption
import us.q3q.fidok.ctap.CTAPPinPermission

class ToggleAlwaysUV : CliktCommand(help = "Turn on (or off) the requirement for a PIN on all operations") {

    val client by requireObject<CTAPClient>()

    override fun run() {
        var info = client.getInfoIfUnset()
        if (info.options?.containsKey(CTAPOption.ALWAYS_UV.value) != true) {
            error("The authenticator does not support alwaysUv")
        }

        runBlocking {
            val token = client.getPinUvTokenUsingAppropriateMethod(CTAPPinPermission.CREDENTIAL_MANAGEMENT.value)

            val config = client.authenticatorConfig()

            config.toggleAlwaysUv(pinUVToken = token)

            info = client.getInfo()

            if (info.options?.get(CTAPOption.ALWAYS_UV.value) == true) {
                echo("AlwaysUV is now ENABLED")
            } else {
                echo("AlwaysUV is now DISABLED")
            }
        }
    }
}
