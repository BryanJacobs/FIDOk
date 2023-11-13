package us.q3q.fidok.simulator.ctap

import org.junit.jupiter.api.BeforeEach
import us.q3q.fidok.ctap.commands.COSEKey
import us.q3q.fidok.ctap.commands.PublicKeyCredentialDescriptor
import us.q3q.fidok.ctap.data.FLAGS
import us.q3q.fidok.simulator.SimulationTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalStdlibApi::class)
class GetAssertion : SimulationTest() {
    lateinit var cred: ByteArray
    lateinit var publicKey: COSEKey

    @BeforeEach
    fun makeCredential() {
        val credRes = client.makeCredential(rpId = rpId, userDisplayName = userDisplayName, userName = userName)
        cred = credRes.getCredentialID()
        publicKey = credRes.getCredentialPublicKey()
    }

    @Test
    fun basicGetAssertion() {
        val challenge = Random.nextBytes(32)

        val assertions =
            client.getAssertions(
                clientDataHash = challenge,
                rpId = rpId,
                allowList =
                    listOf(
                        PublicKeyCredentialDescriptor(id = cred),
                    ),
            )

        assertEquals(1, assertions.size)
        val assertion = assertions.first()
        assertNull(assertion.user)
        assertNull(assertion.numberOfCredentials)
        assertNull(assertion.largeBlobKey)
        assertEquals(cred.toHexString(), assertion.credential.id.toHexString())
        assertNull(assertion.authData.attestedCredentialData)
        assertTrue(assertion.authData.signCount < 64u)
        assertTrue(assertion.authData.hasFlag(FLAGS.USER_PRESENCE))
        assertFalse(assertion.authData.hasFlag(FLAGS.ATTESTED))
        assertFalse(assertion.authData.hasFlag(FLAGS.USER_VERIFICATION))

        client.validateAssertionSignature(assertion, challenge, publicKey)
    }
}
