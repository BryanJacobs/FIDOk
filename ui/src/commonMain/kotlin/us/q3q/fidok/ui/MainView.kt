package us.q3q.fidok.ui

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import us.q3q.fidok.crypto.NullCryptoProvider
import us.q3q.fidok.ctap.AuthenticatorDevice
import us.q3q.fidok.ctap.CTAPClient
import us.q3q.fidok.ctap.FIDOkLibrary

@Composable
fun MainView(
    library: FIDOkLibrary,
    devices: List<AuthenticatorDevice>? = null,
    onListDevices: () -> Unit = {},
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        val devs = devices
        val pinResponseChannel by remember { mutableStateOf(Channel<String?>()) }
        val coroutineScope = rememberCoroutineScope()
        var chosenClient by remember { mutableStateOf<CTAPClient?>(null) }
        var pinRequested by remember { mutableStateOf(false) }
        Button(onClick = {
            chosenClient = null
            onListDevices()
        }) {
            Text(
                if (devs == null) {
                    "Click to list"
                } else {
                    "There ${if (devs.size == 1) "is" else "are"} " +
                        "${devs.size} device${if (devs.size == 1) "" else "s"}"
                },
            )
        }
        val client = chosenClient
        if (devs != null && client == null) {
            MultipleAuthenticatorDisplay(devs, onSelect = {
                chosenClient = library.ctapClient(it, collectPinFromUser = {
                    pinRequested = true
                    val pin = pinResponseChannel.receive()
                    pinRequested = false
                    pin
                })
            })
        }
        if (client != null) {
            AuthenticatorManagementDisplay(client) {
                try {
                    coroutineScope.coroutineContext.cancelChildren()
                } catch (_: IllegalStateException) {
                }
                pinRequested = false
            }
        }
        if (pinRequested) {
            /*Dialog(onDismissRequest = {
                coroutineScope.launch {
                    pinResponseChannel.send(null)
                }
            }) {*/
            PinEntry {
                coroutineScope.coroutineContext.ensureActive()
                coroutineScope.launch {
                    pinResponseChannel.send(it)
                }
            }
            // }
        }
    }
}

@Preview
@Composable
fun MainViewPreview() {
    MainView(FIDOkLibrary.init(NullCryptoProvider()))
}
