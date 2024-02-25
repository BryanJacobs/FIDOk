package us.q3q.fidok.simulator.ctap

import us.q3q.fidok.ctap.CTAPError
import us.q3q.fidok.ctap.CTAPResponse
import us.q3q.fidok.ctap.commands.COSEAlgorithmIdentifier
import us.q3q.fidok.ctap.commands.PublicKeyCredentialDescriptor
import us.q3q.fidok.ctap.data.FLAGS
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
        val res = client.makeCredential(rpId = rpId, userDisplayName = userDisplayName, userName = userName)

        assertTrue(res.authData.signCount < 32u)
        assertTrue(res.authData.hasFlag(FLAGS.USER_PRESENCE))
        assertTrue(res.authData.hasFlag(FLAGS.ATTESTED))
        assertFalse(res.authData.hasFlag(FLAGS.USER_VERIFICATION))
        assertEquals(65u, res.authData.flags)
        assertEquals(112, res.getCredentialID().size)
        assertNull(res.authData.extensions)
        assertEquals("packed", res.fmt)
        assertEquals(COSEAlgorithmIdentifier.ES256.value, res.getPackedAttestationStatement().alg)
    }

    @Test
    fun makeCredentialWithPINProtocolOne() {
        val pin = "something"

        client.setPIN(pin, pinUvProtocol = 1u)

        val token = client.getPinToken(pin, pinUvProtocol = 1u)

        val res =
            client.makeCredential(
                rpId = rpId,
                userDisplayName = userDisplayName,
                userName = userName,
                pinUvToken = token,
            )

        assertTrue(res.authData.hasFlag(FLAGS.USER_VERIFICATION))
    }

    @Test
    fun makeCredentialWithPINProtocolTwo() {
        val pin = "something"

        client.setPIN(pin, pinUvProtocol = 2u)

        val token = client.getPinToken(pin, pinUvProtocol = 2u)

        val res =
            client.makeCredential(
                rpId = rpId,
                userDisplayName = userDisplayName,
                userName = userName,
                pinUvToken = token,
            )

        assertTrue(res.authData.hasFlag(FLAGS.USER_VERIFICATION))
    }

    @Test
    fun overwritingMatchingUsers() {
        val userId = Random.Default.nextBytes(10)

        val res1 =
            client.makeCredential(
                rpId = rpId,
                userDisplayName = "Bob",
                userName = userName,
                userId = userId,
                discoverableCredential = true,
            )
        val res2 =
            client.makeCredential(
                rpId = rpId,
                userDisplayName = "Fred",
                userName = userName,
                userId = userId,
                discoverableCredential = true,
            )

        assertNotEquals(res1.getCredentialID(), res2.getCredentialID())

        val e =
            assertFailsWith<CTAPError> {
                client.getAssertions(
                    clientDataHash = Random.nextBytes(32),
                    rpId = rpId,
                    allowList =
                        listOf(
                            PublicKeyCredentialDescriptor(id = res1.getCredentialID()),
                        ),
                )
            }
        assertEquals(CTAPResponse.NO_CREDENTIALS.value, e.code)
    }
}
