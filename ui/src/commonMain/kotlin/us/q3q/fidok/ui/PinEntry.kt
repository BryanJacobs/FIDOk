package us.q3q.fidok.ui

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PinEntry(onSet: (pin: String) -> Unit = {}) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Enter PIN", modifier = Modifier.padding(16.dp))

            SubmittableText(placeholder = {
                Text("Authenticator PIN")
            }, buttonContent = {
                Text("Send PIN")
            }) {
                onSet(it)
            }
        }
    }
}

@Composable
@Preview
internal fun PinEntryPreview() {
    PinEntry {}
}
