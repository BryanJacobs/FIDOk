package us.q3q.fidok.cable

import org.junit.jupiter.api.Test
import us.q3q.fidok.ctap.commands.CTAPCBORDecoder
import us.q3q.fidok.ctap.commands.CTAPCBOREncoder
import java.time.Instant
import kotlin.test.assertEquals

class CaBLECodeTest {
    @Test
    fun roundTrips() {
        val code =
            CaBLECode(
                publicKey = ByteArray(33) { 0xCD.toByte() },
                secret = ByteArray(16) { 0x13 },
                knownTunnelServerDomains = 9u,
                currentEpochSeconds = Instant.now().toEpochMilli().toULong(),
            )

        val encoder = CTAPCBOREncoder()
        encoder.encodeSerializableValue(CaBLECode.serializer(), code)
        val encoded = encoder.getBytes()

        val decoder = CTAPCBORDecoder(encoded)
        val roundTripped = decoder.decodeSerializableValue(CaBLECode.serializer())

        assertEquals(code, roundTripped)
    }
}
