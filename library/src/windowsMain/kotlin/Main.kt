import co.touchlab.kermit.Logger
import kotlinx.coroutines.runBlocking
import us.q3q.fidok.BotanCryptoProvider
import us.q3q.fidok.LibHIDDevice
import us.q3q.fidok.ctap.CTAPPinPermission
import us.q3q.fidok.ctap.FIDOkLibrary
import us.q3q.fidok.ctap.commands.CredProtectExtension
import us.q3q.fidok.ctap.commands.ExtensionSetup
import us.q3q.fidok.ctap.commands.HMACSecretExtension
import us.q3q.fidok.ctap.commands.PublicKeyCredentialDescriptor
import kotlin.random.Random

@OptIn(ExperimentalStdlibApi::class)
fun main() {
    val library = FIDOkLibrary.init(
        BotanCryptoProvider(),
        listOf(LibHIDDevice),
    )

    val devices = library.listDevices()
    if (devices.isEmpty()) {
        Logger.e("No devices found!")
        return
    }

    val device = devices[0]
    val client = library.ctapClient(device)

    var cred: ByteArray? = null
    for (i in 1..5) {
        val credProtect = CredProtectExtension(2u)
        val hmacSecret = HMACSecretExtension(Random.Default.nextBytes(32))
        val extensions = ExtensionSetup(
            listOf(
                credProtect,
                hmacSecret,
            ),
        )
        val res = client.makeCredential(
            rpId = "something.cool.example",
            userDisplayName = "Bob",
            extensions = extensions,
        )
        println("RESULT: $res")
        println("credRes: ${credProtect.getLevel()}")
        println("hmacSecret: ${hmacSecret.wasCreated()}")
        cred = res.authData.attestedCredentialData?.credentialId
    }

    if (cred == null) {
        throw IllegalStateException("No cred returned")
    }

    val pin = "foobar"

    client.setPIN(pin)

    var pinToken = client.getPinTokenWithPermissions(
        pin,
        permissions = CTAPPinPermission.CREDENTIAL_MANAGEMENT.value,
    )
    runBlocking {
        val meta = client.credentialManagement().getCredsMetadata(pinUVToken = pinToken)
        Logger.i { "Creds meta: $meta" }

        pinToken = client.getPinTokenWithPermissions(
            pin,
            permissions = CTAPPinPermission.CREDENTIAL_MANAGEMENT.value,
        )
        client.credentialManagement().deleteCredential(credentialID = PublicKeyCredentialDescriptor(cred), pinUVToken = pinToken)

        pinToken = client.getPinTokenWithPermissions(
            pin,
            permissions = CTAPPinPermission.CREDENTIAL_MANAGEMENT.value,
        )

        val rps = client.credentialManagement().enumerateRPs(pinUVToken = pinToken)
        Logger.i { "RPs: $rps" }

        for (rp in rps) {
            pinToken = client.getPinTokenWithPermissions(
                pin,
                permissions = CTAPPinPermission.CREDENTIAL_MANAGEMENT.value,
            )

            val creds = client.credentialManagement().enumerateCredentials(rpIDHash = rp.rpIDHash, pinUVToken = pinToken)
            Logger.i { "Creds for ${rp.rpIDHash.toHexString()}: $creds" }
        }

        /*val newPinToken = client.getPinTokenUsingPin(
            "foobar",
            permissions = CTAPPinPermissions.AUTHENTICATOR_CONFIGURATION.value,
        )

        client.authenticatorConfig().setMinPINLength(pinToken = newPinToken, newMinPINLength = 8u)

        Logger.i { "Retry info: ${client.getPINRetries()}" }*/
    }
}
