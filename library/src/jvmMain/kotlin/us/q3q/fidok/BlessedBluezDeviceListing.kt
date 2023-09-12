package us.q3q.fidok

import co.touchlab.kermit.Logger
import com.welie.blessed.BluetoothCentralManager
import com.welie.blessed.BluetoothCentralManagerCallback
import com.welie.blessed.BluetoothCommandStatus
import com.welie.blessed.BluetoothPeripheral
import com.welie.blessed.ScanResult
import us.q3q.fidok.ctap.CTAPClient
import java.util.UUID

const val FIDO_BLE_SERVICE_UUID = "0000fffd-0000-1000-8000-00805f9b34fb"
const val FIDO_CONTROL_POINT_ATTRIBUTE = "f1d0fff1-deaa-ecee-b42f-c9ba7ed623bb"
const val FIDO_STATUS_ATTRIBUTE = "f1d0fff2-deaa-ecee-b42f-c9ba7ed623bb"
const val FIDO_CONTROL_POINT_LENGTH_ATTRIBUTE = "f1d0fff3-deaa-ecee-b42f-c9ba7ed623bb"
const val FIDO_SERVICE_REVISION_BITFIELD_ATTRIBUTE = "f1d0fff4-deaa-ecee-b42f-c9ba7ed623bb"
const val FIDO_SERVICE_REVISION_ATTRIBUTE = "00002a28-0000-1000-8000-00805f9b34fb"

class BlessedBluezDeviceListing {
    companion object {
        private var central: BluetoothCentralManager? = null
        private var devices: HashMap<String, BlessedBluezDevice> = hashMapOf()

        fun start() {
            if (central == null) {
                val bluetoothCentralManagerCallback: BluetoothCentralManagerCallback =
                    object : BluetoothCentralManagerCallback() {
                        override fun onDiscoveredPeripheral(peripheral: BluetoothPeripheral, scanResult: ScanResult) {
                            Logger.i { "Found BLE peripheral ${peripheral.name}" }
                            central?.stopScan()
                            devices[peripheral.address] = BlessedBluezDevice(central!!, peripheral)
                        }

                        override fun onDisconnectedPeripheral(
                            peripheral: BluetoothPeripheral,
                            status: BluetoothCommandStatus,
                        ) {
                            Logger.i { "Disconnected BLE peripheral ${peripheral.name}" }
                            devices.remove(peripheral.address)
                            super.onDisconnectedPeripheral(peripheral, status)
                        }
                    }

                val newCentralManager = BluetoothCentralManager(bluetoothCentralManagerCallback)
                newCentralManager.stopScan()
                Logger.d { "Starting scan..." }
                central = newCentralManager
                newCentralManager.scanForPeripheralsWithServices(arrayOf(UUID.fromString(FIDO_BLE_SERVICE_UUID)))
            }
        }

        fun list(): List<BlessedBluezDevice> {
            return devices.values.toList()
        }
    }
}

fun main() {
    BlessedBluezDeviceListing.start()
    var devices: List<BlessedBluezDevice>
    while (true) {
        devices = BlessedBluezDeviceListing.list()
        println(devices)
        if (devices.isNotEmpty()) {
            break
        }
        Thread.sleep(1000L)
    }

    val device = devices[0]
    val info = CTAPClient(device).getInfo()

    println(info)
}
