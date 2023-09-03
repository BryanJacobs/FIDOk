
package us.q3q.fidok

import co.touchlab.kermit.Logger
import hidapi.hid_close
import hidapi.hid_device
import hidapi.hid_enumerate
import hidapi.hid_free_enumeration
import hidapi.hid_init
import hidapi.hid_open_path
import hidapi.hid_read_timeout
import hidapi.hid_write
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString
import us.q3q.fidok.ctap.Device
import us.q3q.fidok.hid.CTAPHID.Companion.sendAndReceive
import us.q3q.fidok.hid.CTAPHIDCommand
import us.q3q.fidok.hid.PACKET_SIZE

const val FIDO_USAGE_PAGE = 0xF1D0u
const val FIDO_USAGE = 0x0001u

const val TIMEOUT = 5000

@OptIn(ExperimentalForeignApi::class)
class LibHIDDevice(private val path: String) : Device {
    companion object {
        init {
            hid_init()
        }

        fun list(): List<LibHIDDevice> {
            val foundDevices = arrayListOf<LibHIDDevice>()

            val enumeratedDevicesHandle = hid_enumerate(0x00u, 0x00u)
            try {
                var iterationHandle = enumeratedDevicesHandle
                while (iterationHandle != null) {
                    if (iterationHandle.pointed.usage_page.toUInt() == FIDO_USAGE_PAGE &&
                        iterationHandle.pointed.usage.toUInt() == FIDO_USAGE
                    ) {
                        val path = iterationHandle.pointed.path?.toKString() ?: throw RuntimeException("Path to device is null")
                        Logger.i("Found device $path")
                        foundDevices.add(LibHIDDevice(path))
                    }
                    iterationHandle = iterationHandle.pointed.next
                }
            } finally {
                if (enumeratedDevicesHandle != null) {
                    hid_free_enumeration(enumeratedDevicesHandle)
                }
            }

            return foundDevices
        }
    }

    private fun readOnePacket(handle: CPointer<hid_device>): UByteArray {
        Logger.d("Attempting to read from device $path")
        memScoped {
            val packet = allocArray<UByteVar>(PACKET_SIZE + 1)
            val read = hid_read_timeout(handle, packet, (PACKET_SIZE + 1).convert(), TIMEOUT)
            if (read == 0) {
                throw RuntimeException("Timed out reading from HID device $path")
            }
            if (read < 0) {
                throw RuntimeException("Failed to read from HID device $path: $read")
            }
            val ret = UByteArray(read)
            for (i in ret.indices) {
                ret[i] = packet[i]
            }
            return ret
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun sendOnePacket(handle: CPointer<hid_device>, bytes: UByteArray) {
        if (bytes.size != PACKET_SIZE + 1) {
            throw IllegalArgumentException("Requested byte length: ${bytes.size} is not equal to packet size $PACKET_SIZE")
        }
        memScoped {
            val packet = this.allocArray<UByteVar>(PACKET_SIZE + 1)
            for (i in bytes.indices) {
                packet[i] = bytes[i]
            }
            Logger.v(bytes.toHexString())
            val written = hid_write(handle, packet, bytes.size.convert())
            if (written != bytes.size) {
                throw RuntimeException("Failed to write to HID device $path: $written bytes written")
            }
        }
    }

    override fun sendBytes(bytes: ByteArray): ByteArray {
        val handle = hid_open_path(path) ?: throw RuntimeException("Failed to open HID device $path")
        try {
            return sendAndReceive(
                { sendOnePacket(handle, it) },
                { readOnePacket(handle) },
                CTAPHIDCommand.CBOR,
                bytes.toUByteArray(),
            ).toByteArray()
        } finally {
            hid_close(handle)
        }
    }
}
