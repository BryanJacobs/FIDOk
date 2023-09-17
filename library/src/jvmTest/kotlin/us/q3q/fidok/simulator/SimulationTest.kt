package us.q3q.fidok.simulator

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import us.q3q.fidok.ctap.CTAPClient
import us.q3q.fidok.loadNativeLibraryForPlatform
import kotlin.random.Random

@OptIn(ExperimentalStdlibApi::class)
open class SimulationTest {

    lateinit var device: SimulatedAppletDevice
    lateinit var client: CTAPClient
    lateinit var rpId: String
    lateinit var userDisplayName: String

    companion object {
        @JvmStatic
        @BeforeAll
        fun loadNativeLibrary() {
            loadNativeLibraryForPlatform()
        }
    }

    @BeforeEach
    fun setup() {
        device = SimulatedAppletDevice()
        client = CTAPClient(device)

        rpId = Random.nextBytes(Random.nextInt(1, 64)).toHexString()
        userDisplayName = Random.nextBytes(Random.nextInt(1, 64)).toHexString()
    }
}
