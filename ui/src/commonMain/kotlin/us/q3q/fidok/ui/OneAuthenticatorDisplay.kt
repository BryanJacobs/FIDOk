package us.q3q.fidok.ui

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import us.q3q.fidok.ctap.AuthenticatorDevice
import us.q3q.fidok.ctap.AuthenticatorTransport

@Composable
fun OneAuthenticatorDisplay(
    device: AuthenticatorDevice,
    onSelect: () -> Unit = {},
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        for (transport in device.getTransports()) {
            InnerCard(transport.value)
        }
        Text(device.toString(), modifier = Modifier.padding(3.dp))
        Button(onClick = onSelect) {
            Text("Manage")
        }
    }
}

@Composable
@Preview
internal fun oneAuthenticatorDisplayPreview() {
    OneAuthenticatorDisplay(
        object : AuthenticatorDevice {
            override fun sendBytes(bytes: ByteArray) = byteArrayOf()

            override fun getTransports(): List<AuthenticatorTransport> = listOf(AuthenticatorTransport.USB, AuthenticatorTransport.NFC)

            override fun toString() = "Magic Authenticator"
        },
    )
}
