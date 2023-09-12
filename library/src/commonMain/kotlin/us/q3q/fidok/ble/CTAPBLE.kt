
package us.q3q.fidok.ble

import co.touchlab.kermit.Logger
import kotlin.math.min

enum class CTAPBLECommand(val value: UByte) {
    PING(0x81u),
    KEEPALIVE(0x82u),
    MSG(0x83u),
    CANCEL(0xBEu),
    ERROR(0xBFu),
}

enum class CTAPBLEKeepalivePayload(val value: UByte) {
    PROCESSING(0x01u),
    UP_NEEDED(0x02u),
}

@OptIn(ExperimentalUnsignedTypes::class, ExperimentalStdlibApi::class)
class CTAPBLE {
    companion object {
        fun packetizeMessage(command: CTAPBLECommand, bytes: UByteArray, packetSize: Int): List<UByteArray> {
            val bytesForFirstPacket = bytes.copyOfRange(0, min(bytes.size, packetSize - 3))
            val lenHigh = (bytes.size and 0xFF00) shr 8
            val lenLow = bytes.size and 0x00FF
            val pkt = (listOf(command.value, lenHigh.toUByte(), lenLow.toUByte()) + bytesForFirstPacket.toList()).toUByteArray()
            val packets = arrayListOf(pkt)

            var seq: UByte = 0u
            var sent = bytesForFirstPacket.size
            while (sent < bytes.size) {
                val bytesForNextPacket = bytes.copyOfRange(sent, min(bytes.size, sent + packetSize - 1))
                Logger.v("Continuation packet: ${bytesForNextPacket.size} bytes")
                val nextPacket = (listOf(seq) + bytesForNextPacket.toList()).toUByteArray()
                packets.add(nextPacket)
                sent += bytesForNextPacket.size
                if (++seq > 0x7Fu) {
                    seq = 0u
                }
            }

            return packets
        }

        fun sendAndReceive(
            sender: (bytes: UByteArray) -> Unit,
            receiver: () -> UByteArray,
            command: CTAPBLECommand,
            bytes: UByteArray,
            packetSize: Int,
        ): UByteArray {
            val packets = packetizeMessage(command, bytes, packetSize)
            for (packet in packets) {
                sender(packet)
            }

            var packet = receiver()

            while (true) {
                if (packet[0] and 0x80u.toUByte() != 0x80u.toUByte()) {
                    throw IllegalStateException("Initial packet of response has incorrect start: ${packet[0].toHexString()}")
                }
                if (packet[0] == 0xBFu.toUByte()) {
                    throw IllegalStateException("BLE error: ${packet[0].toHexString()}")
                }
                if (packet[0] != 0x82u.toUByte()) {
                    break
                }
                packet = receiver()
            }

            val responseDeclaredLen = packet[1] * 256u + packet[2]
            var response = packet.toList().subList(3, packet.size)

            var seq: UByte = 0u
            while (response.size < responseDeclaredLen.toInt()) {
                packet = receiver()
                Logger.v { "Read packet sequence ${packet[0]}" }
                if (packet[0] != seq) {
                    throw IllegalStateException("Unexpected sequence number: expectd $seq, got ${packet[0]}")
                }
                response += packet.toList().subList(1, packet.size)
                if (++seq > 0x7Fu) {
                    seq = 0u
                }
            }

            return response.toUByteArray()
        }
    }
}
