package us.q3q.fidok.ui

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.loadImageBitmap
import io.github.g0dkar.qrcode.ErrorCorrectionLevel
import io.github.g0dkar.qrcode.QRCode
import io.github.g0dkar.qrcode.QRCodeDataType
import us.q3q.fidok.cable.CaBLECode
import us.q3q.fidok.crypto.NullCryptoProvider
import us.q3q.fidok.ctap.Library
import java.io.ByteArrayInputStream
import java.time.Instant
import kotlin.random.Random

@Composable
fun QRCodeView(data: String, size: Int = 1) {
    val code = QRCode(
        data,
        errorCorrectionLevel = ErrorCorrectionLevel.M,
        dataType = QRCodeDataType.UPPER_ALPHA_NUM,
    )
    val render = code.render(
        cellSize = size,
        margin = 1,
    )
    val renderedBytes = render.getBytes()
    val imageBitmap = loadImageBitmap(ByteArrayInputStream(renderedBytes))
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawImage(imageBitmap)
    }
}

@Preview
@Composable
fun QRCodeTextPreview() {
    QRCodeView("SOMETHING1WICKED", size = 5)
}

@Preview
@Composable
fun QRCodeCaBLEPreview() {
    val code = CaBLECode(
        publicKey = Random.nextBytes(33),
        secret = Random.nextBytes(16),
        knownTunnelServerDomains = 2u,
        currentEpochSeconds = Instant.now().toEpochMilli().toULong(),
    )
    val url = Library.init(NullCryptoProvider()).caBLESupport().buildCaBLEURL(code)
    QRCodeView(url, size = 2)
}
