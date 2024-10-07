package us.q3q.fidok

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import us.q3q.fidok.ble.CTAPBLE
import us.q3q.fidok.ble.CTAPBLECommand
import us.q3q.fidok.ble.FIDO_BLE_SERVICE_UUID
import us.q3q.fidok.ble.FIDO_CONTROL_POINT_ATTRIBUTE
import us.q3q.fidok.ble.FIDO_CONTROL_POINT_LENGTH_ATTRIBUTE
import us.q3q.fidok.ble.FIDO_SERVICE_REVISION_BITFIELD_ATTRIBUTE
import us.q3q.fidok.ble.FIDO_STATUS_ATTRIBUTE
import us.q3q.fidok.ctap.AuthenticatorDevice
import us.q3q.fidok.ctap.AuthenticatorTransport
import us.q3q.fidok.ctap.DeviceCommunicationException
import us.q3q.fidok.ctap.IncorrectDataException
import us.q3q.fidok.ctap.InvalidDeviceException
import kotlin.time.Duration.Companion.seconds

private val READ_TIMEOUT = 5.seconds

/**
 * An [AuthenticatorDevice] implementation for Bluetooth Low Energy (BLE) on Linux.
 *
 * This uses the BlueZ bluetooth stack (over D-Bus) via a library called bluez_inc.
 * This can only work on Linux.
 *
 * @property name The human-readable name of the BLE device
 * @property listing A reference to a BLE manager stack for communicating with the device
 */
class LinuxBluetoothDevice(
    val address: String,
    val name: String,
    private val listing: LinuxBluetoothDeviceListing,
) : AuthenticatorDevice {
    init {
        listing.ref()
    }

    protected fun finalize() {
        listing.unref()
    }

    private var connected: Boolean = false
    private var cpLen: Int = 0

    @Throws(
        DeviceCommunicationException::class,
        CancellationException::class,
        IncorrectDataException::class,
        InvalidDeviceException::class,
    )
    private suspend fun connect() {
        if (!connected) {
            try {
                connected = listing.connect(address)
            } catch (e: Exception) {
                throw DeviceCommunicationException(e.message)
            }

            val cpLenArr =
                listing.read(address, FIDO_BLE_SERVICE_UUID, FIDO_CONTROL_POINT_LENGTH_ATTRIBUTE)
                    ?: throw DeviceCommunicationException("Could not read FIDO control point length")

            if (cpLenArr.size != 2) {
                throw IncorrectDataException("Control point length was not itself two bytes long: ${cpLenArr.size}")
            }

            cpLen = cpLenArr[0].toByte() * 256 + cpLenArr[1].toByte()
            if (cpLen < 20 || cpLen > 512) {
                throw IncorrectDataException("Control point length on '$name' out of bounds: $cpLen")
            }

            val rev =
                listing.read(address, FIDO_BLE_SERVICE_UUID, FIDO_SERVICE_REVISION_BITFIELD_ATTRIBUTE)
                    ?: throw DeviceCommunicationException("BLE device '$name' could not read service revision chara")
            if (rev.isEmpty() || (rev[0] and 0x20u.toUByte()) != 0x20u.toUByte()) {
                throw InvalidDeviceException("BLE device '$name' does not support FIDO2-BLE")
            }

            listing.write(address, FIDO_BLE_SERVICE_UUID, FIDO_SERVICE_REVISION_BITFIELD_ATTRIBUTE, ubyteArrayOf(0x20u), true)
                ?: throw DeviceCommunicationException("BLE device '$name' could not be set to FIDO2-BLE")
        }
    }

    @Throws(DeviceCommunicationException::class)
    override fun sendBytes(bytes: ByteArray): ByteArray {
        return runBlocking {
            connect()

            val readResult = Channel<UByteArray>(Channel.BUFFERED)

            listing.setNotify(address, FIDO_BLE_SERVICE_UUID, FIDO_STATUS_ATTRIBUTE, readResult)

            Logger.v { "Notify ready, beginning CTAP send/recv loop on '$name'" }

            try {
                return@runBlocking CTAPBLE.sendAndReceive({
                    runBlocking {
                        if (listing.write(
                                address,
                                FIDO_BLE_SERVICE_UUID,
                                FIDO_CONTROL_POINT_ATTRIBUTE,
                                it,
                                true,
                            ) == null
                        ) {
                            throw DeviceCommunicationException("Could not write message to peripheral '$name'")
                        }
                    }
                }, {
                    runBlocking {
                        withTimeout(READ_TIMEOUT) {
                            readResult.receive()
                        }
                    }
                }, CTAPBLECommand.MSG, bytes.toUByteArray(), cpLen).toByteArray()
            } finally {
                listing.setNotify(address, FIDO_BLE_SERVICE_UUID, FIDO_STATUS_ATTRIBUTE, null)
            }
        }
    }

    override fun getTransports(): List<AuthenticatorTransport> {
        return listOf(AuthenticatorTransport.BLE)
    }

    override fun toString(): String {
        return "BT:$name ($address)"
    }
}
