package us.q3q.fidok.simulator.ctap

import us.q3q.fidok.ctap.CTAPError
import us.q3q.fidok.ctap.CTAPResponse
import us.q3q.fidok.ctap.commands.COSEAlgorithmIdentifier
import us.q3q.fidok.ctap.commands.FLAGS
import us.q3q.fidok.ctap.commands.PublicKeyCredentialDescriptor
import us.q3q.fidok.simulator.SimulationTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MakeCredential : SimulationTest() {

    @Test
    fun basicMakeCredential() {
        val res = client.makeCredential(rpId = rpId, userDisplayName = userDisplayName)

        assertEquals(0u, res.authData.signCount)
        assertTrue(res.authData.hasFlag(FLAGS.UP))
        assertTrue(res.authData.hasFlag(FLAGS.AT))
        assertFalse(res.authData.hasFlag(FLAGS.UV))
        assertEquals(65u, res.authData.flags)
        assertEquals(64, res.getCredentialID().size)
        assertNull(res.authData.extensions)
        assertEquals("packed", res.fmt)
        assertEquals(COSEAlgorithmIdentifier.ES256.value, res.getPackedAttestationStatement().alg)
    }

    @Test
    fun makeCredentialWithPIN() {
        val pin = "something"

        client.setPIN(pin, pinProtocol = 1u)

        val token = client.getPinToken(pin, pinProtocol = 1u)

        val res = client.makeCredential(rpId = rpId, userDisplayName = userDisplayName, pinToken = token, pinProtocol = 1u)

        assertTrue(res.authData.hasFlag(FLAGS.UV))
    }

    @Test
    fun overwritingMatchingUsers() {
        val userId = Random.Default.nextBytes(10)

        val res1 = client.makeCredential(rpId = rpId, userDisplayName = "Bob", userId = userId, discoverableCredential = true)
        val res2 = client.makeCredential(rpId = rpId, userDisplayName = "Fred", userId = userId, discoverableCredential = true)

        assertNotEquals(res1.getCredentialID(), res2.getCredentialID())

        val e = assertFailsWith<CTAPError> {
            client.getAssertions(
                clientDataHash = Random.nextBytes(32),
                rpId = rpId,
                allowList = listOf(
                    PublicKeyCredentialDescriptor(id = res1.getCredentialID()),
                ),
            )
        }
        assertEquals(CTAPResponse.NO_CREDENTIALS.value, e.code)
    }
}
