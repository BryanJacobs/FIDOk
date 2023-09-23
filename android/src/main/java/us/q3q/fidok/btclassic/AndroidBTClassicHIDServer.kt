package us.q3q.fidok.btclassic

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import java.util.UUID

class AndroidBTClassicHIDServer(private val ctx: Context, private val bluetoothAdapter: BluetoothAdapter) {
    fun startHIDServer() {
        if (!bluetoothAdapter.isEnabled) {
            // val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            // startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            // TODO
            throw IllegalStateException("Bluetooth not enabled!")
        }

        if (ActivityCompat.checkSelfPermission(
                ctx,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            throw IllegalStateException("Bluetooth not given permission!")
        }

        val listener = bluetoothAdapter.listenUsingRfcommWithServiceRecord(
            "FIDO Authenticator",
            UUID.fromString("486b38c2-71d4-4a80-b215-d7b6554fa59c"),
        )

        val socket = listener.accept()
        if (!socket.isConnected) {
            socket.connect()
        }

        // FIXME: finish implementing
        throw NotImplementedError()
    }
}
