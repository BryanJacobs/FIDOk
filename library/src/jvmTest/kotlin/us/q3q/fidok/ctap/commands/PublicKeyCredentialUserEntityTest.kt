package us.q3q.fidok.ctap.commands

import us.q3q.fidok.ctap.commands.Utils.Companion.roundTripSerialize
import kotlin.test.Test
import kotlin.test.assertEquals

class PublicKeyCredentialUserEntityTest {

    @Test
    fun roundTrips() {
        val u = PublicKeyCredentialUserEntity(id = byteArrayOf(0x1A), displayName = "Bobby")

        val roundTripped = roundTripSerialize(u, PublicKeyCredentialUserEntity.serializer())

        assertEquals(u, roundTripped)
    }
}
