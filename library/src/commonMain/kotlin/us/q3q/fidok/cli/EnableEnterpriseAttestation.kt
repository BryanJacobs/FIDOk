package us.q3q.fidok.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.requireObject
import kotlinx.coroutines.runBlocking
import us.q3q.fidok.ctap.CTAPClient
import us.q3q.fidok.ctap.CTAPOption
import us.q3q.fidok.ctap.CTAPPinPermission

class EnableEnterpriseAttestation : CliktCommand(help = "Enable Enterprise Attestation") {

    val client by requireObject<CTAPClient>()

    override fun run() {
        val info = client.getInfoIfUnset()
        if (info.options?.containsKey(CTAPOption.ENTERPRISE_ATTESTATION.value) != true) {
            throw UsageError("The authenticator does not support Enterprise Attestation")
        }

        runBlocking {
            val token = client.getPinUvTokenUsingAppropriateMethod(CTAPPinPermission.CREDENTIAL_MANAGEMENT.value)

            val config = client.authenticatorConfig()

            config.enableEnterpriseAttestation(pinUVToken = token)
        }

        echo("Enterprise attestation enabled")
    }
}
