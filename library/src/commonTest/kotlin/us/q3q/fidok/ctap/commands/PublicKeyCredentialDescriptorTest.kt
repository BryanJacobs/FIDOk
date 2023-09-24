package us.q3q.fidok.ctap.commands

import us.q3q.fidok.ctap.AuthenticatorTransport
import us.q3q.fidok.ctap.commands.Utils.Companion.roundTripSerialize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PublicKeyCredentialDescriptorTest {

    @Test
    fun roundTripSerializationBare() {
        val pk = PublicKeyCredentialDescriptor(type = "foo", id = byteArrayOf(0x50))
        val roundTripped = roundTripSerialize(pk, PublicKeyCredentialDescriptor.serializer())

        assertEquals(pk, roundTripped)
    }

    @Test
    fun roundTripSerializationWithFields() {
        val pk = PublicKeyCredentialDescriptor(type = "foo", id = byteArrayOf(0x50), transports = listOf("bar", "baz"))
        val roundTripped = roundTripSerialize(pk, PublicKeyCredentialDescriptor.serializer())

        assertEquals(pk, roundTripped)
        assertTrue(roundTripped.transports?.contains("bar") ?: false)
    }

    @Test
    fun getKnownTransports() {
        val pk = PublicKeyCredentialDescriptor(type = "foo", id = byteArrayOf(0x50), transports = listOf("bar", "nfc"))
        val known = pk.getKnownTransports()
        assertNotNull(known)
        assertEquals(1, known.size)
        assertEquals(AuthenticatorTransport.NFC, known[0])
    }
}
