package us.q3q.fidok.ctap.commands

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PackedAttestationSerializerTest {

    private val MINIMAL =
        PackedAttestationStatement(42, byteArrayOf(0x00, 0x09, 0x00, 0x13))
    private val MAXIMAL =
        PackedAttestationStatement(42, byteArrayOf(0x00, 0x09, 0x00, 0x13), arrayOf(byteArrayOf(0x29, 0x28)))

    @Test
    fun maximalRoundTrip() {
        val roundTripped = Utils.roundTripSerialize(MAXIMAL, PackedAttestationStatement.serializer())

        assertEquals(MAXIMAL, roundTripped)
    }

    @Test
    fun minimalRoundTrip() {
        val roundTripped = Utils.roundTripSerialize(MINIMAL, PackedAttestationStatement.serializer())

        assertEquals(MINIMAL, roundTripped)
    }
}
