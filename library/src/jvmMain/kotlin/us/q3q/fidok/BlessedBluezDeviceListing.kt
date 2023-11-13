package us.q3q.fidok

import co.touchlab.kermit.Logger
import com.welie.blessed.BluetoothCentralManager
import com.welie.blessed.BluetoothCentralManagerCallback
import com.welie.blessed.BluetoothCommandStatus
import com.welie.blessed.BluetoothPeripheral
import com.welie.blessed.ScanResult
import us.q3q.fidok.ble.FIDO_BLE_SERVICE_UUID
import us.q3q.fidok.ctap.AuthenticatorListing
import us.q3q.fidok.ctap.AuthenticatorTransport
import java.util.UUID

/**
 * Code for listing Bluetooth Low Energy Authenticators using Blessed BlueZ.
 *
 * Authenticators will be returned if they're available via a BLE scan.
 */
class BlessedBluezDeviceListing {
    companion object : AuthenticatorListing {
        private var central: BluetoothCentralManager? = null
        private var devices: HashMap<String, BlessedBluezDevice> = hashMapOf()

        override fun providedTransports(): List<AuthenticatorTransport> {
            return listOf(AuthenticatorTransport.BLE)
        }

        fun start() {
            if (central == null) {
                val bluetoothCentralManagerCallback: BluetoothCentralManagerCallback =
                    object : BluetoothCentralManagerCallback() {
                        override fun onDiscoveredPeripheral(
                            peripheral: BluetoothPeripheral,
                            scanResult: ScanResult,
                        ) {
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
                Logger.d { "Starting BLE device scan..." }
                central = newCentralManager
                newCentralManager.scanForPeripheralsWithServices(arrayOf(UUID.fromString(FIDO_BLE_SERVICE_UUID)))
            }
        }

        fun stop() {
            central?.stopScan()
            devices.clear()
            central = null
        }

        override fun listDevices(): List<BlessedBluezDevice> {
            return devices.values.toList()
        }
    }
}
