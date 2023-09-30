package us.q3q.fidok.ui

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActionScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.Button
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
fun SubmittableText(
    placeholder: @Composable (() -> Unit)? = null,
    buttonContent: @Composable (RowScope.() -> Unit)? = null,
    onSubmit: (text: String) -> Unit,
) {
    var text by remember { mutableStateOf("") }

    val kbdWrap: KeyboardActionScope.() -> Unit = {
        onSubmit(text)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            placeholder = placeholder,
            onValueChange = { text = it },
            singleLine = true,
            keyboardActions = KeyboardActions(
                onDone = kbdWrap,
                onSend = kbdWrap,
                onGo = kbdWrap,
            ),
            modifier = Modifier.onKeyEvent {
                if (it.key == Key.Enter) {
                    onSubmit(text)
                    text = ""
                    true
                } else {
                    false
                }
            }.padding(12.dp),
        )
        if (buttonContent != null) {
            Button(onClick = { onSubmit(text) }, content = buttonContent)
        }
    }
}

@Composable
@Preview
internal fun SubmittableTextNoButtonPreview() {
    SubmittableText(placeholder = { Text("Enter some text") }) {}
}

@Composable
@Preview
internal fun SubmittableTextButtonPreview() {
    SubmittableText(
        placeholder = { Text("Enter some text") },
        buttonContent = { Text("Submit") },
    ) {}
}
