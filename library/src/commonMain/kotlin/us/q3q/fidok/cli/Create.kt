package us.q3q.fidok.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.options.defaultLazy
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.validate
import kotlinx.coroutines.runBlocking
import us.q3q.fidok.ctap.FIDOkLibrary
import us.q3q.fidok.ctap.commands.PublicKeyCredentialRpEntity
import us.q3q.fidok.ctap.commands.PublicKeyCredentialUserEntity
import us.q3q.fidok.webauthn.AuthenticatorSelectionCriteria
import us.q3q.fidok.webauthn.PublicKeyCredentialCreationOptions
import us.q3q.fidok.webauthn.ResidentKeyRequirement
import kotlin.random.Random

@OptIn(ExperimentalStdlibApi::class)
class Create : CliktCommand(help = "Create a new webauthn credential") {

    private val library by requireObject<FIDOkLibrary>()

    private val rpId by option("--rp")
        .required()
        .help("Identifier of the Relying Party")

    private val rpName by option("--rp-name")
        .help("Name (human readable) of the Relying Party")

    private val userId by option("--user-id")
        .defaultLazy { Random.nextBytes(64).toHexString() }
        .help("Unique identifier of the user")
        .validate {
            if (it.isEmpty()) {
                fail("Length must be greater than zero")
            }
            if (it.length > 128) {
                fail("Length must be at most 128 hex characters")
            }
            checkHex(it)
        }

    private val userName by option("--user-name")
        .required()
        .help("Name (human readable) of user")

    private val userDisplayName by option("--user-display-name")
        .help("Even MORE human readable name of user")

    private val discoverable by option("--discoverable")
        .flag()
        .help("If set, make a discoverable (authenticator-stored) credential")

    override fun run() {
        val selectionCriteria = AuthenticatorSelectionCriteria(
            residentKey = if (discoverable) ResidentKeyRequirement.REQUIRED.value else ResidentKeyRequirement.DISCOURAGED.value,
        )

        runBlocking {
            val cred = library.webauthn().create(
                PublicKeyCredentialCreationOptions(
                    rp = PublicKeyCredentialRpEntity(
                        id = rpId,
                        name = rpName ?: rpId,
                    ),
                    user = PublicKeyCredentialUserEntity(
                        id = userId.hexToByteArray(),
                        name = userName,
                        displayName = userDisplayName ?: userName,
                    ),
                    challenge = Random.nextBytes(32),
                    authenticatorSelectionCriteria = selectionCriteria,
                ),
            )

            echo("Credential created: ${cred.id.toHexString()}")
        }
    }
}
