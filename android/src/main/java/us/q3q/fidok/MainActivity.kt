package us.q3q.fidok

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import us.q3q.fidok.ctap.commands.GetInfoResponse
import us.q3q.fidok.ui.InfoDisplay
import us.q3q.fidok.ui.theme.FidoKTheme
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FidoKTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    InfoDisplay(
                        GetInfoResponse(
                            versions = arrayOf("FIDO_2_0"),
                            aaguid = Random.nextBytes(32),
                            options = mapOf(
                                "clientPin" to false,
                                "something" to true,
                            ),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier,
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    FidoKTheme {
        InfoDisplay(
            GetInfoResponse(
                versions = arrayOf("FIDO_2_0"),
                aaguid = Random.nextBytes(32),
                options = mapOf(
                    "clientPin" to false,
                    "something" to true,
                ),
            ),
        )
    }
}
