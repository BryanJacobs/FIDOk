package us.q3q.fidok.ui

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import us.q3q.fidok.ctap.commands.COSEAlgorithmIdentifier
import us.q3q.fidok.ctap.commands.GetInfoResponse
import us.q3q.fidok.ctap.commands.PublicKeyCredentialParameters
import us.q3q.fidok.ctap.commands.PublicKeyCredentialType
import kotlin.random.Random

@Composable
fun InnerCard(text: String) {
    Card(
        border = BorderStroke(1.dp, Color.Black),
        modifier = Modifier.padding(2.dp),
    ) {
        Text(text, modifier = Modifier.padding(2.dp))
    }
}

@Composable
fun LabeledCard(label: String, text: String) {
    CardsForList(label, arrayOf(text))
}

@Composable
fun CardsForList(label: String, elements: Array<String>) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(28.dp),
    ) {
        Text("$label:")
        LazyHorizontalGrid(rows = GridCells.Adaptive(20.dp), modifier = Modifier.fillMaxHeight()) {
            elements.map {
                item {
                    InnerCard(it)
                }
            }
        }
    }
}

@Composable
fun CardsForMap(label: String, elements: Map<String, Any>) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(54.dp),
    ) {
        Text("$label:")
        LazyHorizontalGrid(rows = GridCells.Adaptive(20.dp), modifier = Modifier.fillMaxHeight()) {
            elements.map {
                item {
                    InnerCard("${it.key}=${it.value}")
                }
            }
        }
    }
}

@Composable
fun InfoDisplay(info: GetInfoResponse?) {
    val state = rememberLazyListState()

    if (info == null) {
        return
    }

    Box(
        modifier = Modifier.fillMaxSize()
            .background(color = Color(180, 180, 180))
            .padding(10.dp),
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.Start,
            state = state,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                CardsForList("Versions", info.versions)
            }
            info.extensions?.let {
                item {
                    CardsForList("Extensions", it)
                }
            }
            // aaguid?
            info.options?.let {
                item {
                    CardsForMap("Options", it)
                }
            }
            info.maxMsgSize?.let {
                item {
                    LabeledCard("Max Message Size", "$it bytes")
                }
            }
            info.pinUvAuthProtocols?.let { protos ->
                item {
                    CardsForList(
                        "PIN Protocols",
                        protos.map { it.toString() }.toTypedArray(),
                    )
                }
            }
            info.maxCredentialIdLength?.let {
                item {
                    LabeledCard("Max Credential ID Length", "$it bytes")
                }
            }
            info.transports?.let {
                item {
                    CardsForList("Transports", it)
                }
            }
            info.algorithms?.let {
                item {
                    CardsForList(
                        "Supported Algorithms",
                        it.map { algo -> algo.toString() }.toTypedArray(),
                    )
                }
            }
            info.maxSerializedLargeBlobArray?.let {
                item {
                    LabeledCard(
                        "Large Blob Store Size",
                        "$it bytes",
                    )
                }
            }
            info.forcePINChange?.let {
                item {
                    LabeledCard(
                        "Force PIN Change",
                        it.toString(),
                    )
                }
            }
            info.minPINLength?.let {
                item {
                    LabeledCard(
                        "Minimum PIN Length",
                        "$it characters",
                    )
                }
            }
            info.firmwareVersion?.let {
                item {
                    LabeledCard(
                        "Firmware Version",
                        it.toString(),
                    )
                }
            }
            info.maxCredBlobLength?.let {
                item {
                    LabeledCard(
                        "Blob Storage Per Credential",
                        "$it bytes",
                    )
                }
            }
            info.maxRPIDsForSetMinPINLength?.let {
                item {
                    LabeledCard(
                        "Number of RPs That Can Receive Min PIN Length",
                        it.toString(),
                    )
                }
            }
            info.preferredPlatformUvAttempts?.let {
                item {
                    LabeledCard(
                        "Preferred User Verification Attempts",
                        it.toString(),
                    )
                }
            }
            info.uvModality?.let {
                item {
                    LabeledCard(
                        "User Verification Modalities",
                        it.toString(),
                    )
                }
            }
            info.certifications?.let {
                item {
                    CardsForMap("Certifications", it)
                }
            }
            info.remainingDiscoverableCredentials?.let {
                item {
                    LabeledCard(
                        "Remaining Discoverable Credentials (approx)",
                        it.toString(),
                    )
                }
            }
            info.vendorPrototypeConfigCommands?.let {
                item {
                    CardsForList(
                        "Supported Vendor Prototype Commands",
                        it.map { x -> x.toString() }.toTypedArray(),
                    )
                }
            }
        }
        /*VerticalScrollbar(
            modifier = Modifier.fillMaxHeight(),
            adapter = rememberScrollbarAdapter(scrollState = state),
        )*/
    }
}

@Preview
@Composable
internal fun previewInfo() {
    MaterialTheme {
        InfoDisplay(
            GetInfoResponse(
                versions = arrayOf("FIDO_2_0", "FIDO_2_1_PRE"),
                extensions = arrayOf("minPinLength"),
                aaguid = Random.nextBytes(32),
                options = hashMapOf(
                    "clientPin" to true,
                    "setMinPinLength" to true,
                    "alwaysUv" to true,
                ),
                maxMsgSize = 2048u,
                pinUvAuthProtocols = arrayOf(1u, 2u),
                maxCredentialCountInList = 10u,
                maxCredentialIdLength = 64u,
                transports = arrayOf("nfc", "usb"),
                algorithms = arrayOf(
                    PublicKeyCredentialParameters(
                        alg = COSEAlgorithmIdentifier.ES256,
                        type = PublicKeyCredentialType.PUBLIC_KEY,
                    ),
                ),
                maxSerializedLargeBlobArray = 1024u,
                forcePINChange = true,
                minPINLength = 3u,
                firmwareVersion = 1u,
                maxCredBlobLength = 32u,
                maxRPIDsForSetMinPINLength = 2u,
                preferredPlatformUvAttempts = 0u,
                uvModality = 1u,
                certifications = mapOf("FIDO" to 4u),
                remainingDiscoverableCredentials = 20u,
            ),
        )
    }
}
