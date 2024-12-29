package us.q3q.fidok.ui

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.multiplatform.webview.jsbridge.IJsMessageHandler
import com.multiplatform.webview.jsbridge.JsMessage
import com.multiplatform.webview.jsbridge.rememberWebViewJsBridge
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.WebViewNavigator
import com.multiplatform.webview.web.WebViewState
import com.multiplatform.webview.web.rememberWebViewState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import us.q3q.fidok.crypto.NullCryptoProvider
import us.q3q.fidok.ctap.AuthenticatorDevice
import us.q3q.fidok.ctap.CTAPClient
import us.q3q.fidok.ctap.FIDOkLibrary
import us.q3q.fidok.ctap.commands.PublicKeyCredentialRpEntity
import us.q3q.fidok.ctap.commands.PublicKeyCredentialUserEntity
import us.q3q.fidok.webauthn.AuthenticatorAttestationResponse
import us.q3q.fidok.webauthn.AuthenticatorSelectionCriteria
import us.q3q.fidok.webauthn.CredentialCreationOptions
import us.q3q.fidok.webauthn.PublicKeyCredentialCreationOptions
import us.q3q.fidok.webauthn.UserVerificationRequirement
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
class WebauthnCreateCallback(private val library: FIDOkLibrary, private val state: WebViewState) : IJsMessageHandler {
    override fun handle(
        message: JsMessage,
        navigator: WebViewNavigator?,
        callback: (String) -> Unit,
    ) {
        val paramsString = message.params

        println("Creation handler called with $paramsString!")

        val decoded = Json.decodeFromString<JsonObject>(paramsString)
        val rp = decoded["rp"] as JsonObject
        val user = decoded["user"] as JsonObject
        val pkParams = decoded["pubKeyCredParams"] as JsonArray
        val aSel = decoded["authenticatorSelection"] as JsonObject

        println("Creation handler decoded $decoded")

        val credentialCreationOptions =
            CredentialCreationOptions(
                publicKey =
                    PublicKeyCredentialCreationOptions(
                        rp =
                            PublicKeyCredentialRpEntity(
                                id = rp["id"]?.toString(),
                                name = rp["name"]?.toString(),
                            ),
                        challenge = Base64.decode((decoded["challenge"] as JsonPrimitive).content.toByteArray()),
                        user =
                            PublicKeyCredentialUserEntity(
                                id = Base64.decode((user["id"] as JsonPrimitive).content.toByteArray()),
                                name = user["name"]?.toString(),
                                displayName = user["displayName"]?.toString(),
                            ),
                /*pubKeyCredParams = [PublicKeyCredentialParameters(

                )],*/
                        authenticatorSelectionCriteria =
                            AuthenticatorSelectionCriteria(
                                residentKey = (aSel["residentKey"] as JsonPrimitive?)?.content,
                                requireResidentKey = (aSel["requireResidentKey"] as JsonPrimitive?)?.boolean ?: false,
                                userVerification =
                                    (aSel["userVerification"] as JsonPrimitive?)?.content
                                        ?: UserVerificationRequirement.DISCOURAGED.value,
                            ),
                    ),
            )

        runBlocking {
            val res =
                library.webauthn().create(
                    origin = state.lastLoadedUrl,
                    options = credentialCreationOptions,
                )

            println("Credential creation result $res")

            val att = res.response as AuthenticatorAttestationResponse

            callback(
                Json.encodeToString(
                    JsonObject.serializer(),
                    JsonObject(
                        mapOf(
                            "id" to JsonPrimitive(res.id),
                            "type" to JsonPrimitive(res.type),
                            "authenticatorAttachment" to JsonPrimitive(res.authenticatorAttachment),
                            "response" to
                                JsonObject(
                                    mapOf(
                                        "clientDataJSON" to JsonPrimitive(Base64.encode(att.clientDataJSON)),
                                        "transports" to JsonArray(att.transports.map { JsonPrimitive(it) }),
                                        "publicKeyAlgorithm" to JsonPrimitive(att.publicKeyAlgorithm),
                                        "attestationObject" to JsonPrimitive(Base64.encode(att.attestationObject)),
                                    ),
                                ),
                        ),
                    ),
                ),
            )
        }
    }

    override fun methodName(): String = "WebauthnCreate"
}

@Composable
private fun rememberWebViewNavigator(coroutineScope: CoroutineScope = rememberCoroutineScope()): WebViewNavigator =
    remember(coroutineScope) { WebViewNavigator(coroutineScope) }

private fun attachWebauthnHandlers(navigator: WebViewNavigator) {
    navigator.evaluateJavaScript(
        """
                function _barrToB64(barr) {
                    return btoa(String.fromCharCode(...new Uint8Array(barr)))
                }
                
                function _b64ToBarr(b64) {
                    return Uint8Array.from(atob(b64), c => c.charCodeAt(0)).buffer
                }
        
                navigator.credentials.create = function(options) {
                    const pk = options.publicKey;
                    
                    pk.user.id = _barrToB64(pk.user.id);
                    pk.challenge = _barrToB64(pk.challenge);
                    
                    return new Promise(function(resolve, reject) {
                        try {
                            window.kmpJsBridge.callNative("WebauthnCreate", JSON.stringify(pk), function(result) {
                                console.log("got result", result);
                                
                                result.rawId = _b64ToBarr(result.id);
                                result.response.attestationObject = _b64ToBarr(result.response.attestationObject);
                                result.response.getTransports = function() { return result.response.transports; }
                                result.response.getPublicKeyAlgorithm = function() { return result.response.publicKeyAlgorithm; }
                                // TODO
                                result.response.getPublicKey = function() { return null; }
                                
                                result.getClientExtensionResults = function() { return {}; };
                                
                                resolve(result);
                            });
                        } catch (e) {
                            console.log("rejecting because", e);
                            reject(e);
                        }
                    });
                }
            """,
    )
}

@Composable
fun MainView(
    library: FIDOkLibrary,
    devices: List<AuthenticatorDevice>? = null,
    onListDevices: () -> Unit = {},
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        val pinResponseChannel by remember { mutableStateOf(Channel<String?>()) }
        val coroutineScope = rememberCoroutineScope()
        var chosenClient by remember { mutableStateOf<CTAPClient?>(null) }
        var pinRequested by remember { mutableStateOf(false) }
        var browserMode by remember { mutableStateOf(false) }
        val webViewState = rememberWebViewState("https://webauthn.io")
        val navigator = rememberWebViewNavigator()

        val jsBridge = rememberWebViewJsBridge(navigator)

        LaunchedEffect(jsBridge) {
            jsBridge.register(WebauthnCreateCallback(library, webViewState))
        }

        if (browserMode) {
            attachWebauthnHandlers(navigator)

            SubmittableText(placeholder = { Text("url") }, initialValue = webViewState.lastLoadedUrl ?: "") {
                navigator.loadUrl(it)
                attachWebauthnHandlers(navigator)
            }

            WebView(
                state = webViewState,
                navigator = navigator,
                webViewJsBridge = jsBridge,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(onClick = {
                    chosenClient = null
                    onListDevices()
                }) {
                    Text(
                        if (devices == null) {
                            "List Authenticators"
                        } else {
                            "There ${if (devices.size == 1) "is" else "are"} " +
                                "${devices.size} device${if (devices.size == 1) "" else "s"}"
                        },
                    )
                }
                if ((devices?.size ?: 0) < 1) {
                    Button(onClick = {
                        browserMode = true
                    }) {
                        Text("Web Browser")
                    }
                }
            }
            val client = chosenClient
            if (devices != null && client == null) {
                MultipleAuthenticatorDisplay(devices, onSelect = {
                    chosenClient =
                        library.ctapClient(it, collectPin = {
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
