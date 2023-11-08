package us.q3q.fidok

import co.touchlab.kermit.Logger
import us.q3q.fidok.ctap.AuthenticatorDevice
import us.q3q.fidok.ctap.AuthenticatorTransport
import us.q3q.fidok.ctap.DeviceCommunicationException
import java.nio.ByteBuffer

/**
 * An [AuthenticatorDevice] implementation backed by native code.
 *
 * This allows using platform-specific [AuthenticatorDevice] implementations from the JVM, where no
 * JVM-hosted implementation exists.
 */
class NativeBackedDevice(libraryPath: String, private val deviceNumber: Int) : NativeLibraryUser(libraryPath), AuthenticatorDevice {
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
            throw DeviceCommunicationException("Native device communication failed")
        }

        capacityBacking.rewind()

        val responseSize = capacityBacking.getInt(0)

        Logger.d { "Received $responseSize response bytes from native" }

        return toBA(output, responseSize)
    }

    override fun getTransports(): List<AuthenticatorTransport> {
        // FIXME: need to pass this through to the native side
        return emptyList()
    }
}
