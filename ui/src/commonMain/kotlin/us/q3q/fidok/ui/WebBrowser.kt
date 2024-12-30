package us.q3q.fidok.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import co.touchlab.kermit.Logger
import com.multiplatform.webview.jsbridge.IJsMessageHandler
import com.multiplatform.webview.jsbridge.JsMessage
import com.multiplatform.webview.jsbridge.rememberWebViewJsBridge
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.WebViewNavigator
import com.multiplatform.webview.web.WebViewState
import com.multiplatform.webview.web.rememberWebViewState
import dev.datlag.kcef.KCEF
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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
import us.q3q.fidok.ctap.FIDOkLibrary
import us.q3q.fidok.ctap.commands.COSEAlgorithmIdentifier
import us.q3q.fidok.ctap.commands.PublicKeyCredentialDescriptor
import us.q3q.fidok.ctap.commands.PublicKeyCredentialParameters
import us.q3q.fidok.ctap.commands.PublicKeyCredentialRpEntity
import us.q3q.fidok.ctap.commands.PublicKeyCredentialUserEntity
import us.q3q.fidok.webauthn.AttestationConveyancePreference
import us.q3q.fidok.webauthn.AuthenticatorAssertionResponse
import us.q3q.fidok.webauthn.AuthenticatorAttestationResponse
import us.q3q.fidok.webauthn.AuthenticatorSelectionCriteria
import us.q3q.fidok.webauthn.CredentialCreationOptions
import us.q3q.fidok.webauthn.CredentialRequestOptions
import us.q3q.fidok.webauthn.DEFAULT_PUB_KEY_CRED_PARAMS
import us.q3q.fidok.webauthn.PublicKeyCredentialCreationOptions
import us.q3q.fidok.webauthn.PublicKeyCredentialRequestOptions
import us.q3q.fidok.webauthn.UserVerificationRequirement
import java.io.File
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

        Logger.v("Creation handler called with $paramsString!")

        val decoded = Json.decodeFromString<JsonObject>(paramsString)
        val rp = decoded["rp"]?.jsonObject
        val user = decoded["user"]!!.jsonObject
        val pkParams = decoded["pubKeyCredParams"]?.jsonArray
        val aSel = decoded["authenticatorSelection"]?.jsonObject

        Logger.v("Creation handler decoded $decoded")

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
                        hints = decoded["hints"]?.jsonArray?.map { it.jsonPrimitive.content } ?: listOf(),
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
            launch(Dispatchers.IO) {
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

                Logger.d("Credential creation result $res")

                val att = res.response as AuthenticatorAttestationResponse

                val gottenAttestationObject = att.attestationObject
                val gottenPublicKey = att.publicKey
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
    }

    override fun methodName(): String = "WebauthnCreate"
}

@OptIn(ExperimentalEncodingApi::class)
class WebauthnGetCallback(private val library: FIDOkLibrary, private val state: WebViewState) : IJsMessageHandler {
    override fun handle(
        message: JsMessage,
        navigator: WebViewNavigator?,
        callback: (String) -> Unit,
    ) {
        val paramsString = message.params

        Logger.v("Assertion handler called with $paramsString!")

        val decoded = Json.decodeFromString<JsonObject>(paramsString)

        Logger.v("Assertion handler decoded $decoded")

        val credentialRequestOptions =
            CredentialRequestOptions(
                publicKey =
                    PublicKeyCredentialRequestOptions(
                        rpId = decoded["rpId"]?.jsonPrimitive?.content,
                        challenge = Base64.decode(decoded["challenge"]!!.jsonPrimitive.content.toByteArray()),
                        timeout = decoded["timeout"]?.jsonPrimitive?.long?.toULong(),
                        allowCredentials =
                            decoded["allowCredentials"]?.jsonArray?.map {
                                PublicKeyCredentialDescriptor(
                                    id = Base64.decode(it.jsonObject["id"]!!.jsonPrimitive.content.toByteArray()),
                                    type = it.jsonObject["type"]?.jsonPrimitive?.content ?: "public-key",
                                    transports =
                                        it.jsonObject["transports"]?.jsonArray?.map { transport ->
                                            transport.jsonPrimitive.content
                                        },
                                )
                            } ?: listOf(),
                        userVerification =
                            decoded["userVerification"]?.jsonPrimitive?.content
                                ?: UserVerificationRequirement.PREFERRED.value,
                        hints = decoded["hints"]?.jsonArray?.map { it.jsonPrimitive.content } ?: listOf(),
                        attestation = decoded["attestation"]?.jsonPrimitive?.content ?: AttestationConveyancePreference.DIRECT.value,
                        attestationFormats = decoded["attestationFormats"]?.jsonArray?.map { it.jsonPrimitive.content } ?: listOf(),
                    ),
            )

        runBlocking {
            launch(Dispatchers.IO) {
                val url = URL(state.lastLoadedUrl)
                val protocol = url.protocol

                if (protocol != "https") {
                    throw IllegalStateException("Should not perform FIDO auth on non-HTTPS URLs")
                }

                val res =
                    library.webauthn().get(
                        origin = url.protocol + "://" + url.host,
                        options = credentialRequestOptions,
                    )

                Logger.d("Credential assertion result $res")

                val att = res.response as AuthenticatorAssertionResponse

                val gottenAttestationObject = att.attestationObject
                val gottenUserHandle = att.userHandle
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
                                            "attestationObject" to
                                                if (gottenAttestationObject != null) {
                                                    JsonPrimitive(Base64.encode(gottenAttestationObject))
                                                } else {
                                                    JsonNull
                                                },
                                            "signature" to JsonPrimitive(Base64.encode(gottenSignature)),
                                            "userHandle" to
                                                if (gottenUserHandle != null) {
                                                    JsonPrimitive(Base64.encode(gottenUserHandle))
                                                } else {
                                                    JsonNull
                                                },
                                            "authenticatorData" to JsonPrimitive(Base64.encode(att.authenticatorData)),
                                        ),
                                    ),
                            ),
                        ),
                    ),
                )
            }
        }
    }

    override fun methodName(): String = "WebauthnGet"
}

class WebauthnErrorCallback(private val addErrorCallback: (String) -> Unit) : IJsMessageHandler {
    override fun handle(
        message: JsMessage,
        navigator: WebViewNavigator?,
        callback: (String) -> Unit,
    ) {
        addErrorCallback(message.params)
    }

    override fun methodName() = "SendError"
}

@Composable
private fun rememberWebViewNavigator(coroutineScope: CoroutineScope = rememberCoroutineScope()): WebViewNavigator =
    remember(coroutineScope) { WebViewNavigator(coroutineScope) }

private fun attachWebauthnHandlers(
    navigator: WebViewNavigator,
    callback: (String) -> Unit,
) {
    navigator.evaluateJavaScript(
        """
                function _barrToB64(barr) {
                    return btoa(String.fromCharCode(...new Uint8Array(barr)));
                }

                function _b64ToBarr(base64) {
                    if (!base64) {
                        return null;
                    }

                    var bs = atob(base64);
                    var ret = new Uint8Array(bs.length);
                    for (var i = 0; i < bs.length; i++) {
                        ret[i] = bs.charCodeAt(i);
                    }
                    return ret.buffer;
                }


                if (!navigator.credentials) {
                    navigator.credentials = {};
                }
                navigator.credentials.create = function(options) {
                    const pk = options.publicKey;

                    pk.user.id = _barrToB64(pk.user.id);
                    pk.challenge = _barrToB64(pk.challenge);
                    if (pk.excludeCredentials) {
                        pk.excludeCredentials.forEach((ex) => {
                            ex.id = _barrToB64(ex.id);
                        });
                    }

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

                                        "clientDataJSON": _b64ToBarr(result.response.clientDataJSON),
                                        "attestationObject": _b64ToBarr(result.response.attestationObject),
                                        "signature": _b64ToBarr(result.response.signature),
                                    }
                                };

                                resolve(wrapped);
                            });
                        } catch (e) {
                            console.log("rejecting creation", e);
                            window.kmpJsBridge.callNative(
                                "SendError", JSON.stringify({"message": e.message}), () => {}
                            );
                            reject(e);
                        }
                    });
                }

                navigator.credentials.get = function(options) {
                    const pk = options.publicKey;

                    pk.challenge = _barrToB64(pk.challenge);
                    if (pk.allowCredentials) {
                        pk.allowCredentials.forEach((cred) => {
                            cred.id = _barrToB64(cred.id);
                        });
                    }

                    return new Promise(function(resolve, reject) {
                        try {
                            window.kmpJsBridge.callNative("WebauthnGet", JSON.stringify(pk), (rawResult) => {
                                const result = JSON.parse(rawResult);

                                const wrapped = {
                                    "id": result.id,
                                    "rawId": _b64ToBarr(result.rawId),
                                    "type": "public-key",
                                    "authenticatorAttachment": result.authenticatorAttachment,

                                    "getClientExtensionResults": () => {},

                                    "response": {
                                        "clientDataJSON": _b64ToBarr(result.response.clientDataJSON),
                                        "signature": _b64ToBarr(result.response.signature),
                                        "userHandle": _b64ToBarr(result.response.userHandle),
                                        "authenticatorData": _b64ToBarr(result.response.authenticatorData),
                                    }
                                };

                                resolve(wrapped);
                            });
                        } catch (e) {
                            console.log("rejecting assertion", e);
                            window.kmpJsBridge.callNative(
                                "SendError", JSON.stringify({"message": e.message}), () => {}
                            );
                            reject(e);
                        }
                    });
                }
            """,
        callback,
    )
}

@Composable
fun WebBrowser(
    library: FIDOkLibrary,
    mobileView: Boolean = false,
) {
    var restartRequired by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(0F) }
    var initialized by remember { mutableStateOf(false) }
    var attaching by remember { mutableStateOf(true) }

    val webViewState = rememberWebViewState("https://webauthn.lubu.ch/_test/client.html")
    val navigator = rememberWebViewNavigator()
    val jsBridge = rememberWebViewJsBridge(navigator)

    DisposableEffect(Unit) {
        webViewState.webSettings.apply {
            androidWebSettings.apply {
                domStorageEnabled = true
                allowProtectedMedia = true
            }
        }
        onDispose { }
    }

    LaunchedEffect(jsBridge) {
        jsBridge.register(WebauthnCreateCallback(library, webViewState))
        jsBridge.register(WebauthnGetCallback(library, webViewState))
        jsBridge.register(
            WebauthnErrorCallback {
                runBlocking {
                    library.onException(Exception(it))
                }
            },
        )
    }

    if (!mobileView) {
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                KCEF.init(builder = {
                    installDir(File("kcef-bundle"))

                    progress {
                        onDownloading {
                            downloading = if (it > 0F) it else 0F
                        }
                        onInitialized {
                            initialized = true
                        }
                    }
                    settings {
                        cachePath = File("cache").absolutePath
                    }
                }, onError = {
                    it?.printStackTrace()
                }, onRestartRequired = {
                    restartRequired = true
                })
            }
        }
    } else {
        initialized = true
    }

    if (restartRequired) {
        Text(text = "Restart required.")
    } else {
        if (initialized) {
            attachWebauthnHandlers(navigator) {
                attaching = false
            }

            if (attaching) {
                Text(text = "Still getting things ready for web browsing...")
            }

            SubmittableText(placeholder = { Text("url") }, initialValue = webViewState.lastLoadedUrl ?: "") {
                attaching = true

                if (it.startsWith("https:") || it.startsWith("http:")) {
                    navigator.loadUrl(it)
                } else {
                    navigator.loadUrl("https://$it")
                }

                attachWebauthnHandlers(navigator) {
                    attaching = false
                }
            }

            WebView(
                state = webViewState,
                navigator = navigator,
                webViewJsBridge = jsBridge,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(text = "Downloading browser ${Math.round(downloading * 10.0) / 10.0}%")
        }
    }
}
