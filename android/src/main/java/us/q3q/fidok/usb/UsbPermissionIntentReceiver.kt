package us.q3q.fidok.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import co.touchlab.kermit.Logger

const val ACTION_USB_PERMISSION = "us.q3q.fidok.usb.USB_PERMISSION"

val usbPermissionIntentReceiver =
    object : BroadcastReceiver() {
        override fun onReceive(
            context: Context,
            intent: Intent,
        ) {
            if (ACTION_USB_PERMISSION == intent.action) {
                synchronized(this) {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        Logger.w { "Permission granted for device $device" }
                        device?.apply {
                            // call method to set up device communication
                        }
                    } else {
                        Logger.w { "Permission denied for device $device" }
                    }
                }
            }
        }
    }
