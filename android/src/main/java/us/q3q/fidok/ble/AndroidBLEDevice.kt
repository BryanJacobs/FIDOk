package us.q3q.fidok.ble

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import co.touchlab.kermit.Logger
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import us.q3q.fidok.FIDO_BLE_SERVICE_UUID
import us.q3q.fidok.FIDO_CONTROL_POINT_ATTRIBUTE
import us.q3q.fidok.FIDO_CONTROL_POINT_LENGTH_ATTRIBUTE
import us.q3q.fidok.FIDO_SERVICE_REVISION_BITFIELD_ATTRIBUTE
import us.q3q.fidok.FIDO_STATUS_ATTRIBUTE
import us.q3q.fidok.ctap.Device
import java.util.*
import kotlin.experimental.and

@OptIn(ExperimentalUnsignedTypes::class)
class AndroidBLEDevice(private val ctx: Context, private val device: BluetoothDevice) : Device {

    private val readResult = Channel<ByteArray>(Channel.BUFFERED)
    private val connectResult = Channel<Boolean>()

    private var gatt: BluetoothGatt? = null
    private var cpLen: Int = 0
    private var revBF: ByteArray? = null

    override fun sendBytes(bytes: ByteArray): ByteArray {
        val address = device.address
        if (gatt == null) {
            runBlocking {
                connect(address)
            }
        }

        val g = gatt ?: throw IllegalArgumentException("BLE not connected to device $address")

        checkPermission(address)

        val s = g.getService(UUID.fromString(FIDO_BLE_SERVICE_UUID))
        val controlPointChara = s.getCharacteristic(UUID.fromString(FIDO_CONTROL_POINT_ATTRIBUTE))
            ?: throw IllegalStateException("Could not get FIDO control point for $address")

        val statusAttribute = s.getCharacteristic(UUID.fromString(FIDO_STATUS_ATTRIBUTE))
        if (!g.setCharacteristicNotification(statusAttribute, true)) {
            throw IllegalStateException("Could not enable BLE notifications on $address")
        }

        val ret = CTAPBLE.sendAndReceive({
            controlPointChara.setValue(it.toByteArray())
            if (!g.writeCharacteristic(controlPointChara)) {
                throw IllegalStateException("Could not write message to BLE $address")
            }
        }, {
            runBlocking {
                readResult.receive().toUByteArray()
            }
        }, CTAPBLECommand.MSG, bytes.toUByteArray(), cpLen).toByteArray()

        if (!g.setCharacteristicNotification(statusAttribute, false)) {
            throw IllegalStateException("Could not disable BLE notifications on $address")
        }

        return ret
    }

    private suspend fun connect(address: String?) {
        checkPermission(address)

        val g = device.connectGatt(
            ctx,
            true,
            object : BluetoothGattCallback() {
                override fun onCharacteristicChanged(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    value: ByteArray,
                ) {
                    super.onCharacteristicChanged(gatt, characteristic, value)
                    readResult.trySend(value)
                }

                override fun onCharacteristicRead(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    value: ByteArray,
                    status: Int,
                ) {
                    Logger.v { "BLE characteristic read status: $status" }
                    super.onCharacteristicRead(gatt, characteristic, value, status)
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        readResult.trySend(value)
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                    super.onServicesDiscovered(gatt, status)
                    Logger.d { "BLE services discovered on '$address'" }
                    connectResult.trySend(true)
                }

                override fun onConnectionStateChange(g: BluetoothGatt?, status: Int, newState: Int) {
                    Logger.d { "BLE state change on '$address': $newState" }
                    super.onConnectionStateChange(g, status, newState)
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        try {
                            g?.discoverServices()
                        } catch (e: SecurityException) {
                            throw IllegalStateException(e)
                        }
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        gatt = null
                    }
                }
            },
        )

        connectResult.receive()

        val service = g.getService(UUID.fromString(FIDO_BLE_SERVICE_UUID))
            ?: throw IllegalStateException("BLE device '$address' has no FIDO BLE service")

        val cpLenChara = service.getCharacteristic(UUID.fromString(FIDO_CONTROL_POINT_LENGTH_ATTRIBUTE))
            ?: throw IllegalStateException("BLE device '$address' has no control point length characteristic")
        if (!g.readCharacteristic(cpLenChara)) {
            throw IllegalStateException("BLE device '$address' could not read control point length")
        }

        val cpLenArr = readResult.receive()
        if (cpLenArr.size != 2) {
            throw IllegalStateException("Control point length was not itself two bytes long: ${cpLenArr.size}")
        }
        cpLen = cpLenArr[0] * 256 + cpLenArr[1]
        if (cpLen < 20 || cpLen > 512) {
            throw IllegalStateException("Control point length out of bounds: $cpLen")
        }

        val srevChara = service.getCharacteristic(UUID.fromString(FIDO_SERVICE_REVISION_BITFIELD_ATTRIBUTE))
            ?: throw IllegalStateException("BLE device '$address' has no service revision bitfield attribute")
        if (!g.readCharacteristic(srevChara)) {
            throw IllegalStateException("BLE device '$address' could not read service revision chara")
        }

        val rev = readResult.receive()
        if (rev.isEmpty() || (rev[0] and 0x20.toByte()) != 0x20.toByte()) {
            throw IllegalStateException("BLE device '$address' does not support FIDO2-BLE")
        }
        revBF = rev

        srevChara.setValue(byteArrayOf(0x20))
        if (!g.writeCharacteristic(srevChara)) {
            throw IllegalStateException("BLE device '$address' could not be set to FIDO2-BLE")
        }

        gatt = g
    }

    private fun checkPermission(address: String?) {
        if (ActivityCompat.checkSelfPermission(
                ctx,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            //    ActivityCompat#requestPermissions
            throw IllegalStateException("Permission for BLE connections is denied to $address")
        }
    }

    override fun toString(): String {
        return "BLE Device $device"
    }
}
