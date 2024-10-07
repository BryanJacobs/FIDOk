package us.q3q.fidok

import us.q3q.fidok.crypto.AESKey
import us.q3q.fidok.crypto.P256Point
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@OptIn(ExperimentalStdlibApi::class)
class BotanCryptoProviderTest {
    @Test
    fun sha256Hash() {
        val c = BotanCryptoProvider()

        val input = "1234".encodeToByteArray()

        val res = c.sha256(input).hash
        assertEquals(32, res.size)
        assertEquals("03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4", res.toHexString())
    }

    @Test
    fun random() {
        val c = BotanCryptoProvider()

        val a = c.secureRandom(32)
        val b = c.secureRandom(32)

        assertEquals(32, a.size)
        assertEquals(32, b.size)
        assertNotEquals(a.toHexString(), b.toHexString())
    }

    @Test
    fun ecdhKey() {
        val c = BotanCryptoProvider()

        val x = "69FF6B7CB423AC4CEC7133501EF7A2739FBD49893F91AA9F91533FF3CBE5EE9E".hexToByteArray()
        val y = "4BD9E6BD87DEA506A9F7350D2BE386896907471D07F36A15B2942E6D594EAD21".hexToByteArray()

        val otherPoint = P256Point(x, y)
        val state = c.ecdhKeyAgreementInit(otherPoint)
        val key1 = c.ecdhKeyAgreementKDF(state, otherPoint, false, byteArrayOf(), byteArrayOf())
        val key2 = c.ecdhKeyAgreementKDF(state, otherPoint, false, byteArrayOf(), byteArrayOf())

        assertNotEquals(state.localPublicX.toHexString(), state.localPublicY.toHexString())
        assertEquals(32, key1.bytes.size)
        assertEquals(key1.bytes.toList(), key2.bytes.toList())
    }

    @Test
    fun aes256RoundTrip() {
        val c = BotanCryptoProvider()

        val data = Random.nextBytes(64)
        val key = AESKey(Random.nextBytes(32), Random.nextBytes(16))

        val enc = c.aes256CBCEncrypt(data, key)
        val res = c.aes256CBCDecrypt(enc, key)
        assertNotEquals(data.toList(), enc.toList())
        assertEquals(data.toList(), res.toList())
    }

    @Test
    fun aes128RoundTrip() {
        val c = BotanCryptoProvider()

        val data = Random.nextBytes(64)
        val key = AESKey(Random.nextBytes(16), Random.nextBytes(16))

        val enc = c.aes128CBCEncrypt(data, key)
        val res = c.aes128CBCDecrypt(enc, key)
        assertNotEquals(data.toList(), enc.toList())
        assertEquals(data.toList(), res.toList())
    }

    @Test
    fun aesIVsMatter() {
        val c = BotanCryptoProvider()

        val data = Random.nextBytes(64)
        val key = Random.nextBytes(32)
        val iv1 = Random.nextBytes(16)
        val iv2 = Random.nextBytes(16)

        val enc1 = c.aes256CBCEncrypt(data, AESKey(key, iv1))
        val enc2 = c.aes256CBCEncrypt(data, AESKey(key, iv2))
        assertNotEquals(enc1.toList(), enc2.toList())
    }
}
