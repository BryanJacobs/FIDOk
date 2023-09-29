package us.q3q.fidok.ui

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import us.q3q.fidok.ctap.AuthenticatorDevice
import us.q3q.fidok.ctap.AuthenticatorTransport

@Composable
fun MultipleAuthenticatorDisplay(
    devices: List<AuthenticatorDevice>,
    onSelect: (d: AuthenticatorDevice) -> Unit = {},
) {
    Column {
        Text("Found ${devices.size} authenticator${if (devices.size != 1) "s" else ""}")
        devices.map {
            OneAuthenticatorDisplay(it, onSelect = {
                onSelect(it)
            })
        }
    }
}

@Composable
@Preview
internal fun multipleAuthenticatorDisplayPreviewNone() {
    MultipleAuthenticatorDisplay(
        listOf(),
    )
}

@Composable
@Preview
internal fun multipleAuthenticatorDisplayPreviewOne() {
    MultipleAuthenticatorDisplay(
        listOf(
            object : AuthenticatorDevice {
                override fun sendBytes(bytes: ByteArray) = byteArrayOf()
                override fun getTransports() = listOf(AuthenticatorTransport.SMART_CARD)
                override fun toString() = "Some Authenticator"
            },
        ),
    )
}
