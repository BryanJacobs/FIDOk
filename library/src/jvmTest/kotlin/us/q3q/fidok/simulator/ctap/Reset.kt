package us.q3q.fidok.simulator.ctap

import us.q3q.fidok.ctap.CTAPError
import us.q3q.fidok.ctap.CTAPResponse
import us.q3q.fidok.ctap.commands.PublicKeyCredentialDescriptor
import us.q3q.fidok.simulator.SimulationTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Reset : SimulationTest() {

    @Test
    fun resetInvalidatesCredential() {
        val credRes = client.makeCredential(rpId = rpId, userDisplayName = userDisplayName)

        client.authenticatorReset()

        val exception = assertFailsWith<CTAPError> {
            client.getAssertions(
                clientDataHash = Random.nextBytes(32),
                rpId = rpId,
                allowList = listOf(
                    PublicKeyCredentialDescriptor(id = credRes.authData.attestedCredentialData!!.credentialId),
                ),
            )
        }
        assertEquals(CTAPResponse.NO_CREDENTIALS.value, exception.code)
    }
}
