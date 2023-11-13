package us.q3q.fidok.ctap.commands

import kotlin.test.Test
import kotlin.test.assertEquals

class PackedAttestationSerializerTest {
    private val minimal =
        PackedAttestationStatement(42, byteArrayOf(0x00, 0x09, 0x00, 0x13))
    private val maximal =
        PackedAttestationStatement(42, byteArrayOf(0x00, 0x09, 0x00, 0x13), arrayOf(byteArrayOf(0x29, 0x28)))

    @Test
    fun maximalRoundTrip() {
        val roundTripped = Utils.roundTripSerialize(maximal, PackedAttestationStatement.serializer())

        assertEquals(maximal, roundTripped)
    }

    @Test
    fun minimalRoundTrip() {
        val roundTripped = Utils.roundTripSerialize(minimal, PackedAttestationStatement.serializer())

        assertEquals(minimal, roundTripped)
    }
}
