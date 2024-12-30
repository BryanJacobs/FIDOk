package us.q3q.fidok.ui

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import us.q3q.fidok.FIDOkCallbacks
import us.q3q.fidok.crypto.NullCryptoProvider
import us.q3q.fidok.ctap.AuthenticatorDevice
import us.q3q.fidok.ctap.CTAPClient
import us.q3q.fidok.ctap.FIDOkLibrary

@Composable
fun MainView(
    library: FIDOkLibrary,
    devices: List<AuthenticatorDevice>? = null,
    onListDevices: () -> Unit = {},
    mobileView: Boolean = false,
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        val pinResponseChannel by remember { mutableStateOf(Channel<String?>()) }
        val coroutineScope = rememberCoroutineScope()
        var chosenClient by remember { mutableStateOf<CTAPClient?>(null) }
        var pinRequested by remember { mutableStateOf(false) }
        var browserMode by remember { mutableStateOf(false) }
        var exception by remember { mutableStateOf<Exception?>(null) }

        library.setCallbacks(
            object : FIDOkCallbacks {
                override suspend fun collectPin(client: CTAPClient?): String? {
                    pinRequested = true
                    val pin = pinResponseChannel.receive()
                    pinRequested = false
                    return pin
                }

                override suspend fun exceptionEncountered(ex: Exception): Boolean {
                    exception = ex
                    return true
                }
            },
        )

        val gottenException = exception
        if (gottenException != null) {
            Text(
                gottenException.message ?: "Unknown exception ${gottenException.javaClass.name}",
                color = Color.Red,
                fontWeight = FontWeight.Bold,
            )
        }

        if (pinRequested) {
            PinEntry {
                coroutineScope.coroutineContext.ensureActive()
                coroutineScope.launch {
                    pinResponseChannel.send(it)
                }
            }
        }

        if (browserMode) {
            WebBrowser(library, mobileView)
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(onClick = {
                    chosenClient = null
                    onListDevices()
                }) {
                    Text("List/Manage Authenticators")
                }
                Button(onClick = {
                    browserMode = true
                }) {
                    Text("Web Browser")
                }
            }
            val client = chosenClient
            if (devices != null && client == null) {
                MultipleAuthenticatorDisplay(devices, onSelect = {
                    chosenClient = library.ctapClient(it)
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
        }
    }
}

@Preview
@Composable
fun MainViewPreview() {
    MainView(FIDOkLibrary.init(NullCryptoProvider()))
}
