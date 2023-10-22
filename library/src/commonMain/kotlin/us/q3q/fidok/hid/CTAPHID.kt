
package us.q3q.fidok.hid

import co.touchlab.kermit.Logger
import kotlinx.coroutines.runBlocking
import us.q3q.fidok.ctap.DeviceCommunicationException
import us.q3q.fidok.ctap.IncorrectDataException
import kotlin.math.min
import kotlin.random.Random

enum class CTAPHIDCommand(val value: UByte) {
    PING(0x01u),
    MSG(0x03u),
    LOCK(0x04u),
    INIT(0x06u),
    WINK(0x08u),
    CBOR(0x10u),
    CANCEL(0x11u),
    KEEPALIVE(0x3Bu),
    ERROR(0x3Fu),
    VENDOR_FIRST(0x40u),
    VENDOR_LAST(0x7Fu),
}

enum class CTAPHIDError(val value: UByte) {
    INVALID_CMD(0x01u),
    INVALID_PAR(0x02u),
    INVALID_LEN(0x03u),
    INVALID_SEQ(0x04u),
    MSG_TIMEOUT(0x05u),
    CHANNEL_BUSY(0x06u),
    LOCK_REQUIRED(0x0Au),
    INVALID_CHANNEL(0x0Bu),
    OTHER(0x7Fu),
}

const val HID_DEFAULT_PACKET_SIZE = 64
const val HID_BROADCAST_CHANNEL = 0xFFFFFFFFu

class CTAPHID {
    companion object {
        fun initPacket(channel: UInt, cmd: CTAPHIDCommand, totalLength: Int, data: ByteArray, packetSize: Int): ByteArray {
            if (data.size > packetSize - 7) {
                throw DeviceCommunicationException("Overlarge packet sent to HID device: ${data.size} bytes")
            }
            if (totalLength > UShort.MAX_VALUE.toInt()) {
                throw IncorrectDataException("Too much data sent to HID device: $totalLength bytes")
            }
            val ret = arrayListOf(
                ((channel and 0xFF000000u) shr 24).toByte(),
                ((channel and 0x00FF0000u) shr 16).toByte(),
                ((channel and 0x0000FF00u) shr 8).toByte(),
                (channel and 0x000000FFu).toByte(),
                cmd.value.or((0x80).toUByte()).toByte(),
                ((totalLength and 0xFF00) shr 8).toByte(),
                (totalLength and 0x00FF).toByte(),
            )
            ret.addAll(data.toList())
            while (ret.size < packetSize) {
                ret.add(0x00)
            }
            return ret.toByteArray()
        }

        fun continuationPacket(channel: UInt, seq: UByte, data: ByteArray, packetSize: Int): ByteArray {
            if (data.size > packetSize - 5) {
                throw DeviceCommunicationException("Overlarge continuation packet sent to HID device: ${data.size} bytes")
            }
            val ret = arrayListOf(
                ((channel and 0xFF000000u) shr 24).toByte(),
                ((channel and 0x00FF0000u) shr 16).toByte(),
                ((channel and 0x0000FF00u) shr 8).toByte(),
                (channel and 0x000000FFu).toByte(),
                seq.toByte(),
            )
            ret.addAll(data.toList())
            while (ret.size < packetSize) {
                ret.add(0x00)
            }
            return ret.toByteArray()
        }

        fun packetizeMessage(command: CTAPHIDCommand, channel: UInt, bytes: ByteArray, packetSize: Int): List<ByteArray> {
            val bytesForFirstPacket = bytes.copyOfRange(0, min(bytes.size, packetSize - 7))
            val pkt = initPacket(channel, command, bytes.size, bytesForFirstPacket, packetSize)
            val packets = arrayListOf(pkt)

            var seq: UByte = 0u
            var sent = bytesForFirstPacket.size
            while (sent < bytes.size) {
                val bytesForNextPacket = bytes.copyOfRange(sent, min(bytes.size, sent + packetSize - 5))
                Logger.v { "Continuation packet: ${bytesForNextPacket.size} bytes" }
                val nextPacket = continuationPacket(channel, seq++, bytesForNextPacket, packetSize)
                packets.add(nextPacket)
                sent += bytesForNextPacket.size
            }

            return packets
        }

        private suspend fun readIgnoringKeepalives(channel: UInt, cmd: CTAPHIDCommand, reader: suspend () -> ByteArray): ByteArray {
            while (true) {
                val response = reader()
                if (response.size < 6) {
                    throw IncorrectDataException("HID device returned short packet, ${response.size} bytes long")
                }
                for (i in 0..<4) {
                    if (response[i] != (channel shr 8 * (3 - i)).toByte()) {
                        throw IncorrectDataException("Channel mismatch in reponse on HID device")
                    }
                }
                if (response[4].toUByte() and 0x80u == (0x00u).toUByte()) {
                    throw IncorrectDataException("Got a non-initial packet at the start of an HID sequence!")
                }
                val gottenCmd = response[4].toUByte() and 0x7Fu
                if (gottenCmd != cmd.value) {
                    if (gottenCmd == CTAPHIDCommand.ERROR.value) {
                        val errorType = CTAPHIDError.entries.find { it.value == response[6].toUByte() }
                        if (errorType == null) {
                            throw IncorrectDataException("Unknown CTAPHID error type ${response[6]} in response to ${cmd.name}")
                        } else {
                            throw DeviceCommunicationException("HID ${cmd.name} failed: ${errorType.name}")
                        }
                    }
                    if (gottenCmd == CTAPHIDCommand.KEEPALIVE.value) {
                        // Keepalives are fine... ignore them.
                        continue
                    }
                    throw IncorrectDataException("Tried to use command $cmd, but got a response for command $gottenCmd")
                }
                return response
            }
        }

        suspend fun readFromChannel(channel: UInt, cmd: CTAPHIDCommand, reader: suspend () -> ByteArray): ByteArray {
            val response = readIgnoringKeepalives(channel, cmd, reader)
            val len = assembleLengthFromBytes(response, 5)
            val accumulated = arrayListOf<Byte>()
            accumulated.addAll(response.copyOfRange(7, min(len.toInt() + 7, response.size)).toList())
            return continueReadingFromChannel(channel, len, accumulated, reader)
        }

        suspend fun continueReadingFromChannel(
            channel: UInt,
            len: UInt,
            accumulator: ArrayList<Byte>,
            reader: suspend () -> ByteArray,
        ): ByteArray {
            var seq = 0u
            val expected = len.toInt()
            while (accumulator.size < expected) {
                // keep reading...
                Logger.v { "Response len $len, accumulated ${accumulator.size}" }
                val nextBytes = reader()
                for (i in 0..<4) {
                    if (nextBytes[i] != (channel shr 8 * (3 - i)).toByte()) {
                        throw IncorrectDataException("Channel mismatch in subsequent response on HID device")
                    }
                }
                val gottenSeq = nextBytes[4].toUByte()
                if (gottenSeq and 0x80u != (0x00u).toUByte()) {
                    throw IncorrectDataException("Got initial packet in continuation HID sequence")
                }
                if (gottenSeq.toUInt() != seq++) {
                    throw IncorrectDataException("Invalid HID sequence number: expected $seq, got $gottenSeq")
                }
                val remaining = expected - accumulator.size
                for (i in 5..<min(remaining + 5, nextBytes.size)) {
                    accumulator.add(nextBytes[i])
                }
            }
            return accumulator.toByteArray()
        }

        fun assembleChannelFromBytes(bytes: ByteArray, startOffset: Int): UInt {
            return (bytes[startOffset].toUByte().toUInt() shl 24) +
                (bytes[startOffset + 1].toUByte().toUInt() shl 16) +
                (bytes[startOffset + 2].toUByte().toUInt() shl 8) +
                bytes[startOffset + 3].toUByte()
        }

        fun assembleLengthFromBytes(bytes: ByteArray, startOffset: Int): UInt {
            return (bytes[startOffset].toUByte().toUInt() shl 8) +
                bytes[startOffset + 1].toUByte()
        }

        @OptIn(ExperimentalStdlibApi::class)
        suspend fun openChannel(packetSize: Int, sender: (bytes: ByteArray) -> Unit, receiver: () -> ByteArray): UInt {
            Logger.d { "Opening new channel with HID device" }
            val nonce = Random.nextBytes(8)
            sender(initPacket(HID_BROADCAST_CHANNEL, CTAPHIDCommand.INIT, 8, nonce, packetSize))
            val response = readFromChannel(HID_BROADCAST_CHANNEL, CTAPHIDCommand.INIT, receiver)
            if (response.size < 12) {
                throw IncorrectDataException("HID device returned short channel-open packet, ${response.size} bytes long")
            }
            for (i in nonce.indices) {
                if (nonce[i] != response[i]) {
                    throw IncorrectDataException("Got mismatching nonce in response to HID open")
                }
            }
            val ret = assembleChannelFromBytes(response, 8)

            Logger.d { "HID channel opened: ${ret.toHexString()}" }
            return ret
        }

        suspend fun sendToChannel(command: CTAPHIDCommand, channel: UInt, bytes: ByteArray, packetSize: Int, sender: suspend (bytes: ByteArray) -> Unit) {
            val packets = packetizeMessage(command, channel, bytes, packetSize)
            for (pkt in packets) {
                sender(pkt)
            }
        }

        @Throws(DeviceCommunicationException::class)
        fun sendAndReceive(
            sender: (bytes: ByteArray) -> Unit,
            receiver: () -> ByteArray,
            command: CTAPHIDCommand,
            bytes: ByteArray,
            packetSize: Int = HID_DEFAULT_PACKET_SIZE,
        ): ByteArray {
            return runBlocking {
                val channel = openChannel(packetSize, sender, receiver)
                sendToChannel(command, channel, bytes, packetSize, sender)
                readFromChannel(channel, command, receiver)
            }
        }
    }
}
