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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import us.q3q.fidok.crypto.NullCryptoProvider
import us.q3q.fidok.ctap.AuthenticatorDevice
import us.q3q.fidok.ctap.CTAPClient
import us.q3q.fidok.ctap.FIDOkLibrary
import us.q3q.fidok.ctap.commands.COSEAlgorithmIdentifier
import us.q3q.fidok.ctap.commands.PublicKeyCredentialDescriptor
import us.q3q.fidok.ctap.commands.PublicKeyCredentialParameters
import us.q3q.fidok.ctap.commands.PublicKeyCredentialRpEntity
import us.q3q.fidok.ctap.commands.PublicKeyCredentialUserEntity
import us.q3q.fidok.webauthn.AttestationConveyancePreference
import us.q3q.fidok.webauthn.AuthenticatorAttestationResponse
import us.q3q.fidok.webauthn.AuthenticatorSelectionCriteria
import us.q3q.fidok.webauthn.CredentialCreationOptions
import us.q3q.fidok.webauthn.DEFAULT_PUB_KEY_CRED_PARAMS
import us.q3q.fidok.webauthn.PublicKeyCredentialCreationOptions
import us.q3q.fidok.webauthn.UserVerificationRequirement
import java.net.URL
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
        val rp = decoded["rp"]?.jsonObject
        val user = decoded["user"]!!.jsonObject
        val pkParams = decoded["pubKeyCredParams"]?.jsonArray
        val aSel = decoded["authenticatorSelection"]?.jsonObject

        println("Creation handler decoded $decoded")

        val credentialCreationOptions =
            CredentialCreationOptions(
                publicKey =
                    PublicKeyCredentialCreationOptions(
                        attestation = decoded["attestation"]?.jsonPrimitive?.content ?: AttestationConveyancePreference.DIRECT.value,
                        attestationFormats =
                            decoded["attestationFormats"]?.jsonArray?.map {
                                it.jsonPrimitive.content
                            } ?: listOf(),
                        timeout = decoded["timeout"]?.jsonPrimitive?.long?.toULong(),
                        rp =
                            PublicKeyCredentialRpEntity(
                                id = rp?.get("id")?.jsonPrimitive?.content,
                                name = rp?.get("name")?.jsonPrimitive?.content,
                            ),
                        challenge = Base64.decode(decoded["challenge"]!!.jsonPrimitive.content.toByteArray()),
                        user =
                            PublicKeyCredentialUserEntity(
                                id = Base64.decode(user["id"]!!.jsonPrimitive.content.toByteArray()),
                                name = user["name"]?.jsonPrimitive?.content,
                                displayName = user["displayName"]?.jsonPrimitive?.content,
                            ),
                        pubKeyCredParams =
                            pkParams?.map { param ->
                                PublicKeyCredentialParameters(
                                    type = param.jsonObject["type"]!!.jsonPrimitive.content,
                                    alg =
                                        COSEAlgorithmIdentifier.entries.first {
                                            it.value == param.jsonObject["alg"]!!.jsonPrimitive.long
                                        },
                                )
                            } ?: DEFAULT_PUB_KEY_CRED_PARAMS,
                        authenticatorSelectionCriteria =
                            if (aSel == null) {
                                null
                            } else {
                                AuthenticatorSelectionCriteria(
                                    residentKey = aSel["residentKey"]?.jsonPrimitive?.content,
                                    requireResidentKey = aSel["requireResidentKey"]?.jsonPrimitive?.boolean ?: false,
                                    userVerification =
                                        aSel["userVerification"]?.jsonPrimitive?.content
                                            ?: UserVerificationRequirement.DISCOURAGED.value,
                                )
                            },
                        hints = decoded["hints"]?.jsonArray?.map { (it as JsonPrimitive).content } ?: listOf(),
                        excludeCredentials =
                            decoded["excludeCredentials"]?.jsonArray?.map {
                                PublicKeyCredentialDescriptor(
                                    type = it.jsonObject["type"]?.jsonPrimitive?.content ?: "public-key",
                                    id = Base64.decode(it.jsonObject["id"]!!.jsonPrimitive.content.toByteArray()),
                                )
                            } ?: listOf(),
                    ),
            )

        runBlocking {
            val url = URL(state.lastLoadedUrl)
            val protocol = url.protocol

            if (protocol != "https") {
                throw IllegalStateException("Should not perform FIDO auth on non-HTTPS URLs")
            }

            val res =
                library.webauthn().create(
                    origin = url.protocol + "://" + url.host,
                    options = credentialCreationOptions,
                )

            println("Credential creation result $res")

            val att = res.response as AuthenticatorAttestationResponse

            val gottenAttestationObject = att.attestationObject
            val gottenPublicKey = att.publicKey
            val gottenAuthenticatorData = att.authenticatorData
            val gottenSignature = att.signature

            callback(
                Json.encodeToString(
                    JsonObject.serializer(),
                    JsonObject(
                        mapOf(
                            "id" to JsonPrimitive(res.id),
                            "rawId" to JsonPrimitive(Base64.encode(res.rawId)),
                            "type" to JsonPrimitive(res.type),
                            "authenticatorAttachment" to JsonPrimitive(res.authenticatorAttachment),
                            "response" to
                                JsonObject(
                                    mapOf(
                                        "clientDataJSON" to JsonPrimitive(Base64.encode(att.clientDataJSON)),
                                        "transports" to JsonArray(att.transports.map { JsonPrimitive(it) }),
                                        "publicKey" to
                                            if (gottenPublicKey != null) {
                                                JsonPrimitive(Base64.encode(gottenPublicKey))
                                            } else {
                                                JsonNull
                                            },
                                        "publicKeyAlgorithm" to JsonPrimitive(att.publicKeyAlgorithm),
                                        "attestationObject" to
                                            if (gottenAttestationObject != null) {
                                                JsonPrimitive(Base64.encode(gottenAttestationObject))
                                            } else {
                                                JsonNull
                                            },
                                        "authenticatorData" to
                                            if (gottenAuthenticatorData != null) {
                                                JsonPrimitive(Base64.encode(gottenAuthenticatorData))
                                            } else {
                                                JsonNull
                                            },
                                        "signature" to
                                            if (gottenSignature != null) {
                                                JsonPrimitive(Base64.encode(gottenSignature))
                                            } else {
                                                JsonNull
                                            },
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
                    return btoa(String.fromCharCode(...new Uint8Array(barr)));
                }

                function _b64ToBarr(base64) {
                    if (!base64) {
                        return null;
                    }

                    var binaryString = atob(base64);
                    var bytes = new Uint8Array(binaryString.length);
                    for (var i = 0; i < binaryString.length; i++) {
                        bytes[i] = binaryString.charCodeAt(i);
                    }
                    return bytes.buffer;
                }


                navigator.credentials = {};
                navigator.credentials.create = function(options) {
                    const pk = options.publicKey;

                    pk.user.id = _barrToB64(pk.user.id);
                    pk.challenge = _barrToB64(pk.challenge);

                    return new Promise(function(resolve, reject) {
                        try {
                            window.kmpJsBridge.callNative("WebauthnCreate", JSON.stringify(pk), (rawResult) => {
                                const result = JSON.parse(rawResult);

                                const wrapped = {
                                    "id": result.id,
                                    "rawId": _b64ToBarr(result.rawId),
                                    "type": "public-key",
                                    "authenticatorAttachment": result.authenticatorAttachment,

                                    "getClientExtensionResults": () => {},

                                    "response": {
                                        "getTransports": () => result.response.transports,
                                        "getPublicKey": () => _b64ToBarr(result.response.publicKey),
                                        "getPublicKeyAlgorithm": () => result.response.publicKeyAlgorithm,
                                        // "getAuthenticatorData": () => _b64ToBarr(result.response.authenticatorData),
                                        // "authenticatorData": _b64ToBarr(result.response.authenticatorData),

                                        "clientDataJSON": _b64ToBarr(result.response.clientDataJSON),
                                        "attestationObject": _b64ToBarr(result.response.attestationObject),
                                        "signature": _b64ToBarr(result.response.signature),
                                    }
                                };

                                resolve(wrapped);
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
        val webViewState = rememberWebViewState("https://webauthn.lubu.ch/_test/client.html?userVerification=discouraged&fmt_none=1")
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
