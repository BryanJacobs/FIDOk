package us.q3q.fidok.simulator.ez

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import us.q3q.fidok.ez.EZHmac
import us.q3q.fidok.simulator.SimulationTest
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.assertNotEquals

class EZHmacTest : SimulationTest() {
    private lateinit var ezHmac: EZHmac

    @BeforeEach
    fun ready() {
        ezHmac = EZHmac(library, rpId)

        client.setPIN(TEST_PIN)
    }

    @Test
    fun roundTrips() {
        val toEncrypt = Random.nextBytes(12)

        val decrypted =
            runBlocking {
                val setup = ezHmac.setup()

                val encrypted = ezHmac.encrypt(setup, toEncrypt)
                ezHmac.decrypt(setup, encrypted)
            }

        assertContentEquals(toEncrypt, decrypted)
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun isConsistent() {
        val toEncrypt = Random.nextBytes(66)

        runBlocking {
            val setup = ezHmac.setup()

            val encrypted1 = ezHmac.encrypt(setup, toEncrypt)
            val encrypted2 = ezHmac.encrypt(setup, toEncrypt)

            assertNotEquals(encrypted1.toHexString(), encrypted2.toHexString())

            assertContentEquals(ezHmac.decrypt(setup, encrypted1), ezHmac.decrypt(setup, encrypted2))
        }
    }

    @Test
    fun rotationChangesSaltButLeavesData() {
        val toEncrypt = Random.nextBytes(32)
        val salt2 = Random.nextBytes(32)

        runBlocking {
            val setup = ezHmac.setup()

            val encrypted = ezHmac.encrypt(setup, toEncrypt)

            val rotation =
                ezHmac.decryptAndRotate(
                    setup,
                    previouslyEncryptedData = encrypted,
                    newSalt = salt2,
                )

            assertContentEquals(toEncrypt, rotation.first)
            assertContentEquals(toEncrypt, ezHmac.decrypt(setup, rotation.second, salt2))
        }
    }

    @Test
    fun rotationCanChangeDataToo() {
        val toEncrypt = Random.nextBytes(32)
        val replacementData = Random.nextBytes(32)
        val salt1 = Random.nextBytes(32)
        val salt2 = Random.nextBytes(32)

        runBlocking {
            val setup = ezHmac.setup()

            val encrypted = ezHmac.encrypt(setup, toEncrypt, salt = salt1)

            val rotation =
                ezHmac.decryptAndRotate(
                    setup,
                    previouslyEncryptedData = encrypted,
                    previousSalt = salt1,
                    newSalt = salt2,
                    newData = replacementData,
                )

            assertContentEquals(toEncrypt, rotation.first)
            assertContentEquals(replacementData, ezHmac.decrypt(setup, rotation.second, salt2))
        }
    }

    @Test
    fun differentSetupDifferentEncryption() {
        val toEncrypt = Random.nextBytes(5)

        runBlocking {
            val setup1 = ezHmac.setup()
            val setup2 = ezHmac.setup()

            val enc1 = ezHmac.encrypt(setup1, toEncrypt)

            assertThrows<EZHmac.InvalidKeyException> {
                runBlocking {
                    ezHmac.decrypt(setup2, enc1)
                }
            }
        }
    }

    @Test
    fun differentSaltsDifferentEncryption() {
        val toEncrypt = Random.nextBytes(33)

        runBlocking {
            val setup = ezHmac.setup()

            val enc2 = ezHmac.encrypt(setup, toEncrypt, Random.nextBytes(32))

            assertThrows<EZHmac.InvalidKeyException> {
                runBlocking {
                    ezHmac.decrypt(setup, enc2)
                }
            }
        }
    }
}
