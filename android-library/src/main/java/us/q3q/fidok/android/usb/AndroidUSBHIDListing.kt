package us.q3q.fidok.usb

import android.app.PendingIntent
import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbManager
import co.touchlab.kermit.Logger

const val USB_TIMEOUT_MS = 15000

@OptIn(ExperimentalStdlibApi::class)
class AndroidUSBHIDListing {
    companion object {
        fun listDevices(
            c: Context,
            permissionIntent: PendingIntent?,
        ): List<AndroidUSBHIDDevice> {
            val m = c.getSystemService(UsbManager::class.java)

            val ret = arrayListOf<AndroidUSBHIDDevice>()

            val devices = m.deviceList

            Logger.v { "There are ${devices.size} USB devices to examine" }

            for (entry in devices.entries) {
                val deviceAddr = entry.key
                val d = entry.value

                Logger.v { "Device entry: ${d.deviceName}" }

                val potentiallyValidInterfaceIDs = arrayListOf<Int>()

                for (interfaceNumber in 0..<d.interfaceCount) {
                    var foundInEndpoint = false
                    var foundOutEndpoint = false

                    val interf = d.getInterface(interfaceNumber)
                    if (interf.interfaceClass != UsbConstants.USB_CLASS_HID || interf.interfaceSubclass != 0 ||
                        interf.interfaceProtocol != 0
                    ) {
                        continue
                    }
                    if (interf.endpointCount < 2) {
                        // Need one in and one out endpoint...
                        Logger.v { "Interface $interfaceNumber ignored because it has too few interfaces" }
                        continue
                    }

                    for (endpointNumber in 0..<interf.endpointCount) {
                        val endPoint = interf.getEndpoint(endpointNumber)
                        if (endPoint.direction == UsbConstants.USB_DIR_OUT) {
                            foundOutEndpoint = true
                        } else if (endPoint.direction == UsbConstants.USB_DIR_IN) {
                            foundInEndpoint = true
                        }
                    }

                    if (foundInEndpoint && foundOutEndpoint) {
                        // This might be an okay interface for us...
                        potentiallyValidInterfaceIDs.add(interfaceNumber)
                        Logger.d { "Interface $interfaceNumber on $deviceAddr might be FIDO" }
                    } else {
                        Logger.v { "Interface $interfaceNumber ignored because we couldn't find an in+out endpoint" }
                    }
                }

                if (potentiallyValidInterfaceIDs.isEmpty()) {
                    Logger.v { "Found no likely interfaces on device $deviceAddr" }
                    continue
                }

                if (!m.hasPermission(d)) {
                    if (permissionIntent == null) {
                        Logger.w { "Missing permission for $deviceAddr, but not given a permission Intent" }
                        continue
                    }
                    Logger.i { "Requesting permission for $deviceAddr" }
                    m.requestPermission(d, permissionIntent)
                    continue // FIXME
                }

                val conn = m.openDevice(d)

                var fidoInterfaceNumber: Int? = null

                val outBufferSize = 64u // TODO: see if this can be read without walking the whole raw descriptor

                // Get HID report descriptor - from whichever interface...
                for (interfaceNumber in potentiallyValidInterfaceIDs) {
                    val response = ByteArray(outBufferSize.toInt())

                    val ifaceObj = d.getInterface(interfaceNumber)
                    if (!conn.claimInterface(ifaceObj, true)) {
                        continue
                    }

                    Logger.v { "Trying $interfaceNumber" }

                    val bytesTransferred =
                        conn.controlTransfer(
                            UsbConstants.USB_DIR_IN or 0x01,
                            0x00000006,
                            0x00002200 + interfaceNumber,
                            interfaceNumber,
                            response,
                            response.size,
                            USB_TIMEOUT_MS,
                        )
                    Logger.v {
                        "Transferred $bytesTransferred report descriptor bytes " +
                            "from $deviceAddr interface $interfaceNumber : ${response[0].toHexString()}"
                    }

                    conn.releaseInterface(ifaceObj)

                    if (bytesTransferred > 3) {
                        // We got the report descriptor, finally
                        if (response[0] == 0x06.toByte() &&
                            response[1] == 0xD0.toByte() &&
                            response[2] == 0xF1.toByte()
                        ) {
                            // This smells like a FIDO interface!!!
                            fidoInterfaceNumber = interfaceNumber
                            Logger.i { "Found FIDO device $deviceAddr (interface $interfaceNumber)" }
                            break
                        }
                    }

                    if (fidoInterfaceNumber != null) {
                        break
                    }
                }

                if (fidoInterfaceNumber == null) {
                    conn.close()
                    continue
                }

                ret.add(AndroidUSBHIDDevice(m, deviceAddr, fidoInterfaceNumber))
            }

            return ret
        }
    }
}
