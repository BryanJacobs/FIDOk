package us.q3q.fidok.ctap.commands

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalStdlibApi::class)
class ClientPinGetKeyAgreementCommandTest {

    @Test
    fun canSerialize() {
        val cmd = ClientPinCommand.getKeyAgreement(2u)

        val enc = CTAPCBOREncoder()
        enc.encodeSerializableValue(CtapCommand.serializer(), cmd)
        val res = enc.getBytes()

        assertEquals("06a201020202", res.toHexString())
    }
}
