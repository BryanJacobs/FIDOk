package us.q3q.fidok.ui

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import us.q3q.fidok.crypto.NullCryptoProvider
import us.q3q.fidok.ctap.AuthenticatorDevice
import us.q3q.fidok.ctap.AuthenticatorTransport
import us.q3q.fidok.ctap.CTAPClient
import us.q3q.fidok.ctap.CTAPPinPermission
import us.q3q.fidok.ctap.FIDOkLibrary
import us.q3q.fidok.ctap.RPWithHash
import us.q3q.fidok.ctap.StoredCredentialData
import us.q3q.fidok.ctap.commands.COSEKey
import us.q3q.fidok.ctap.commands.CredentialManagementGetMetadataResponse
import us.q3q.fidok.ctap.commands.GetInfoResponse
import us.q3q.fidok.ctap.commands.PublicKeyCredentialDescriptor
import us.q3q.fidok.ctap.commands.PublicKeyCredentialRpEntity
import us.q3q.fidok.ctap.commands.PublicKeyCredentialUserEntity
import kotlin.random.Random

@Composable
fun AuthenticatorManagementDisplay(client: CTAPClient, onChangeTab: () -> Unit = {}) {
    var selection by remember { mutableStateOf(0) }

    Column {
        TabRow(selectedTabIndex = selection) {
            Tab(text = { Text("Info") }, selected = selection == 0, onClick = {
                onChangeTab()
                selection = 0
            })
            Tab(text = { Text("Cred") }, selected = selection == 1, onClick = {
                onChangeTab()
                selection = 1
            })
            Tab(text = { Text("PIN") }, selected = selection == 2, onClick = {
                onChangeTab()
                selection = 2
            })
            Tab(text = { Text("Cfg") }, selected = selection == 3, onClick = {
                onChangeTab()
                selection = 3
            })
            Tab(text = { Text("Blob") }, selected = selection == 4, onClick = {
                onChangeTab()
                selection = 4
            })
        }

        when (selection) {
            0 -> InfoTab(client)
            1 -> CredentialsManagementTab(client)
            2 -> PINTab(client)
            3 -> ConfigTab(client)
            4 -> BlobsTab(client)
        }
    }
}

@Composable
fun InfoTab(client: CTAPClient) {
    var info by remember { mutableStateOf<GetInfoResponse?>(null) }

    if (info == null) {
        Button(onClick = {
            info = client.getInfo()
        }) {
            Text("Get Info")
        }
    } else {
        InfoDisplay(info)
    }
}

@Composable
fun OneRPManagementView(rpWithHash: RPWithHash, onListCreds: () -> Unit = {}) {
    val rpName = (rpWithHash.rp.name ?: rpWithHash.rp.id) ?: "Unknown RP"
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(rpName, modifier = Modifier.padding(3.dp))
        Button(onClick = onListCreds) {
            Text("List Credentials")
        }
    }
}

@OptIn(ExperimentalStdlibApi::class)
@Composable
fun OneCredView(cred: StoredCredentialData, onDelete: () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val userDesc = arrayOf(
            cred.user.displayName ?: "",
            if (cred.user.name != null) "(${cred.user.name})" else "",
            if (cred.user.displayName == null && cred.user.name == null) cred.user.id.toHexString() else "",
        ).joinToString(" ")
        Text(userDesc)
        Button(onClick = onDelete) {
            Text("Delete")
        }
    }
}

@Composable
fun CredentialsManagementTab(client: CTAPClient) {
    var rpList by remember { mutableStateOf<List<RPWithHash>?>(null) }
    var chosenRP by remember { mutableStateOf<RPWithHash?>(null) }
    var credList by remember { mutableStateOf<List<StoredCredentialData>?>(null) }
    var credMeta by remember { mutableStateOf<CredentialManagementGetMetadataResponse?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Column {
        val meta = credMeta
        if (meta == null) {
            Button(onClick = {
                coroutineScope.launch {
                    credMeta = client.credentialManagement().getCredsMetadata()
                }
            }) {
                Text("Get Metadata")
            }
        } else {
            CredsMetadataDisplay(meta)
        }
        Button(onClick = {
            credMeta = null
            credList = null
            chosenRP = null
            coroutineScope.launch {
                rpList = client.credentialManagement().enumerateRPs()
            }
        }) {
            Text("List Relying Parties")
        }
        val rps = rpList
        if (rps != null) {
            for (rpWithHash in rps) {
                OneRPManagementView(rpWithHash, onListCreds = {
                    coroutineScope.launch {
                        val pinToken = client.getPinUVTokenUsingAppropriateMethod(
                            desiredPermissions = CTAPPinPermission.CREDENTIAL_MANAGEMENT.value,
                            desiredRpId = rpWithHash.rp.id,
                        )
                        credList = client.credentialManagement().enumerateCredentials(
                            rpIDHash = rpWithHash.rpIDHash,
                            pinUVToken = pinToken,
                        )
                    }
                    rpList = null
                    credMeta = null
                    chosenRP = rpWithHash
                })
            }
        }
        val creds = credList
        if (creds != null) {
            Text("Credentials for RP ${chosenRP?.rp?.name ?: chosenRP?.rp?.id}")
            for (cred in creds) {
                OneCredView(cred, onDelete = {
                    coroutineScope.launch {
                        val pinToken = client.getPinUVTokenUsingAppropriateMethod(
                            desiredPermissions = CTAPPinPermission.CREDENTIAL_MANAGEMENT.value,
                            desiredRpId = chosenRP?.rp?.id,
                        )
                        client.credentialManagement().deleteCredential(
                            cred.credentialID,
                            pinUVToken = pinToken,
                        )
                    }
                    rpList = null
                    chosenRP = null
                    credList = null
                    credMeta = null
                })
            }
        }
    }
}

@Composable
fun CredsMetadataDisplay(meta: CredentialManagementGetMetadataResponse) {
    Text(
        "${meta.existingDiscoverableCredentialsCount} credentials used;" +
            " ${meta.maxPossibleRemainingCredentialsCount} remain",
    )
}

@Composable
fun PINTab(client: CTAPClient) {
    val coroutineScope = rememberCoroutineScope()

    SubmittableText(placeholder = {
        Text("New PIN")
    }, buttonContent = {
        Text("Set/Change PIN")
    }) {
        coroutineScope.launch {
            client.setOrChangePIN(it)
        }
    }
}

@Composable
fun ConfigTab(client: CTAPClient) {
    Text("Placeholder: Config Management")
}

@Composable
fun BlobsTab(client: CTAPClient) {
    Text("Placeholder: Large Blob Management")
}

internal fun fakeClient(): CTAPClient {
    val library = FIDOkLibrary.init(NullCryptoProvider())
    return library.ctapClient(
        object : AuthenticatorDevice {
            override fun sendBytes(bytes: ByteArray) = byteArrayOf()
            override fun getTransports() = listOf<AuthenticatorTransport>()
        },
    )
}

@Composable
@Preview
internal fun authenticatorManagementDisplayPreview() {
    AuthenticatorManagementDisplay(fakeClient())
}

@Composable
@Preview
internal fun credentialManagementTabPreview() {
    CredentialsManagementTab(fakeClient())
}

@Composable
@Preview
internal fun pinTabPreview() {
    PINTab(fakeClient())
}

@Composable
@Preview
internal fun oneRPManagementPreview() {
    OneRPManagementView(
        RPWithHash(
            rp = PublicKeyCredentialRpEntity(
                name = "Some RP",
            ),
            rpIDHash = byteArrayOf(),
        ),
    )
}

@Composable
@Preview
internal fun oneCredPreview() {
    OneCredView(
        StoredCredentialData(
            user = PublicKeyCredentialUserEntity(
                id = Random.nextBytes(32),
                name = "someuser",
                displayName = "Bob McBobs",
            ),
            credentialID = PublicKeyCredentialDescriptor(
                Random.nextBytes(64),
            ),
            publicKey = COSEKey(
                kty = 2,
                alg = -7,
                crv = 1,
                x = Random.nextBytes(32),
                y = Random.nextBytes(32),
            ),
            credProtect = 3u,
        ),
    )
}
