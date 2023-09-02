package us.q3q.fidok.ui

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
fun ContainerCard(f: @Composable () -> Unit) {
    val scrollState = rememberScrollState()
    Card(elevation = 6.dp, modifier = Modifier.padding(vertical = 2.dp).horizontalScroll(scrollState)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 2.dp),
        ) {
            f()
        }
    }
}

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
    ContainerCard {
        Text("$label:")
        elements.map {
            InnerCard(it)
        }
    }
}

@Composable
fun CardsForMap(label: String, elements: Map<String, Any>) {
    ContainerCard {
        Text("$label:")
        elements.map {
            InnerCard("${it.key}=${it.value}")
        }
    }
}

@Composable
fun InfoDisplay(info: GetInfoResponse) {
    val state = rememberScrollState()
    Column(Modifier.fillMaxWidth().verticalScroll(state), horizontalAlignment = Alignment.Start) {
        CardsForList("Versions", info.versions)
        info.extensions?.let {
            CardsForList("Extensions", it)
        }
        // aaguid?
        info.options?.let {
            CardsForMap("Options", it)
        }
        info.maxMsgSize?.let {
            LabeledCard("Max Message Size", "$it bytes")
        }
        info.pinUvAuthProtocols?.let { protos ->
            CardsForList(
                "PIN Protocols",
                protos.map { it.toString() }.toTypedArray(),
            )
        }
        info.maxCredentialIdLength?.let {
            LabeledCard("Max Credential ID Length", "$it bytes")
        }
        info.transports?.let {
            CardsForList("Transports", it)
        }
        info.algorithms?.let {
            CardsForList(
                "Supported Algorithms",
                it.map { algo -> algo.toString() }.toTypedArray(),
            )
        }
        info.maxSerializedLargeBlobArray?.let {
            LabeledCard(
                "Large Blob Store Size",
                "$it bytes",
            )
        }
        info.forcePINChange?.let {
            LabeledCard(
                "Force PIN Change",
                it.toString(),
            )
        }
        info.minPINLength?.let {
            LabeledCard(
                "Minimum PIN Length",
                "$it characters",
            )
        }
        info.firmwareVersion?.let {
            LabeledCard(
                "Firmware Version",
                it.toString(),
            )
        }
        info.maxCredBlobLength?.let {
            LabeledCard(
                "Blob Storage Per Credential",
                "$it bytes",
            )
        }
        info.maxRPIDsForSetMinPINLength?.let {
            LabeledCard(
                "Number of RPs That Can Receive Min PIN Length",
                it.toString(),
            )
        }
        info.preferredPlatformUvAttempts?.let {
            LabeledCard(
                "Preferred User Verification Attempts",
                it.toString(),
            )
        }
        info.uvModality?.let {
            LabeledCard(
                "User Verification Modalities",
                it.toString(),
            )
        }
        info.certifications?.let {
            CardsForMap("Certifications", it)
        }
        info.remainingDiscoverableCredentials?.let {
            LabeledCard(
                "Remaining Discoverable Credentials (approx)",
                it.toString(),
            )
        }
        info.vendorPrototypeConfigCommands?.let {
            CardsForList(
                "Supported Vendor Prototype Commands",
                it.map { x -> x.toString() }.toTypedArray(),
            )
        }
    }
}

@Preview
@Composable
fun previewInfo() {
    MaterialTheme {
        InfoDisplay(
            GetInfoResponse(
                versions = arrayOf("FIDO_2_0", "FIDO_2_1_PRE"),
                extensions = arrayOf("minPinLength"),
                aaguid = Random.nextBytes(32),
                options = hashMapOf(
                    "rk" to true,
                    "uv" to true,
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
