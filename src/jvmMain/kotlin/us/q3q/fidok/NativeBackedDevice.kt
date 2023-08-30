package us.q3q.fidok

import co.touchlab.kermit.Logger
import us.q3q.fidok.ctap.Device
import java.nio.ByteBuffer

class NativeBackedDevice(libraryPath: String, private val deviceNumber: Int) : NativeLibraryUser(libraryPath), Device {
    override fun sendBytes(bytes: ByteArray): ByteArray {
        val capacity = 32768
        val output = ByteBuffer.allocateDirect(capacity)
        val capacityBacking = ByteBuffer.allocateDirect(4)
        capacityBacking.putInt(capacity)
        capacityBacking.rewind()

        val res = native.fidok_send_bytes(
            deviceNumber,
            toBB(bytes),
            bytes.size,
            output,
            capacityBacking,
        )
        if (res != 0) {
            throw RuntimeException("Native device communication failed")
        }

        capacityBacking.rewind()

        val responseSize = capacityBacking.getInt(0)

        Logger.d { "Received $responseSize response bytes from native" }

        return toBA(output, responseSize)
    }
}
