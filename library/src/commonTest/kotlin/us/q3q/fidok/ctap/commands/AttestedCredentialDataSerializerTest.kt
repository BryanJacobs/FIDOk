package us.q3q.fidok.ctap.commands

import us.q3q.fidok.ctap.data.AttestedCredentialData
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class AttestedCredentialDataSerializerTest {
    @Test
    fun roundTrips() {
        val cred =
            AttestedCredentialData(
                Random.nextBytes(16),
                Random.nextBytes(32),
                COSEKey(2, -7, 1, Random.nextBytes(32), Random.nextBytes(32)),
            )
        val roundTripped = Utils.roundTripSerialize(cred, AttestedCredentialData.serializer())

        assertEquals(cred, roundTripped)
    }
}
