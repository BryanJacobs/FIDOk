import co.touchlab.kermit.Logger
import us.q3q.fidok.HIDDevice
import us.q3q.fidok.LinuxCrypto
import us.q3q.fidok.PCSCDevice
import us.q3q.fidok.ctap.CTAPClient
import us.q3q.fidok.ctap.CTAPPinPermissions
import us.q3q.fidok.ctap.Library
import us.q3q.fidok.ctap.commands.PublicKeyCredentialDescriptor
import kotlin.random.Random

@OptIn(ExperimentalStdlibApi::class)
fun main() {
    val devices = HIDDevice.list() + PCSCDevice.list()
    if (devices.isEmpty()) {
        Logger.e("No devices found!")
        return
    }

    Library.init(LinuxCrypto())

    val device = devices[0]
    val client = CTAPClient(device)

    var cred: ByteArray? = null
    for (i in 1..5) {
        val res = client.makeCredential(clientDataHash = Random.Default.nextBytes(32), rpId = "something.cool.example")
        cred = res.authData.attestedCredentialData?.credentialId
    }

    if (cred == null) {
        throw IllegalStateException("No cred returned")
    }

    val pin = "foobar"

    client.setPIN(pin)

    var pinToken = client.getPinTokenUsingPin(
        pin,
        permissions = CTAPPinPermissions.CREDENTIAL_MANAGEMENT.value,
    )
    val meta = client.credentialManagement().getCredsMetadata(pinToken = pinToken)
    Logger.i { "Creds meta: $meta" }

    pinToken = client.getPinTokenUsingPin(
        pin,
        permissions = CTAPPinPermissions.CREDENTIAL_MANAGEMENT.value,
    )
    client.credentialManagement().deleteCredential(credentialID = PublicKeyCredentialDescriptor(cred), pinToken = pinToken)

    pinToken = client.getPinTokenUsingPin(
        pin,
        permissions = CTAPPinPermissions.CREDENTIAL_MANAGEMENT.value,
    )

    val rps = client.credentialManagement().enumerateRPs(pinToken = pinToken)
    Logger.i { "RPs: $rps" }

    for (rp in rps) {
        pinToken = client.getPinTokenUsingPin(
            pin,
            permissions = CTAPPinPermissions.CREDENTIAL_MANAGEMENT.value,
        )

        val creds = client.credentialManagement().enumerateCredentials(rpIDHash = rp.rpIDHash, pinToken = pinToken)
        Logger.i { "Creds for ${rp.rpIDHash.toHexString()}: $creds" }
    }

    /*val newPinToken = client.getPinTokenUsingPin(
        "foobar",
        permissions = CTAPPinPermissions.AUTHENTICATOR_CONFIGURATION.value,
    )

    client.authenticatorConfig().setMinPINLength(pinToken = newPinToken, newMinPINLength = 8u)

    Logger.i { "Retry info: ${client.getPINRetries()}" }*/
}
