package us.q3q.fidok.ui

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActionScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.unit.dp

@Composable
fun PinEntry(onSet: (pin: String) -> Unit = {}) {
    var heldPIN by remember { mutableStateOf("") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Enter PIN", modifier = Modifier.padding(16.dp))

            val innerDoSend = {
                onSet(heldPIN)
            }
            val doSend: KeyboardActionScope.() -> Unit = {
                innerDoSend()
            }

            OutlinedTextField(
                value = heldPIN,
                placeholder = {
                    Text("Authenticator PIN")
                },
                singleLine = true,
                onValueChange = {
                    heldPIN = it
                },
                keyboardActions = KeyboardActions(
                    onDone = doSend,
                    onSend = doSend,
                    onGo = doSend,
                ),
                modifier = Modifier.onKeyEvent {
                    if (it.key == Key.Enter) {
                        innerDoSend()
                        true
                    } else {
                        false
                    }
                },
            )
            Button(onClick = innerDoSend) {
                Text("Send PIN")
            }
        }
    }
}

@Composable
@Preview
internal fun PinEntryPreview() {
    PinEntry {}
}
