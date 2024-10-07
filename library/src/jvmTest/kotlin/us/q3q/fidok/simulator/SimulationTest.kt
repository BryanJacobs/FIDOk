package us.q3q.fidok.simulator

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import us.q3q.fidok.FIDOkCallbacks
import us.q3q.fidok.PureJVMCryptoProvider
import us.q3q.fidok.ctap.AuthenticatorDevice
import us.q3q.fidok.ctap.AuthenticatorListing
import us.q3q.fidok.ctap.CTAPClient
import us.q3q.fidok.ctap.FIDOkLibrary
import kotlin.random.Random

@OptIn(ExperimentalStdlibApi::class)
open class SimulationTest {
    private lateinit var device: SimulatedAppletDevice
    lateinit var client: CTAPClient
    lateinit var rpId: String
    lateinit var userDisplayName: String
    lateinit var userName: String

    companion object {
        lateinit var library: FIDOkLibrary

        const val TEST_PIN = "TEST_PIN"

        @JvmStatic
        @BeforeAll
        fun loadNativeLibrary() {
            library =
                FIDOkLibrary.init(
                    PureJVMCryptoProvider(),
                    callbacks =
                        object : FIDOkCallbacks {
                            override suspend fun collectPin(client: CTAPClient?): String? {
                                return TEST_PIN
                            }
                        },
                )
        }
    }

    @BeforeEach
    fun setup() {
        device = SimulatedAppletDevice()
        client = library.ctapClient(device)

        rpId = Random.nextBytes(Random.nextInt(1, 64)).toHexString()
        userDisplayName = Random.nextBytes(Random.nextInt(1, 64)).toHexString()
        userName = Random.nextBytes(Random.nextInt(1, 64)).toHexString()

        library.setAuthenticatorAccessors(
            listOf(
                object : AuthenticatorListing {
                    override suspend fun listDevices(): List<AuthenticatorDevice> {
                        return listOf(device)
                    }
                },
            ),
        )
    }
}
