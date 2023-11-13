package us.q3q.fidok.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbManager
import co.touchlab.kermit.Logger
import us.q3q.fidok.ctap.AuthenticatorDevice
import us.q3q.fidok.ctap.AuthenticatorTransport
import us.q3q.fidok.ctap.DeviceCommunicationException
import us.q3q.fidok.ctap.IncorrectDataException
import us.q3q.fidok.ctap.InvalidDeviceException
import us.q3q.fidok.hid.CTAPHID
import us.q3q.fidok.hid.CTAPHIDCommand

const val TIMEOUT_MS = 3000

@OptIn(ExperimentalUnsignedTypes::class, ExperimentalStdlibApi::class)
class AndroidUSBHIDDevice(
    private val manager: UsbManager,
    private val deviceAddr: String,
    private val interfaceNumber: Int,
) : AuthenticatorDevice {
    @Throws(DeviceCommunicationException::class)
    override fun sendBytes(bytes: ByteArray): ByteArray {
        Logger.v { "Sending ${bytes.size} bytes to device $deviceAddr" }

        val device =
            manager.deviceList[deviceAddr]
                ?: throw DeviceCommunicationException("Device $deviceAddr disappeared before write")

        if (!manager.hasPermission(device)) {
            throw DeviceCommunicationException("Cannot send to $deviceAddr - permission not granted")
        }

        if (interfaceNumber >= device.interfaceCount) {
            throw DeviceCommunicationException("Cannot send to $deviceAddr - interface $interfaceNumber too high")
        }

        val interfaceObject = device.getInterface(interfaceNumber)

        val conn = manager.openDevice(device)

        if (!conn.claimInterface(interfaceObject, true)) {
            conn.close()
            throw DeviceCommunicationException("Could not claim interface $interfaceNumber on device $deviceAddr")
        }

        try {
            var inEndpoint = interfaceObject.getEndpoint(0)
            var outEndpoint = interfaceObject.getEndpoint(1)
            if (inEndpoint.direction != UsbConstants.USB_DIR_IN) {
                // Whoops, swapped
                inEndpoint = outEndpoint
                outEndpoint = interfaceObject.getEndpoint(0)
            }
            if (inEndpoint.direction != UsbConstants.USB_DIR_IN) {
                throw InvalidDeviceException("A USB device with two out endpoints?!")
            }

            val transferBuffer = ByteArray(inEndpoint.maxPacketSize)

            return CTAPHID.sendAndReceive({
                Logger.v { "About to send ${it.size} USB byte(s) to $deviceAddr: ${it.toHexString()}" }

                val toSend = it.toByteArray()

                val sent = conn.bulkTransfer(outEndpoint, toSend, toSend.size, TIMEOUT_MS)
                if (sent != toSend.size) {
                    throw DeviceCommunicationException("Send to $deviceAddr failed to deliver ${toSend.size} bytes: got $sent")
                }

                Logger.v { "Sent $sent USB bytes to $deviceAddr" }
            }, {
                Logger.v { "Attempting to receive ${transferBuffer.size} bytes from $deviceAddr" }

                val received = conn.bulkTransfer(inEndpoint, transferBuffer, transferBuffer.size, TIMEOUT_MS)

                if (received < 0) {
                    throw IncorrectDataException("Unexpected number of bytes received from device $deviceAddr: $received")
                }

                Logger.v { "Read $received USB bytes from $deviceAddr : ${transferBuffer.toHexString()}" }

                transferBuffer.copyOfRange(0, received).toUByteArray()
            }, CTAPHIDCommand.CBOR, bytes.toUByteArray(), outEndpoint.maxPacketSize).toByteArray()
        } finally {
            conn.releaseInterface(interfaceObject)
            conn.close()
        }
    }

    override fun getTransports(): List<AuthenticatorTransport> {
        return listOf(AuthenticatorTransport.USB)
    }

    override fun toString(): String {
        return "USB Device $deviceAddr Interface $interfaceNumber"
    }
}
