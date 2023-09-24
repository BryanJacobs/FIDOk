package us.q3q.fidok.ui

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import us.q3q.fidok.crypto.NullCryptoProvider
import us.q3q.fidok.ctap.Device
import us.q3q.fidok.ctap.Library
import us.q3q.fidok.ctap.commands.GetInfoResponse

@Composable
fun MainView(library: Library, deviceConstructor: () -> List<Device>) {
    var devices by remember { mutableStateOf<List<Device>?>(null) }
    var infoByDevice by remember { mutableStateOf<Map<Int, GetInfoResponse>>(hashMapOf()) }
    MaterialTheme {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            val devs = devices
            Button(onClick = { devices = deviceConstructor() }) {
                Text(if (devs == null) "Click to list" else "There are ${devs.size} device(s)")
            }
            (0..<(devs?.size ?: 0)).map { deviceNum ->
                Button(onClick = {
                    val info = library.ctapClient(devices!![deviceNum]).getInfo()
                    infoByDevice = infoByDevice.plus(deviceNum to info)
                }) {
                    Text("Get Info on Device $deviceNum")
                }
                infoByDevice[deviceNum]?.let {
                    InfoDisplay(it)
                }
            }
        }
    }
}

@Preview
@Composable
fun MainViewPreview() {
    MainView(Library.init(NullCryptoProvider())) { listOf() }
}
