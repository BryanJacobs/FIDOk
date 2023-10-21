package us.q3q.fidok.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.coroutines.runBlocking
import us.q3q.fidok.ctap.CTAPClient
import us.q3q.fidok.ctap.CTAPPinPermission
import us.q3q.fidok.ctap.PinUVToken
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class CredList : CliktCommand(name = "list", help = "List discoverable credentials") {

    val client by requireObject<CTAPClient>()

    val rpId by option("--rp")
        .help("Identifier or hash (hex encoded) of the RP of interest")

    @OptIn(ExperimentalStdlibApi::class, ExperimentalEncodingApi::class)
    override fun run() {
        runBlocking {
            var token: PinUVToken? = client.getPinUvTokenUsingAppropriateMethod(CTAPPinPermission.CREDENTIAL_MANAGEMENT.value)

            val credMgmt = client.credentialManagement()

            val rps = credMgmt.enumerateRPs(pinUVToken = token)

            if (rps.isEmpty()) {
                echo("No discoverable credentials")
            }

            for (rp in rps) {
                if (rpId != null && rp.rp.id != rpId && rp.rpIDHash.toHexString().lowercase() != rpId?.lowercase()) {
                    continue
                }

                echo("Stored relying party: ${rp.rp.name ?: (rp.rp.id ?: rp.rpIDHash.toHexString())}")

                if (token == null) {
                    token = client.getPinUvTokenUsingAppropriateMethod(CTAPPinPermission.CREDENTIAL_MANAGEMENT.value)
                }

                val creds = credMgmt.enumerateCredentials(rp.rpIDHash, pinUVToken = token)
                token = null // Token might now be bound to "the wrong" RPID

                for (cred in creds) {
                    echo(" -- Stored credential (level ${cred.credProtect ?: 1}): ${Base64.UrlSafe.encode(cred.credentialID.id)}")
                    echo("   User ID: ${cred.user.id.toHexString()}")
                    if (cred.user.name != null) {
                        echo("   User name: ${cred.user.name} (display ${cred.user.displayName})")
                    }
                }
            }
        }
    }
}
