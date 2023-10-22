package us.q3q.fidok.gateway

import co.touchlab.kermit.Logger
import kotlinx.coroutines.delay
import us.q3q.fidok.ctap.FIDOkLibrary
import us.q3q.fidok.hid.CTAPHID
import us.q3q.fidok.hid.CTAPHIDCommand
import us.q3q.fidok.hid.HID_BROADCAST_CHANNEL
import us.q3q.fidok.hid.HID_DEFAULT_PACKET_SIZE
import kotlin.experimental.and
import kotlin.math.min
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

const val FIDOK_HID_PRODUCT = 0x9393
const val FIDOK_HID_VENDOR = 0x9494
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

@OptIn(ExperimentalStdlibApi::class)
interface HIDGatewayBase {

    suspend fun handlePacket(gateway: HIDGateway, library: FIDOkLibrary, bytes: ByteArray) {
        if (bytes.size < 5) {
            throw IllegalStateException("Received short packet: only ${bytes.size} bytes long")
        }
        val channelId = CTAPHID.assembleChannelFromBytes(bytes, 0)

        Logger.v { "Incoming HID channel ID $channelId ($HID_BROADCAST_CHANNEL)" }

        val isFirstPacket = (bytes[4] and 0x80.toByte()) != 0x00.toByte()
        val seqOrCmd = bytes[4] and 0x7F

        if (!isFirstPacket) {
            throw IllegalStateException("Follow-up packet received when expecting initial")
        }

        val len = CTAPHID.assembleLengthFromBytes(bytes, 5)

        if (channelId == HID_BROADCAST_CHANNEL) {
            Logger.v { "HID new channel requested" }

            if (seqOrCmd.toUByte() != CTAPHIDCommand.INIT.value) {
                throw IllegalStateException("Command other than INIT ($seqOrCmd) received on HID broadcast channel")
            }

            if (len != 8u) {
                throw IllegalStateException("HID INIT packet had invalid length: $len")
            }

            val newChannel = Random.nextBytes(4)

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
        } else {
            val cmd = CTAPHIDCommand.entries.find {
                it.value == seqOrCmd.toUByte()
            } ?: throw IllegalStateException("Unknown CTAPHID command $seqOrCmd")

            Logger.v { "Incoming HID command $cmd" }

            val accumulator = arrayListOf<Byte>()
            accumulator.addAll(bytes.copyOfRange(7, min(len.toInt() + 7, bytes.size)).toList())

            val read = CTAPHID.continueReadingFromChannel(channelId, len, accumulator) {
                gateway.recv()
            }

            Logger.i { "Read from HID channel: ${read.toHexString()}" }

            if (cmd == CTAPHIDCommand.CBOR) {
                while (true) {
                    val devices = library.listDevices()
                    if (devices.isEmpty()) {
                        Logger.d { "No CTAP devices found; waiting for one to appear" }

                        delay(500.milliseconds)
                        continue
                    }
                    val ret = devices[0].sendBytes(read)
                    if (ret.isNotEmpty()) {
                        val packets = CTAPHID.packetizeMessage(cmd, channelId, ret, HID_DEFAULT_PACKET_SIZE)
                        for (packet in packets) {
                            gateway.send(packet)
                        }
                        break
                    }
                }
            }
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

expect class HIDGateway() : HIDGatewayBase {

    suspend fun listenForever(library: FIDOkLibrary)

    suspend fun send(bytes: ByteArray)
    suspend fun recv(): ByteArray
}
