package us.q3q.fidok.cable

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import us.q3q.fidok.PureJVMCryptoProvider
import us.q3q.fidok.ctap.Library
import java.lang.IllegalArgumentException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CaBLESupportTest {

    companion object {
        @BeforeAll
        @JvmStatic
        fun initLibrary() {
            Library.init(PureJVMCryptoProvider())
        }
    }

    @Test
    fun decodeGoogleStaticRegisteredDomain() {
        val res = CaBLESupport.decodeTunnelServerDomain(0)

        assertEquals("cable.ua5v.com", res)
    }

    @Test
    fun decodeAppleStaticRegisteredDomain() {
        val res = CaBLESupport.decodeTunnelServerDomain(1)

        assertEquals("cable.auth.com", res)
    }

    @Test
    fun decodeInvalidStaticDomain() {
        assertFailsWith<IllegalArgumentException> {
            CaBLESupport.decodeTunnelServerDomain(128)
        }
    }

    @Test
    fun decodeHighNumberOrgDomain() {
        val res = CaBLESupport.decodeTunnelServerDomain(293)

        assertEquals("cable.72jvldr5gf2tb.org", res)
    }

    @Test
    fun decodeHighNumberNetDomain() {
        val res = CaBLESupport.decodeTunnelServerDomain(382)

        assertEquals("cable.v24ui6fwrqljd.net", res)
    }

    @Test
    fun testBuildingCaBLEURL() {
        val code = CaBLECode(
            publicKey = ByteArray(33) { 0xFE.toByte() },
            secret = ByteArray(16) { 0xCD.toByte() },
            knownTunnelServerDomains = 2u,
        )
        val url = CaBLESupport.buildCaBLEURL(code)

        assertTrue(url.startsWith("FIDO:/"))
        for (c in url.substring(6)) {
            assertTrue("0123456789".contains(c))
        }
    }
}
