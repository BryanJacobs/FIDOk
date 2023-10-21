package us.q3q.fidok.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.unique
import com.github.ajalt.clikt.parameters.options.validate
import kotlinx.coroutines.runBlocking
import us.q3q.fidok.ctap.FIDOkLibrary
import us.q3q.fidok.ctap.commands.PublicKeyCredentialDescriptor
import us.q3q.fidok.webauthn.AuthenticatorAssertionResponse
import us.q3q.fidok.webauthn.PublicKeyCredentialRequestOptions
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random

@OptIn(ExperimentalEncodingApi::class)
class Get : CliktCommand(help = "Get (use) an existing webauthn credential") {

    private val library by requireObject<FIDOkLibrary>()

    private val rpId by option("--rp")
        .required()
        .help("Identifier of the Relying Party")

    private val credentials by option("--credential")
        .help("A credential ID to attempt to use, expressed as a base64URL string")
        .multiple()
        .unique()
        .validate {
            for (cred in it) {
                if (cred.isEmpty()) {
                    fail("Length must be greater than zero")
                }
            }
        }

    private val hmacSalt by option("--hmac-salt")
        .help("Salt to use for the hmac-secret extension, hex encoded")
        .multiple()
        .validate {
            if (it.size > 2) {
                fail("At most two HMAC salts can be used")
            }
            for (salt in it) {
                if (salt.length != 64) {
                    fail("Each HMAC salt must be exactly 64 hex characters")
                }
                checkHex(salt)
            }
        }

    @OptIn(ExperimentalStdlibApi::class)
    override fun run() {
        val extensions = hashMapOf<String, Any>()
        if (hmacSalt.isNotEmpty()) {
            val mp = hashMapOf<String, ByteArray>()
            mp["salt1"] = hmacSalt[0].hexToByteArray()
            if (hmacSalt.size > 1) {
                mp["salt2"] = hmacSalt[1].hexToByteArray()
            }
            extensions["hmacGetSecret"] = mp
        }

        runBlocking {
            val assertion = library.webauthn().get(
                PublicKeyCredentialRequestOptions(
                    challenge = Random.nextBytes(32),
                    rpId = rpId,
                    allowCredentials = credentials.map {
                        PublicKeyCredentialDescriptor(
                            id = Base64.UrlSafe.decode(it),
                        )
                    },
                    extensions = extensions,
                ),
            )

            val realAssertionResponse = assertion.response as AuthenticatorAssertionResponse

            echo("Assertion created: ${Base64.UrlSafe.encode(realAssertionResponse.authenticatorData)}")

            if (hmacSalt.isNotEmpty()) {
                val secretOutput = assertion.clientExtensionResults["hmacGetSecret"] as Map<*, *>?
                if (secretOutput != null) {
                    echo("HMAC output 1: ${(secretOutput["output1"] as ByteArray?)?.toHexString()}")
                    echo("HMAC output 2: ${(secretOutput["output2"] as ByteArray?)?.toHexString()}")
                }
            }
        }
    }
}
