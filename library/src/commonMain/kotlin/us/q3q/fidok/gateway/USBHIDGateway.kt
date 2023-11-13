package us.q3q.fidok.gateway

import co.touchlab.kermit.Logger
import kotlinx.coroutines.delay
import us.q3q.fidok.ctap.DeviceCommunicationException
import us.q3q.fidok.ctap.FIDOkLibrary
import us.q3q.fidok.ctap.IncorrectDataException
import us.q3q.fidok.hid.CTAPHID
import us.q3q.fidok.hid.CTAPHIDCommand
import us.q3q.fidok.hid.CTAPHIDError
import us.q3q.fidok.hid.HID_BROADCAST_CHANNEL
import us.q3q.fidok.hid.HID_DEFAULT_PACKET_SIZE
import kotlin.experimental.and
import kotlin.math.min
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

const val FIDOK_HID_PRODUCT = 0x27d9
const val FIDOK_HID_VENDOR = 0x16c0
const val FIDOK_HID_DEVICE_NAME = "FIDOk Virtual HID Device"
val FIDOK_REPORT_DESCRIPTOR = byteArrayOf(
    0x06, 0xD0.toByte(), 0xF1.toByte(), // Usage Page (FIDO = F1D0)
    0x09, 0x01, // Usage (CTAPHID)
    0xA1.toByte(), 0x01, // Collection (Application)
    0x09, 0x20, // Usage (Data In)
    0x15, 0x00, // Logical min (0)
    0x26, 0xFF.toByte(), 0x00, // Logical max (255 = FF,00)
    0x75, 0x08, // Report Size (8)
    0x95.toByte(), 0x40, // Report count (64 bytes per packet = 0x40)
    0x81.toByte(), 0x02, // Input(HID_Data | HID_Absolute | HID_Variable)
    0x09, 0x21, // Usage (Data Out)
    0x15, 0x00, // Logical min (0)
    0x26, 0xFF.toByte(), 0x00, // Logical max (255 = FF,00)
    0x75, 0x08, // Report Size (8)
    0x95.toByte(), 0x40, // Report count (64 bytes per packet = 0x40)
    0x91.toByte(), 0x02, // Output(HID_Data | HID_Absolute | HID_Variable)
    0xc0.toByte(), // End Collection
)

val HID_TIMEOUT = 30.seconds
val HID_POLL_DELAY = 500.milliseconds
val HID_RECV_DELAY = 25.milliseconds

@OptIn(ExperimentalStdlibApi::class)
interface HIDGatewayBase {

    suspend fun handlePacket(gateway: HIDGateway, library: FIDOkLibrary, bytes: ByteArray) {
        if (bytes.size < 5) {
            throw DeviceCommunicationException("Received short packet: only ${bytes.size} bytes long")
        }
        val channelId = CTAPHID.assembleChannelFromBytes(bytes, 0)

        Logger.v { "Incoming HID channel ID $channelId ($HID_BROADCAST_CHANNEL)" }

        val isFirstPacket = (bytes[4] and 0x80.toByte()) != 0x00.toByte()
        val seqOrCmd = bytes[4] and 0x7F

        if (!isFirstPacket) {
            sendError(gateway, channelId, CTAPHIDError.INVALID_SEQ)
            throw DeviceCommunicationException("Follow-up packet received when expecting initial")
        }

        val len = CTAPHID.assembleLengthFromBytes(bytes, 5)

        if (channelId == HID_BROADCAST_CHANNEL) {
            if (seqOrCmd.toUByte() != CTAPHIDCommand.INIT.value) {
                sendError(gateway, channelId, CTAPHIDError.INVALID_CMD)
                throw DeviceCommunicationException("Command other than INIT ($seqOrCmd) received on HID broadcast channel")
            }

            openOrResetChannel(gateway, channelId, len, bytes)
        } else {
            val cmd = CTAPHIDCommand.entries.find {
                it.value == seqOrCmd.toUByte()
            }
            if (cmd == null) {
                sendError(gateway, channelId, CTAPHIDError.INVALID_CMD)
                throw DeviceCommunicationException("Unknown CTAPHID command $seqOrCmd")
            }

            Logger.v { "Incoming HID command $cmd" }

            val accumulator = arrayListOf<Byte>()
            accumulator.addAll(bytes.copyOfRange(7, min(len.toInt() + 7, bytes.size)).toList())

            val read = try {
                CTAPHID.continueReadingFromChannel(channelId, len, accumulator) {
                    gateway.recv()
                }
            } catch (e: IncorrectDataException) {
                Logger.e("Incorrect data reading from HID channel", e)
                sendError(gateway, channelId, CTAPHIDError.INVALID_CMD)
                return
            }

            Logger.i { "Read from HID channel: ${read.toHexString()}" }

            when (cmd) {
                CTAPHIDCommand.CBOR -> {
                    handleCBORMessage(library, gateway, channelId, cmd, read)
                }
                CTAPHIDCommand.INIT -> {
                    openOrResetChannel(gateway, channelId, len, bytes)
                }
                CTAPHIDCommand.PING -> {
                    CTAPHID.sendToChannel(cmd, channelId, read, read.size) {
                        gateway.send(it)
                    }
                }
                else -> {
                    Logger.w { "Unhandled CTAPHID command $cmd" }
                    sendError(gateway, channelId, CTAPHIDError.INVALID_CMD)
                }
            }
        }
    }

    private suspend fun handleCBORMessage(
        library: FIDOkLibrary,
        gateway: HIDGateway,
        channelId: UInt,
        cmd: CTAPHIDCommand,
        message: ByteArray,
    ) {
        val delayAmt = HID_POLL_DELAY
        val now = TimeSource.Monotonic.markNow()
        while (now.elapsedNow() < HID_TIMEOUT) {
            val devices = library.listDevices()
            if (devices.isEmpty()) {
                Logger.d { "No CTAP devices found; waiting for one to appear" }
            } else {
                try {
                    val ret = devices[0].sendBytes(message)
                    if (ret.isNotEmpty()) {
                        Logger.v { "Sending HID response ${ret.toHexString()}" }

                        val packets = CTAPHID.packetizeMessage(cmd, channelId, ret, HID_DEFAULT_PACKET_SIZE)
                        for (packet in packets) {
                            gateway.send(packet)
                        }
                    }
                    return
                } catch (e: DeviceCommunicationException) {
                    Logger.w("Failure communicating with device", e)
                }
            }
            delay(delayAmt)
        }
        sendError(gateway, channelId, CTAPHIDError.MSG_TIMEOUT)
    }

    private suspend fun sendError(gateway: HIDGateway, channelId: UInt, error: CTAPHIDError) {
        CTAPHID.sendToChannel(CTAPHIDCommand.ERROR, channelId, byteArrayOf(error.value), HID_DEFAULT_PACKET_SIZE) {
            gateway.send(it)
        }
    }

    private suspend fun openOrResetChannel(
        gateway: HIDGateway,
        channelId: UInt,
        len: UInt,
        bytes: ByteArray,
    ) {
        Logger.v { "HID new channel requested" }

        if (len != 8u) {
            sendError(gateway, channelId, CTAPHIDError.INVALID_LEN)
            throw DeviceCommunicationException("HID INIT packet had invalid length: $len")
        }

        val newChannel = if (channelId == HID_BROADCAST_CHANNEL) {
            Random.nextBytes(4)
        } else {
            byteArrayOf(
                ((channelId and 0xFF000000u) shr 24).toByte(),
                ((channelId and 0x00FF0000u) shr 16).toByte(),
                ((channelId and 0x0000FF00u) shr 8).toByte(),
                (channelId and 0x000000FFu).toByte(),
            )
        }

        val pkt = byteArrayOf(
            bytes[7],
            bytes[8],
            bytes[9],
            bytes[10],
            bytes[11],
            bytes[12],
            bytes[13],
            bytes[14], // nonce
            newChannel[0],
            newChannel[1],
            newChannel[2],
            newChannel[3],
            0x02, // protocol version
            0x01, // device version (major)
            0x00, // device version (minor)
            0x00, // device version (point)
            0x0C.toByte(), // CTAP capabilities bitfield - CBOR but no MSG or WINK.
            // To implement CTAP1/U2F this would need to remove 0x08/NOMSG
        )

        CTAPHID.sendToChannel(CTAPHIDCommand.INIT, channelId, pkt, HID_DEFAULT_PACKET_SIZE) {
            gateway.send(it)
        }
    }
}

@Suppress("UNUSED_PARAMETER")
class StubHIDGateway() : HIDGatewayBase {

    suspend fun listenForever(library: FIDOkLibrary) {
        throw NotImplementedError()
    }

    suspend fun send(bytes: ByteArray) {
        throw NotImplementedError()
    }

    suspend fun recv(): ByteArray {
        throw NotImplementedError()
    }
}

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class HIDGateway() : HIDGatewayBase {

    suspend fun listenForever(library: FIDOkLibrary)

    suspend fun send(bytes: ByteArray)
    suspend fun recv(): ByteArray
}
