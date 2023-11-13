package us.q3q.fidok.simulator.ctap

import us.q3q.fidok.simulator.SimulationTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class TestGetInfo : SimulationTest() {
    @Test
    fun getsInfo() {
        val res = client.getInfo()

        assertContains(res.versions, "FIDO_2_1")
        assertEquals(512u, res.uvModality)
        assertEquals(2u, res.maxRPIDsForSetMinPINLength)
    }
}
