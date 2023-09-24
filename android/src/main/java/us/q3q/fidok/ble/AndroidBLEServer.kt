package us.q3q.fidok.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.widget.Toast
import androidx.core.app.ActivityCompat
import co.touchlab.kermit.Logger
import us.q3q.fidok.FIDO_BLE_SERVICE_UUID
import us.q3q.fidok.FIDO_CONTROL_POINT_ATTRIBUTE
import us.q3q.fidok.FIDO_CONTROL_POINT_LENGTH_ATTRIBUTE
import us.q3q.fidok.FIDO_SERVICE_REVISION_ATTRIBUTE
import us.q3q.fidok.FIDO_SERVICE_REVISION_BITFIELD_ATTRIBUTE
import us.q3q.fidok.FIDO_STATUS_ATTRIBUTE
import us.q3q.fidok.ctap.DeviceCommunicationException
import java.util.*

class AndroidBLEServer(private val ctx: Context, private val manager: BluetoothManager, private val adapter: BluetoothAdapter) {

    @Throws(DeviceCommunicationException::class)
    fun startBLEServer() {
        Logger.i { "Requesting startup of BLE server" }

        if (
            ActivityCompat.checkSelfPermission(
                ctx,
                Manifest.permission.BLUETOOTH_ADVERTISE,
            ) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(
                ctx,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Logger.w { "BLE server cannot start due to missing BLE advertising/connect permission" }
            Toast.makeText(ctx, "Missing Bluetooth permission!", Toast.LENGTH_LONG).show()
            return
        }

        adapter.bluetoothLeAdvertiser.startAdvertising(
            AdvertiseSettings.Builder()
                // .setDiscoverable(true)
                .setConnectable(true)
                .build(),
            AdvertiseData.Builder()
                .addServiceUuid(ParcelUuid.fromString(FIDO_BLE_SERVICE_UUID))
                .build(),
            object : AdvertiseCallback() {
                override fun onStartFailure(errorCode: Int) {
                    super.onStartFailure(errorCode)
                }

                override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                    super.onStartSuccess(settingsInEffect)
                }
            },
        )

        Logger.v { "BLE advertising started" }

        val service = BluetoothGattService(UUID.fromString(FIDO_BLE_SERVICE_UUID), BluetoothGattService.SERVICE_TYPE_PRIMARY)

        service.addCharacteristic(
            BluetoothGattCharacteristic(
                UUID.fromString(FIDO_CONTROL_POINT_ATTRIBUTE),
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE,
            ),
        )
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                UUID.fromString(FIDO_STATUS_ATTRIBUTE),
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ,
            ),
        )
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                UUID.fromString(FIDO_CONTROL_POINT_LENGTH_ATTRIBUTE),
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ,
            ),
        )
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                UUID.fromString(FIDO_SERVICE_REVISION_BITFIELD_ATTRIBUTE),
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE,
            ),
        )
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                UUID.fromString(FIDO_SERVICE_REVISION_ATTRIBUTE),
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ,
            ),
        )

        Logger.v { "BLE service constructed; opening server" }

        val gattCallbackHandler = object : BluetoothGattServerCallback() {
            override fun onCharacteristicReadRequest(
                device: BluetoothDevice?,
                requestId: Int,
                offset: Int,
                characteristic: BluetoothGattCharacteristic?,
            ) {
                super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
                Logger.v { "Received read request for ${characteristic?.uuid}" }
            }

            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice?,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic?,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray?,
            ) {
                super.onCharacteristicWriteRequest(
                    device,
                    requestId,
                    characteristic,
                    preparedWrite,
                    responseNeeded,
                    offset,
                    value,
                )
                Logger.v { "Received write request for ${characteristic?.uuid}" }
            }
        }
        val server = manager.openGattServer(
            ctx,
            gattCallbackHandler,
        )

        Logger.v { "BLE server opened" }

        if (!server.addService(service)) {
            throw DeviceCommunicationException("Failed to add BLE service to GATT server")
        }

        Logger.i { "BLE server ready!" }
    }
}
