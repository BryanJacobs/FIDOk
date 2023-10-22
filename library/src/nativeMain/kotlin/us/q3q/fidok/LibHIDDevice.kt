
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
import us.q3q.fidok.ctap.AuthenticatorDevice
import us.q3q.fidok.ctap.AuthenticatorListing
import us.q3q.fidok.ctap.AuthenticatorTransport
import us.q3q.fidok.ctap.DeviceCommunicationException
import us.q3q.fidok.ctap.InvalidDeviceException
import us.q3q.fidok.hid.CTAPHID.Companion.sendAndReceive
import us.q3q.fidok.hid.CTAPHIDCommand

const val FIDO_USAGE_PAGE = 0xF1D0u
const val FIDO_USAGE = 0x0001u

const val TIMEOUT = 5000

@OptIn(ExperimentalForeignApi::class)
class LibHIDDevice(private val path: String, private val packetSize: Int) : AuthenticatorDevice {
    companion object : AuthenticatorListing {
        init {
            hid_init()
        }

        override fun listDevices(): List<LibHIDDevice> {
            val foundDevices = arrayListOf<LibHIDDevice>()

            val enumeratedDevicesHandle = hid_enumerate(0x00u, 0x00u)
            try {
                var iterationHandle = enumeratedDevicesHandle
                while (iterationHandle != null) {
                    if (iterationHandle.pointed.usage_page.toUInt() == FIDO_USAGE_PAGE &&
                        iterationHandle.pointed.usage.toUInt() == FIDO_USAGE
                    ) {
                        val path = iterationHandle.pointed.path?.toKString() ?: throw InvalidDeviceException("Path to device is null")
                        Logger.i("Found device $path")
                        foundDevices.add(LibHIDDevice(path, 64))
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

        override fun providedTransports(): List<AuthenticatorTransport> {
            return listOf(AuthenticatorTransport.USB)
        }
    }

    override fun getTransports(): List<AuthenticatorTransport> {
        return providedTransports()
    }

    private fun readOnePacket(handle: CPointer<hid_device>): ByteArray {
        Logger.d("Attempting to read from device $path")
        memScoped {
            val packet = allocArray<UByteVar>(packetSize)
            val read = hid_read_timeout(handle, packet, packetSize.convert(), TIMEOUT)
            if (read == 0) {
                throw DeviceCommunicationException("Timed out reading from HID device $path")
            }
            if (read < 0) {
                throw DeviceCommunicationException("Failed to read from HID device $path: $read")
            }
            val ret = ByteArray(read)
            for (i in ret.indices) {
                ret[i] = packet[i].convert()
            }
            return ret
        }
    }

    private fun sendOnePacket(handle: CPointer<hid_device>, bytes: ByteArray) {
        if (bytes.size != packetSize) {
            throw IllegalArgumentException("Requested byte length: ${bytes.size} is not equal to packet size $packetSize")
        }

        memScoped {
            val packet = this.allocArray<UByteVar>(packetSize + 1)
            packet[0] = 0x00u // LibHID wants the descriptor number here
            for (i in bytes.indices) {
                packet[i + 1] = bytes[i].convert()
            }
            // Logger.v(bytes.toHexString())
            val written = hid_write(handle, packet, (packetSize + 1).convert())
            if (written != packetSize + 1) {
                throw DeviceCommunicationException("Failed to write to HID device $path: $written bytes written of ${packetSize + 1}")
            }
        }
    }

    override fun sendBytes(bytes: ByteArray): ByteArray {
        val handle = hid_open_path(path) ?: throw InvalidDeviceException("Failed to open HID device $path")
        try {
            return sendAndReceive(
                { sendOnePacket(handle, it) },
                { readOnePacket(handle) },
                CTAPHIDCommand.CBOR,
                bytes,
                packetSize = packetSize,
            )
        } finally {
            hid_close(handle)
        }
    }

    override fun toString(): String {
        return "libhid:$path"
    }
}
