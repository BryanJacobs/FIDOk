package us.q3q.fidok

import co.touchlab.kermit.Logger
import com.welie.blessed.BluetoothCentralManager
import com.welie.blessed.BluetoothCommandStatus
import com.welie.blessed.BluetoothGattCharacteristic
import com.welie.blessed.BluetoothGattService
import com.welie.blessed.BluetoothPeripheral
import com.welie.blessed.BluetoothPeripheralCallback
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import us.q3q.fidok.ble.CTAPBLE
import us.q3q.fidok.ble.CTAPBLECommand
import us.q3q.fidok.ctap.Device
import java.lang.IllegalStateException
import java.util.UUID
import kotlin.experimental.and

@OptIn(ExperimentalStdlibApi::class, ExperimentalUnsignedTypes::class)
class BlessedBluezDevice(
    private val central: BluetoothCentralManager,
    private val peripheral: BluetoothPeripheral,
) : Device {
    var connected: Boolean = false

    val readResult = Channel<ByteArray>(Channel.BUFFERED)
    val connectResult = Channel<Boolean>()
    val bondResult = Channel<Boolean>()
    var cpLen: Int = 0
    var revBF: ByteArray? = null

    val callback: BluetoothPeripheralCallback =
        object : BluetoothPeripheralCallback() {
            override fun onBondingSucceeded(peripheral: BluetoothPeripheral) {
                Logger.d { "${peripheral.name} bond established" }
                bondResult.trySend(true)
            }

            override fun onBondLost(peripheral: BluetoothPeripheral) {
                Logger.i { "${peripheral.name} bond lost" }
                connected = false
            }

            override fun onServicesDiscovered(
                peripheral: BluetoothPeripheral,
                services: MutableList<BluetoothGattService>,
            ) {
                Logger.d { "${peripheral.name} services discovered" }
                connected = true
                connectResult.trySend(true)
            }

            override fun onCharacteristicUpdate(
                peripheral: BluetoothPeripheral,
                value: ByteArray?,
                characteristic: BluetoothGattCharacteristic,
                status: BluetoothCommandStatus,
            ) {
                Logger.v { "${peripheral.name} read result for ${characteristic.uuid}: ${value?.toHexString()}" }
                if (value != null) {
                    readResult.trySend(value)
                }
            }
        }

    private suspend fun connect() {
        if (!connected) {
            central.connectPeripheral(peripheral, callback)
            connectResult.receive()
            val service = peripheral.services.find {
                it.uuid.toString() == FIDO_BLE_SERVICE_UUID
            } ?: throw IllegalStateException("BLE device '${peripheral.name}' has no FIDO service")

            /*if (!peripheral.createBond(callback)) {
                throw IllegalStateException("Could not bond with BLE device ${peripheral.name}")
            }
            bondResult.receive()*/

            val cpLenChara = service.getCharacteristic(UUID.fromString(FIDO_CONTROL_POINT_LENGTH_ATTRIBUTE))
                ?: throw IllegalStateException("BLE device '${peripheral.name}' has no control point length characteristic")
            if (!peripheral.readCharacteristic(cpLenChara)) {
                throw IllegalStateException("BLE device '${peripheral.name}' could not read control point length")
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
                ?: throw IllegalStateException("BLE device '${peripheral.name}' has no service revision bitfield attribute")
            if (!peripheral.readCharacteristic(srevChara)) {
                throw IllegalStateException("BLE device '${peripheral.name}' could not read service revision chara")
            }

            val rev = readResult.receive()
            if (rev.isEmpty() || (rev[0] and 0x20.toByte()) != 0x20.toByte()) {
                throw IllegalStateException("BLE device '${peripheral.name}' does not support FIDO2-BLE")
            }
            revBF = rev

            if (!peripheral.writeCharacteristic(
                    srevChara,
                    byteArrayOf(0x20),
                    BluetoothGattCharacteristic.WriteType.WITH_RESPONSE,
                )
            ) {
                throw IllegalStateException("BLE device '${peripheral.name}' could not be set to FIDO2-BLE")
            }
        }
    }

    override fun sendBytes(bytes: ByteArray): ByteArray {
        runBlocking {
            connect()
        }

        val controlPointChara = peripheral.getCharacteristic(
            UUID.fromString(FIDO_BLE_SERVICE_UUID),
            UUID.fromString(FIDO_CONTROL_POINT_ATTRIBUTE),
        )
            ?: throw IllegalStateException("Could not get FIDO control point for ${peripheral.name}")

        peripheral.setNotify(
            UUID.fromString(FIDO_BLE_SERVICE_UUID),
            UUID.fromString(FIDO_STATUS_ATTRIBUTE),
            true,
        )

        return CTAPBLE.sendAndReceive({
            if (!peripheral.writeCharacteristic(controlPointChara, it.toByteArray(), BluetoothGattCharacteristic.WriteType.WITH_RESPONSE)) {
                throw IllegalStateException("Could not write message to peripheral ${peripheral.name}")
            }
        }, {
            runBlocking {
                readResult.receive().toUByteArray()
            }
        }, CTAPBLECommand.MSG, bytes.toUByteArray(), cpLen).toByteArray()
    }
}
