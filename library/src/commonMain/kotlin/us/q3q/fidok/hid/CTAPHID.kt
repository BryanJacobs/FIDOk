
package us.q3q.fidok.hid

import co.touchlab.kermit.Logger
import kotlin.math.min
import kotlin.random.Random
import kotlin.random.nextUBytes

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

const val PACKET_SIZE = 64
const val BROADCAST_CHANNEL = 0xFFFFFFFFu

@OptIn(ExperimentalUnsignedTypes::class)
class CTAPHID {
    companion object {
        fun initPacket(channel: UInt, cmd: CTAPHIDCommand, totalLength: Int, data: UByteArray, packetSize: Int): UByteArray {
            if (data.size > packetSize - 7) {
                throw RuntimeException("Overlarge packet sent to HID device: ${data.size} bytes")
            }
            if (totalLength > UShort.MAX_VALUE.toInt()) {
                throw RuntimeException("Too much data sent to HID device: $totalLength bytes")
            }
            val ret = arrayListOf(
                0x00u, // report number
                ((channel and 0xFF000000u) shr 24).toUByte(),
                ((channel and 0x00FF0000u) shr 16).toUByte(),
                ((channel and 0x0000FF00u) shr 8).toUByte(),
                (channel and 0x000000FFu).toUByte(),
                cmd.value.or((0x80).toUByte()),
                ((totalLength and 0xFF00) shr 8).toUByte(),
                (totalLength and 0x00FF).toUByte(),
            )
            ret.addAll(data.toList())
            while (ret.size < packetSize + 1) {
                ret.add(0x00u)
            }
            return ret.toUByteArray()
        }

        fun continuationPacket(channel: UInt, seq: UByte, data: UByteArray, packetSize: Int): UByteArray {
            if (data.size > packetSize - 5) {
                throw RuntimeException("Overlarge continuation packet sent to HID device: ${data.size} bytes")
            }
            val ret = arrayListOf(
                0x00u, // report number
                ((channel and 0xFF000000u) shr 24).toUByte(),
                ((channel and 0x00FF0000u) shr 16).toUByte(),
                ((channel and 0x0000FF00u) shr 8).toUByte(),
                (channel and 0x000000FFu).toUByte(),
                seq,
            )
            ret.addAll(data.toList())
            while (ret.size < packetSize + 1) {
                ret.add(0x00u)
            }
            return ret.toUByteArray()
        }

        fun packetizeMessage(command: CTAPHIDCommand, channel: UInt, bytes: UByteArray, packetSize: Int): List<UByteArray> {
            val bytesForFirstPacket = bytes.copyOfRange(0, min(bytes.size, packetSize - 7))
            val pkt = initPacket(channel, command, bytes.size, bytesForFirstPacket, packetSize)
            val packets = arrayListOf(pkt)

            var seq: UByte = 0u
            var sent = bytesForFirstPacket.size
            while (sent < bytes.size) {
                val bytesForNextPacket = bytes.copyOfRange(sent, min(bytes.size, sent + packetSize - 5))
                Logger.v("Continuation packet: ${bytesForNextPacket.size} bytes")
                val nextPacket = continuationPacket(channel, ++seq, bytesForNextPacket, packetSize)
                packets.add(nextPacket)
                sent += bytesForNextPacket.size
            }

            return packets
        }

        private fun readIgnoringKeepalives(channel: UInt, cmd: CTAPHIDCommand, reader: () -> UByteArray): UByteArray {
            while (true) {
                val response = reader()
                if (response.size < 6) {
                    throw RuntimeException("HID device returned short packet, ${response.size} bytes long")
                }
                for (i in 0..<4) {
                    if (response[i] != (channel shr 8 * (3 - i)).toUByte()) {
                        throw RuntimeException("Channel mismatch in reponse on HID device")
                    }
                }
                if (response[4] and 0x80u == (0x00u).toUByte()) {
                    throw RuntimeException("Got a non-initial packet at the start of an HID sequence!")
                }
                val gottenCmd = response[4] and 0x7Fu
                if (gottenCmd != cmd.value) {
                    if (gottenCmd == CTAPHIDCommand.ERROR.value) {
                        val errorType = CTAPHIDError.entries.find { it.value == response[6] }
                        if (errorType == null) {
                            throw RuntimeException("Unknown CTAPHID error type ${response[6]} in response to ${cmd.name}")
                        } else {
                            throw RuntimeException("HID ${cmd.name} failed: ${errorType.name}")
                        }
                    }
                    if (gottenCmd == CTAPHIDCommand.KEEPALIVE.value) {
                        // Keepalives are fine... ignore them.
                        continue
                    }
                    throw RuntimeException("Tried to use command $cmd, but got a response for command $gottenCmd")
                }
                return response
            }
        }

        fun readFromChannel(channel: UInt, cmd: CTAPHIDCommand, reader: () -> UByteArray): UByteArray {
            val response = readIgnoringKeepalives(channel, cmd, reader)
            val len = (response[5].toUInt() shl 8) + response[6]
            val accumulated = arrayListOf<UByte>()
            accumulated.addAll(response.toList().subList(7, min(len.toInt() + 7, response.size)))
            var seq = 0u
            val expected = len.toInt()
            while (accumulated.size < expected) {
                // keep reading...
                Logger.v("Response ${response.size}, len $len, accumulated ${accumulated.size}")
                val nextBytes = reader()
                for (i in 0..<4) {
                    if (nextBytes[i] != (channel shr 8 * (3 - i)).toUByte()) {
                        throw RuntimeException("Channel mismatch in subsequent response on HID device")
                    }
                }
                val gottenSeq = nextBytes[4]
                if (gottenSeq and 0x80u != (0x00u).toUByte()) {
                    throw RuntimeException("Got initial packet in continuation HID sequence!")
                }
                if (gottenSeq.toUInt() != seq++) {
                    throw RuntimeException("Invalid HID sequence number: expected $seq, got $gottenSeq")
                }
                val remaining = expected - accumulated.size
                for (i in 5..<min(remaining + 5, nextBytes.size)) {
                    accumulated.add(nextBytes[i])
                }
            }
            return accumulated.toUByteArray()
        }

        fun openChannel(packetSize: Int, sender: (bytes: UByteArray) -> Unit, receiver: () -> UByteArray): UInt {
            Logger.d("Opening new channel with HID device")
            val nonce = Random.nextUBytes(8)
            sender(initPacket(BROADCAST_CHANNEL, CTAPHIDCommand.INIT, 8, nonce, packetSize))
            val response = readFromChannel(BROADCAST_CHANNEL, CTAPHIDCommand.INIT, receiver)
            if (response.size < 12) {
                throw RuntimeException("HID device returned short channel-open packet, ${response.size} bytes long")
            }
            for (i in nonce.indices) {
                if (nonce[i] != response[i]) {
                    throw RuntimeException("Got mismatching nonce in response to HID open")
                }
            }
            val ret = (response[8].toUInt() shl 24) +
                (response[9].toUInt() shl 16) +
                (response[10].toUInt() shl 8) +
                response[11]

            Logger.d("HID channel opened!")
            return ret
        }

        fun sendToChannel(command: CTAPHIDCommand, channel: UInt, bytes: UByteArray, packetSize: Int, sender: (bytes: UByteArray) -> Unit) {
            val packets = packetizeMessage(command, channel, bytes, packetSize)
            for (pkt in packets) {
                sender(pkt)
            }
        }

        fun sendAndReceive(
            sender: (bytes: UByteArray) -> Unit,
            receiver: () -> UByteArray,
            command: CTAPHIDCommand,
            bytes: UByteArray,
            packetSize: Int = PACKET_SIZE,
        ): UByteArray {
            val channel = openChannel(packetSize, sender, receiver)
            sendToChannel(command, channel, bytes, packetSize, sender)
            return readFromChannel(channel, command, receiver)
        }
    }
}
