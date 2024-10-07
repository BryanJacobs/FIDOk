package us.q3q.fidok.ctap.commands

import us.q3q.fidok.ctap.commands.Utils.Companion.roundTripSerialize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PublicKeyCredentialRpEntityTest {
    @Test
    fun roundTripSerializationWithFields() {
        val entity =
            PublicKeyCredentialRpEntity(
                id = "something",
                name = "bobby",
                icon = "data:foo",
            )
        val result = roundTripSerialize(entity, PublicKeyCredentialRpEntity.serializer())

        assertEquals(entity, result)
        @Suppress("DEPRECATION")
        assertEquals("data:foo", result.icon)
    }

    @Test
    fun roundTripSerializationBare() {
        val entity =
            PublicKeyCredentialRpEntity(
                id = "something",
            )
        val result = roundTripSerialize(entity, PublicKeyCredentialRpEntity.serializer())

        assertEquals(entity, result)
        assertEquals("something", result.id)
        @Suppress("DEPRECATION")
        assertNull(result.icon)
    }
}
