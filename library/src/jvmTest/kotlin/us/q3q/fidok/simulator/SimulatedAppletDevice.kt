package us.q3q.fidok.simulator

import com.licel.jcardsim.base.Simulator
import us.q3q.fido2.VSim
import us.q3q.fidok.ctap.Device
import us.q3q.fidok.pcsc.CTAPPCSC

class SimulatedAppletDevice : Device {

    private val sim: Simulator

    init {
        sim = VSim.startForegroundSimulator()
        VSim.installApplet(sim, byteArrayOf())
    }

    fun softReset() {
        VSim.softReset(sim)
    }

    override fun sendBytes(bytes: ByteArray): ByteArray {
        return CTAPPCSC.sendAndReceive(bytes, selectApplet = false, useExtendedMessages = true) {
            VSim.transmitCommand(sim, it)
        }
    }
}
