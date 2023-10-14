package us.q3q.fidok.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.validate
import kotlinx.coroutines.runBlocking
import us.q3q.fidok.ctap.FIDOkLibrary
import us.q3q.fidok.ctap.commands.PublicKeyCredentialDescriptor
import us.q3q.fidok.webauthn.PublicKeyCredentialRequestOptions
import kotlin.random.Random

@OptIn(ExperimentalStdlibApi::class)
class Get : CliktCommand(help = "Get (use) an existing webauthn credential") {

    private val library by requireObject<FIDOkLibrary>()

    private val rpId by option("--rp")
        .required()
        .help("Identifier of the Relying Party")

    private val credentials by option("--credential")
        .help("A credential ID to attempt to use, expressed as a hex string")
        .multiple()
        .validate {
            for (cred in it) {
                if (cred.isEmpty()) {
                    fail("Length must be greater than zero")
                }
                checkHex(cred)
            }
        }

    override fun run() {
        runBlocking {
            library.webauthn().get(
                PublicKeyCredentialRequestOptions(
                    challenge = Random.nextBytes(32),
                    rpId = rpId,
                    allowCredentials = credentials.map {
                        PublicKeyCredentialDescriptor(
                            id = it.hexToByteArray(),
                        )
                    },
                ),
            )
        }
    }
}
