package us.q3q.fidok.simulator.ctap

import us.q3q.fidok.ctap.commands.PublicKeyCredentialDescriptor
import us.q3q.fidok.simulator.SimulationTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalStdlibApi::class)
class AssertionIteration : SimulationTest() {

    @Test
    fun iterateDiscoverable() {
        val cred1 = client.makeCredential(rpId = rpId, userDisplayName = userDisplayName, discoverableCredential = true)
        val cred2 = client.makeCredential(rpId = rpId, userDisplayName = userDisplayName, discoverableCredential = true)

        val challenge = Random.nextBytes(32)

        val assertions = client.getAssertions(
            clientDataHash = challenge,
            rpId = rpId,
        )

        assertEquals(2, assertions.size)
        assertEquals(2, assertions[0].numberOfCredentials)
        assertNull(assertions[1].numberOfCredentials)

        // Note: must come back in order with the most recent first
        assertEquals(cred2.getCredentialID().toHexString(), assertions[0].credential.id.toHexString())
        assertEquals(cred1.getCredentialID().toHexString(), assertions[1].credential.id.toHexString())
        client.validateAssertionSignature(assertions[0], challenge, cred2.getCredentialPublicKey())
        client.validateAssertionSignature(assertions[1], challenge, cred1.getCredentialPublicKey())
    }

    @Test
    fun iterateAllowList() {
        val cred1 = client.makeCredential(rpId = rpId, userDisplayName = userDisplayName)
        val cred2 = client.makeCredential(rpId = rpId, userDisplayName = userDisplayName)

        val challenge = Random.nextBytes(32)

        val assertions = client.getAssertions(
            clientDataHash = challenge,
            rpId = rpId,
            allowList = listOf(
                PublicKeyCredentialDescriptor(id = cred1.getCredentialID()),
                PublicKeyCredentialDescriptor(id = cred2.getCredentialID()),
            ),
        )

        // Authenticator allowed to return just one entry in this case - the first one
        assertEquals(1, assertions.size)
        assertEquals(cred1.getCredentialID().toHexString(), assertions[0].credential.id.toHexString())
        client.validateAssertionSignature(assertions[0], challenge, cred1.getCredentialPublicKey())
    }
}
