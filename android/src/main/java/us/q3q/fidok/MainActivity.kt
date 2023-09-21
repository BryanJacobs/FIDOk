package us.q3q.fidok

import android.Manifest
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.os.ParcelUuid
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.MutableLiveData
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import us.q3q.fidok.ble.AndroidBLEDevice
import us.q3q.fidok.ctap.CTAPClient
import us.q3q.fidok.ctap.Device
import us.q3q.fidok.ctap.commands.GetInfoResponse
import us.q3q.fidok.nfc.AndroidNFCDevice
import us.q3q.fidok.ui.InfoDisplay
import us.q3q.fidok.ui.theme.FidoKTheme
import us.q3q.fidok.usb.ACTION_USB_PERMISSION
import us.q3q.fidok.usb.AndroidUSBHIDListing
import us.q3q.fidok.usb.usbPermissionIntentReceiver

class MainActivity : ComponentActivity() {
    private var nfcAdapter: NfcAdapter? = null

    private val techDiscoveredIntentFilter = IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
    private val supportedTechClassNames = arrayOf(
        IsoDep::class.java.name,
    )

    private var nfcPendingIntent: PendingIntent? = null
    private var infoLive = MutableLiveData<GetInfoResponse?>(null)
    private var deviceListLive = MutableLiveData<List<Device>?>(null)
    private var usbPermissionIntent: PendingIntent? = null

    private val REQUEST_ENABLE_BT = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        Logger.setMinSeverity(Severity.Verbose)

        val permissionIntent = PendingIntent.getBroadcast(
            this,
            0,
            Intent(ACTION_USB_PERMISSION),
            PendingIntent.FLAG_IMMUTABLE,
        )
        usbPermissionIntent = permissionIntent
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        registerReceiver(usbPermissionIntentReceiver, filter)

        val nfcIntent = Intent(this, javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        nfcPendingIntent = PendingIntent.getActivity(
            this, 0, nfcIntent,
            PendingIntent.FLAG_MUTABLE,
        )

        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        var bluetoothAdapter = bluetoothManager.adapter
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            bluetoothAdapter = null
        }

        setContent {
            FidoKTheme {
                val deviceList: List<Device>? by deviceListLive.observeAsState()
                val info: GetInfoResponse? by infoLive.observeAsState()

                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column {
                        DeviceDisplayAndManip(
                            deviceList = deviceList,
                            onListUSBReq = {
                                val gottenDeviceList = AndroidUSBHIDListing.listDevices(applicationContext, permissionIntent)
                                deviceListLive.postValue(gottenDeviceList)
                            },
                            onListBLEReq = bleList@{
                                if (bluetoothAdapter != null) {
                                    if (!bluetoothAdapter.isEnabled) {
                                        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                                        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
                                        Logger.i { "BLE scan not started because adapter disabled; trying to enable" }
                                        return@bleList
                                    }

                                    val bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

                                    Logger.i { "Starting BLE scan" }
                                    val leCallback = object : ScanCallback() {
                                        override fun onScanResult(callbackType: Int, result: ScanResult?) {
                                            Logger.i { "Got BLE scan result $result" }
                                            if (result != null) {
                                                try {
                                                    bluetoothLeScanner.stopScan(this)
                                                } catch (e: SecurityException) {
                                                    Logger.e { "Was unable to stop self-initiated BLE scan: $e" }
                                                }
                                                deviceListLive.postValue(
                                                    listOf(
                                                        AndroidBLEDevice(this@MainActivity, result.device),
                                                    ),
                                                )
                                            }
                                            super.onScanResult(callbackType, result)
                                        }

                                        override fun onScanFailed(errorCode: Int) {
                                            Logger.w { "BLE scan failed with error $errorCode" }
                                            super.onScanFailed(errorCode)
                                        }
                                    }

                                    bluetoothLeScanner.startScan(
                                        listOf(
                                            ScanFilter.Builder()
                                                .setServiceUuid(ParcelUuid.fromString(FIDO_BLE_SERVICE_UUID))
                                                .build(),
                                        ),
                                        ScanSettings.Builder().build(),
                                        leCallback,
                                    )
                                } else {
                                    Logger.i { "BLE scanning not available" }
                                }
                            },
                            getInfoReq = {
                                val gottenInfo = CTAPClient(it).getInfo()
                                infoLive.postValue(gottenInfo)
                            },
                        )
                        InfoDisplay(info = info)
                    }
                }
            }
        }
    }

    private fun foregroundNfcDispatch() {
        val adapter = nfcAdapter
        if (nfcPendingIntent != null && adapter != null) {
            adapter.enableForegroundDispatch(
                this,
                nfcPendingIntent,
                arrayOf(techDiscoveredIntentFilter),
                arrayOf(supportedTechClassNames),
            )
        }
    }

    override fun onDestroy() {
        unregisterReceiver(usbPermissionIntentReceiver)
        super.onDestroy()
    }

    public override fun onPause() {
        super.onPause()
        Logger.v { "Pausing; disabling foreground NFC handling" }
        nfcAdapter?.disableForegroundDispatch(this)
    }

    public override fun onResume() {
        super.onResume()
        Logger.v { "Resuming; enabling foreground NFC handling" }
        foregroundNfcDispatch()
    }

    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Logger.v { "Main activity intent handler called for action ${intent.action}" }
        if (intent.action == "android.hardware.usb.action.USB_DEVICE_ATTACHED") {
            val listing = AndroidUSBHIDListing.listDevices(applicationContext, usbPermissionIntent)
            deviceListLive.postValue(listing)
        } else {
            val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
            Logger.i { "Detected tag $tag" }

            val device = AndroidNFCDevice(IsoDep.get(tag))
            val response = CTAPClient(device).getInfo()

            deviceListLive.postValue(listOf(device))
            infoLive.postValue(response)
        }
    }
}

@Composable
fun DeviceDisplayAndManip(
    deviceList: List<Device>?,
    onListUSBReq: () -> Unit = {},
    onListBLEReq: () -> Unit = {},
    getInfoReq: (d: Device) -> Unit = {},
) {
    Column {
        Row {
            Button(onClick = onListUSBReq) {
                Text("List USB")
            }
            Button(onClick = onListBLEReq) {
                Text("List BLE")
            }
        }
        if (deviceList != null) {
            DevicesDisplay(deviceList, getInfoReq = getInfoReq)
        }
    }
}

@Composable
fun DevicesDisplay(devices: List<Device>, getInfoReq: (d: Device) -> Unit) {
    Column {
        Text("Found ${devices.size} devices")
        devices.map {
            DeviceDisplay(it, getInfoReq = {
                getInfoReq(it)
            })
        }
    }
}

@Composable
fun DeviceDisplay(device: Device, getInfoReq: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(device.toString(), modifier = Modifier.padding(3.dp))
        Button(onClick = getInfoReq) {
            Text("Get Info")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DevicesDisplayPreviewEmpty() {
    DeviceDisplayAndManip(null)
}

@Preview(showBackground = true)
@Composable
fun DevicesDisplayPreview() {
    DeviceDisplayAndManip(
        deviceList = listOf(
            object : Device {
                override fun sendBytes(bytes: ByteArray) = byteArrayOf()
                override fun toString(): String = "FirstDevice"
            },
            object : Device {
                override fun sendBytes(bytes: ByteArray) = byteArrayOf()
                override fun toString(): String = "SecondDevice"
            },
        ),
    )
}
