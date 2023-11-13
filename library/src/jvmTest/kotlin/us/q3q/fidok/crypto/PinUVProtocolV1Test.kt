package us.q3q.fidok.crypto

import us.q3q.fidok.PureJVMCryptoProvider
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PinUVProtocolV1Test {
    private lateinit var pinUVProtocolV1: PinUVProtocolV1
    private lateinit var platformKey: KeyAgreementPlatformKey

    @BeforeTest
    fun setUpPP() {
        pinUVProtocolV1 = PinUVProtocolV1(PureJVMCryptoProvider())
        platformKey =
            KeyAgreementPlatformKey(
                Random.nextBytes(32),
                Random.nextBytes(32),
                Random.nextBytes(32),
                Random.nextBytes(32),
                Random.nextBytes(32),
            )
    }

    @Test
    fun authenticationsAreValid() {
        val data = Random.nextBytes(85)

        val sig = pinUVProtocolV1.authenticate(platformKey, data)
        assertTrue(pinUVProtocolV1.verify(platformKey, data, sig))
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun encryptionRoundTripsWithPlatformKey() {
        val data = Random.nextBytes(80)

        val encrypted = pinUVProtocolV1.encrypt(platformKey, data)
        val decrypted = pinUVProtocolV1.decrypt(platformKey, encrypted)

        assertEquals(data.toHexString(), decrypted.toHexString())
    }

    @Test
    fun versionIsOne() {
        assertEquals(1u, pinUVProtocolV1.getVersion())
    }
}
