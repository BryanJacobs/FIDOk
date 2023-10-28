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
import us.q3q.fidok.ctap.FIDOkLibrary
import us.q3q.fidok.ctap.commands.PublicKeyCredentialDescriptor
import us.q3q.fidok.ctap.commands.PublicKeyCredentialUserEntity
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalStdlibApi::class)
class UpdateUser : CliktCommand(help = "Change the user ID associated with a stored credential") {

    val client by requireObject<CTAPClient>()

    val library by requireObject<FIDOkLibrary>()

    val rpId by option("--rp")
        .help("Identifier of the RP associated with the stored credential, for permissions")

    val credential by option("--credential")
        .required()
        .help("Credential to update, as a base64-url-encoded string")
        .validate {
            if (it.isEmpty()) {
                fail("Must not be empty")
            }
        }

    private val userId by option("--user-id")
        .help("New unique identifier of the user")
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
        .help("New name (human readable) of user")

    private val userDisplayName by option("--user-display-name")
        .help("New even MORE human readable name of user")

    @OptIn(ExperimentalEncodingApi::class)
    override fun run() {
        val credBytes = Base64.UrlSafe.decode(credential)

        runBlocking {
            var token = client.getPinUvTokenUsingAppropriateMethod(
                CTAPPinPermission.CREDENTIAL_MANAGEMENT.value,
                desiredRpId = rpId,
            )

            val credMgmt = client.credentialManagement()

            var effectiveUserId = userId?.hexToByteArray()
            var effectiveUserName = userName
            var effectiveUserDisplayName = userDisplayName

            if (userId == null || userName == null) {
                // Get previous credential to read-modify-write the user info
                val rpRawId = rpId ?: error("Either the RPID must be provided, or both the user ID and name must be")
                val creds = credMgmt.enumerateCredentials(
                    rpIDHash = library.cryptoProvider.sha256(rpRawId.hexToByteArray()).hash,
                    pinUVToken = token,
                )
                var matched = false
                for (cred in creds) {
                    if (cred.credentialID.id.contentEquals(credBytes)) {
                        if (userId == null) {
                            effectiveUserId = cred.user.id
                        }
                        if (userName == null) {
                            effectiveUserName = cred.user.name
                        }
                        if (userDisplayName == null && effectiveUserDisplayName?.isEmpty() != true) {
                            effectiveUserDisplayName = cred.user.displayName
                        }
                        matched = true
                        break
                    }
                }
                if (!matched) {
                    error("User ID and/or name were not provided, but no discoverable credential matches the given one")
                }

                token = client.getPinUvTokenUsingAppropriateMethod(
                    CTAPPinPermission.CREDENTIAL_MANAGEMENT.value,
                    desiredRpId = rpId,
                )
            }

            if (effectiveUserId == null || effectiveUserName == null) {
                error("User ID and/or name not set, but both are required")
            }

            credMgmt.updateUserInformation(
                credentialID = PublicKeyCredentialDescriptor(credBytes),
                user = PublicKeyCredentialUserEntity(
                    id = effectiveUserId,
                    name = effectiveUserName,
                    displayName = effectiveUserDisplayName,
                ),
                pinUVToken = token,
            )
        }

        echo("User information updated")
    }
}
