package us.q3q.fidok.nfc

import android.nfc.tech.IsoDep
import co.touchlab.kermit.Logger
import us.q3q.fidok.ctap.AuthenticatorDevice
import us.q3q.fidok.ctap.AuthenticatorTransport
import us.q3q.fidok.ctap.DeviceCommunicationException
import us.q3q.fidok.pcsc.CTAPPCSC

const val NFC_TIMEOUT_MS = 5000

@OptIn(ExperimentalStdlibApi::class)
class AndroidNFCDevice(
    private val tag: IsoDep,
) : AuthenticatorDevice {
    @Throws(DeviceCommunicationException::class)
    override fun sendBytes(bytes: ByteArray): ByteArray {
        if (!tag.isConnected) {
            tag.connect()
            tag.timeout = NFC_TIMEOUT_MS
        }

        // TODO: packetize using tag.getMaxTransceiveLength
        return CTAPPCSC.sendAndReceive(
            bytes,
            selectApplet = true,
            useExtendedMessages = tag.isExtendedLengthApduSupported,
        ) {
            Logger.v { "About to send ${it.size} NFC bytes to $tag" }

            val res = tag.transceive(it)

            Logger.v { "Received ${res.size} NFC bytes from $tag: ${res.toHexString()}" }

            res
        }
    }

    override fun getTransports(): List<AuthenticatorTransport> {
        return listOf(AuthenticatorTransport.NFC)
    }

    override fun toString(): String {
        return "NFC Device $tag"
    }
}
