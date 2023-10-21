package us.q3q.fidok.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.options.defaultLazy
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.types.choice
import kotlinx.coroutines.runBlocking
import us.q3q.fidok.ctap.FIDOkLibrary
import us.q3q.fidok.ctap.commands.COSEAlgorithmIdentifier
import us.q3q.fidok.ctap.commands.PublicKeyCredentialParameters
import us.q3q.fidok.ctap.commands.PublicKeyCredentialRpEntity
import us.q3q.fidok.ctap.commands.PublicKeyCredentialUserEntity
import us.q3q.fidok.webauthn.AuthenticatorSelectionCriteria
import us.q3q.fidok.webauthn.DEFAULT_PUB_KEY_CRED_PARAMS
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

    private val challenge by option("--challenge")
        .defaultLazy { Random.nextBytes(32).toHexString() }
        .help("Hex-encoded random challenge for credential creation operation")
        .validate {
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

    private val algorithm by option("--algorithm")
        .choice(*COSEAlgorithmIdentifier.entries.map { it.name.lowercase() }.toTypedArray())
        .help("Name of cryptographic algorithm to use for credential")

    private val hmacSecret by option("--hmac-secret")
        .flag()
        .help("If set, (try to) prepare the credential for use with the hmac-secret extension")

    private val credProtectLevel by option("--protect")
        .choice("1", "2", "3")
        .help("CTAP credProtect level - 2 for UV-unless-cred-provided, 3 for always-UV")

    override fun run() {
        val selectionCriteria = AuthenticatorSelectionCriteria(
            residentKey = if (discoverable) ResidentKeyRequirement.REQUIRED.value else ResidentKeyRequirement.DISCOURAGED.value,
        )

        val chosenAlgorithm = algorithm?.let {
            COSEAlgorithmIdentifier.entries.find { cose -> cose.name.lowercase() == it }
        }.let {
            if (it != null) {
                listOf(PublicKeyCredentialParameters(it))
            } else {
                null
            }
        }

        val extensions = hashMapOf<String, Any>()

        if (hmacSecret) {
            extensions["hmacCreateSecret"] = true
        }

        val cpLevel = credProtectLevel
        if (cpLevel != null) {
            extensions["credentialProtectionPolicy"] = cpLevel.toInt()
            extensions["enforceCredentialProtectionPolicy"] = true
        }

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
                    challenge = challenge.hexToByteArray(),
                    authenticatorSelectionCriteria = selectionCriteria,
                    pubKeyCredParams = chosenAlgorithm ?: DEFAULT_PUB_KEY_CRED_PARAMS,
                    extensions = extensions,
                ),
            )

            echo("Credential created: ${cred.id}")
            if (hmacSecret) {
                echo("HMAC set up: ${cred.clientExtensionResults["hmacCreateSecret"]}")
            }
        }
    }
}
