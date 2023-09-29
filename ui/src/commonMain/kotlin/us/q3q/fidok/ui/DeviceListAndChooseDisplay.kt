package us.q3q.fidok.ui

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import us.q3q.fidok.ctap.AuthenticatorDevice
import us.q3q.fidok.ctap.AuthenticatorTransport

@Composable
fun DeviceListAndChooseDisplay(
    deviceList: List<AuthenticatorDevice>?,
    onListUSBReq: () -> Unit = {},
    onListBLEReq: () -> Unit = {},
    onStartServer: () -> Unit = {},
    manageReq: (d: AuthenticatorDevice) -> Unit = {},
) {
    Column {
        Row {
            Button(onClick = onListUSBReq) {
                Text("List USB")
            }
            Button(onClick = onListBLEReq) {
                Text("List BLE")
            }
            Button(onClick = onStartServer) {
                Text("BLE Server")
            }
        }
        if (deviceList != null) {
            MultipleAuthenticatorDisplay(deviceList, onSelect = manageReq)
        }
    }
}

@Preview
@Composable
internal fun DeviceListAndChooseDisplayPreviewEmpty() {
    DeviceListAndChooseDisplay(null)
}

@Preview
@Composable
internal fun DeviceListAndChooseDisplayPreview() {
    DeviceListAndChooseDisplay(
        deviceList = listOf(
            object : AuthenticatorDevice {
                override fun sendBytes(bytes: ByteArray) = byteArrayOf()
                override fun getTransports(): List<AuthenticatorTransport> =
                    listOf(AuthenticatorTransport.NFC, AuthenticatorTransport.SMART_CARD)
                override fun toString(): String = "FirstDevice"
            },
            object : AuthenticatorDevice {
                override fun sendBytes(bytes: ByteArray) = byteArrayOf()
                override fun getTransports(): List<AuthenticatorTransport> = listOf(AuthenticatorTransport.USB)
                override fun toString(): String = "SecondDevice"
            },
        ),
    )
}
