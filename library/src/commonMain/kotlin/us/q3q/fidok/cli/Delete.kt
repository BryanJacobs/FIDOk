package us.q3q.fidok.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.validate
import kotlinx.coroutines.runBlocking
import us.q3q.fidok.ctap.CTAPClient
import us.q3q.fidok.ctap.CTAPPinPermission
import us.q3q.fidok.ctap.commands.PublicKeyCredentialDescriptor
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class Delete : CliktCommand(help = "Remove a stored discoverable credential") {

    val client by requireObject<CTAPClient>()

    val rpId by option("--rp")
        .help("Identifier of the RP of interest, for permissions")

    val credential by option("--credential")
        .required()
        .help("Credential to delete, as a base64-url-encoded string")
        .validate {
            if (it.isEmpty()) {
                fail("Must not be empty")
            }
        }

    @OptIn(ExperimentalEncodingApi::class)
    override fun run() {
        runBlocking {
            val token = client.getPinUvTokenUsingAppropriateMethod(
                CTAPPinPermission.CREDENTIAL_MANAGEMENT.value,
                desiredRpId = rpId,
            )

            val credMgmt = client.credentialManagement()

            credMgmt.deleteCredential(
                credentialID = PublicKeyCredentialDescriptor(Base64.UrlSafe.decode(credential)),
                pinUVToken = token,
            )
        }

        echo("Credential deleted (maybe - Authenticators may conceal whether the cred existed)")
    }
}
