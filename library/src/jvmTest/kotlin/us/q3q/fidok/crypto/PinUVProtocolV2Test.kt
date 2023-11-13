package us.q3q.fidok.crypto

import us.q3q.fidok.PureJVMCryptoProvider
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PinUVProtocolV2Test {
    private lateinit var pinUVProtocolV2: PinUVProtocolV2
    private lateinit var platformKey: KeyAgreementPlatformKey

    @BeforeTest
    fun setUpPP() {
        pinUVProtocolV2 = PinUVProtocolV2(PureJVMCryptoProvider())
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

        val sig = pinUVProtocolV2.authenticate(platformKey, data)
        assertTrue(pinUVProtocolV2.verify(platformKey, data, sig))
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun encryptionRoundTripsWithPlatformKey() {
        val data = Random.nextBytes(80)

        val encrypted = pinUVProtocolV2.encrypt(platformKey, data)
        val decrypted = pinUVProtocolV2.decrypt(platformKey, encrypted)

        assertEquals(data.toHexString(), decrypted.toHexString())
    }

    @Test
    fun versionIsTwo() {
        assertEquals(2u, pinUVProtocolV2.getVersion())
    }
}
