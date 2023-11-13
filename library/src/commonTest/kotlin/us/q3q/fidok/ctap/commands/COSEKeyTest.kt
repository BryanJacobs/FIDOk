package us.q3q.fidok.ctap.commands

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class COSEKeyTest {
    @Test
    fun roundTrip() {
        val key = COSEKey(2, -7, 1, Random.nextBytes(32), Random.nextBytes(32))
        val roundTripped = Utils.roundTripSerialize(key, COSEKey.serializer())

        assertEquals(key, roundTripped)
    }
}
