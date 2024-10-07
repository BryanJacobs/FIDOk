package us.q3q.fidok.app

import android.Manifest
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.livedata.observeAsState
import androidx.core.app.ActivityCompat
import androidx.lifecycle.MutableLiveData
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import us.q3q.fidok.PureJVMCryptoProvider
import us.q3q.fidok.app.intent.ACTION_USB_PERMISSION
import us.q3q.fidok.app.intent.usbPermissionIntentReceiver
import us.q3q.fidok.app.theme.FidoKTheme
import us.q3q.fidok.ble.AndroidBLEServer
import us.q3q.fidok.ctap.AuthenticatorDevice
import us.q3q.fidok.ctap.AuthenticatorListing
import us.q3q.fidok.ctap.FIDOkLibrary
import us.q3q.fidok.nfc.AndroidNFCDevice
import us.q3q.fidok.ui.MainView
import us.q3q.fidok.usb.AndroidUSBHIDListing

class MainActivity : ComponentActivity() {
    private var nfcAdapter: NfcAdapter? = null

    private val techDiscoveredIntentFilter = IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
    private val supportedTechClassNames =
        arrayOf(
            IsoDep::class.java.name,
        )

    private val blePermissionRequestLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            Logger.d { "BLE permissions request result: $it" }
        }

    private var nfcPendingIntent: PendingIntent? = null
    private var deviceListLive = MutableLiveData<List<AuthenticatorDevice>?>(null)
    private var usbPermissionIntent: PendingIntent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            Logger.e("Uncaught exception", throwable)
            Toast.makeText(applicationContext, throwable.message, Toast.LENGTH_LONG).show()
            recreate()
        }

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        Logger.setMinSeverity(Severity.Verbose)

        val permissionIntent =
            PendingIntent.getBroadcast(
                this,
                0,
                Intent(ACTION_USB_PERMISSION),
                PendingIntent.FLAG_IMMUTABLE,
            )
        usbPermissionIntent = permissionIntent
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        registerReceiver(usbPermissionIntentReceiver, filter)

        val nfcIntent =
            Intent(this, javaClass).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        nfcPendingIntent =
            PendingIntent.getActivity(
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

        val doListUSB: () -> List<AuthenticatorDevice> = {
            AndroidUSBHIDListing.listDevices(applicationContext, permissionIntent)
        }

        /*val doListBLE: suspend () -> List<AuthenticatorDevice> = bleList@{
            if (bluetoothAdapter != null) {
                if (!bluetoothAdapter.isEnabled) {
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
                    Logger.i { "BLE scan not started because adapter disabled; trying to enable" }
                    return listOf()
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
                            return listOf(
                                AndroidBLEDevice(this@MainActivity, result.device),
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
        }*/

        val library =
            FIDOkLibrary.init(
                PureJVMCryptoProvider(),
                authenticatorAccessors =
                    listOf(
                        object : AuthenticatorListing {
                            override fun listDevices(): List<AuthenticatorDevice> {
                                return doListUSB()
                            }
                        },
                    ),
            )

        val doStartServer = {
            Logger.v { "About to check BLE permissions" }

            if (ActivityCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.BLUETOOTH_CONNECT,
                ) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Logger.v { "Trying to request BLE permissions" }

                blePermissionRequestLauncher.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_ADVERTISE,
                    ),
                )

                Logger.v { "BLE permissions request launched" }
            } else {
                Logger.v { "BLE permissions check passed, moving into server" }

                AndroidBLEServer(this@MainActivity, bluetoothManager, bluetoothAdapter).startBLEServer()
            }
        }

        setContent {
            FidoKTheme {
                /*Surface(modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground
                    ) {*/
                MainView(library = library, devices = deviceListLive.observeAsState().value, onListDevices = {
                    deviceListLive.postValue(library.listDevices().toList())
                })
                // }
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
            deviceListLive.postValue(listOf(device))
        }
    }
}
