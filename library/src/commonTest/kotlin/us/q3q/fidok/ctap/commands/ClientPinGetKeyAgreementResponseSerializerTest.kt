package us.q3q.fidok.ctap.commands

import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalStdlibApi::class)
class ClientPinGetKeyAgreementResponseSerializerTest {
    @Test
    fun decodeRealisticResponse() {
        val hex =
            "a101a50102033818200121582088ace42990cfc003311ba0c4473563b3745e956a7ca95506fd9a1e60b85" +
                "ad6b82258205306501388a6411355ab91d4fb384500aa9216221f2d87c8f008c5fca35edecb"
        val decoder = CTAPCBORDecoder(hex.hexToByteArray())
        val res = decoder.decodeSerializableValue(ClientPinGetKeyAgreementResponse.serializer())

        assertEquals(-25, res.key.alg)
        assertEquals(1, res.key.crv)
        assertEquals(2, res.key.kty)
    }
}
