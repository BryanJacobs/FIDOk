/**
 * CTAP Authenticators may be connecting to Platforms via Bluetooth Low Energy,
 * with the Authenticator providing a GATT server and the Platform a GATT client.
 * This class implements support for the byte-level encoding of messages for that transport.
 */

package us.q3q.fidok.ble

import co.touchlab.kermit.Logger
import us.q3q.fidok.ctap.DeviceCommunicationException
import us.q3q.fidok.ctap.IncorrectDataException
import kotlin.jvm.JvmStatic
import kotlin.math.min

/**
 * The UUID of the FIDO CTAP2 BLE service
 */
const val FIDO_BLE_SERVICE_UUID = "0000fffd-0000-1000-8000-00805f9b34fb"

/**
 * The UUID of a BLE control point attribute, used for sending commands
 */
const val FIDO_CONTROL_POINT_ATTRIBUTE = "f1d0fff1-deaa-ecee-b42f-c9ba7ed623bb"

/**
 * The UUID of a BLE status attribute, for out of band notifications
 */
const val FIDO_STATUS_ATTRIBUTE = "f1d0fff2-deaa-ecee-b42f-c9ba7ed623bb"

/**
 * The UUID of a BLE attribute defining the write lengths for the control point
 */
const val FIDO_CONTROL_POINT_LENGTH_ATTRIBUTE = "f1d0fff3-deaa-ecee-b42f-c9ba7ed623bb"

/**
 * The UUID of a BLE attribute defining write lengths for the control point
 */
const val FIDO_SERVICE_REVISION_BITFIELD_ATTRIBUTE = "f1d0fff4-deaa-ecee-b42f-c9ba7ed623bb"

/**
 * The UUID of a BLE attribute defining the CTAP service revision supported by an Authenticator
 */
const val FIDO_SERVICE_REVISION_ATTRIBUTE = "00002a28-0000-1000-8000-00805f9b34fb"

/**
 * Bytes representing command types
 */
enum class CTAPBLECommand(val value: UByte) {
    /**
     * An "are you still there" packet
     */
    PING(0x81u),

    /**
     * A delay representing that a request is still being processed
     */
    KEEPALIVE(0x82u),

    /**
     * A real CTAP message - the most common command type
     */
    MSG(0x83u),

    /**
     * A cancellation of an outstanding message still processing
     */
    CANCEL(0xBEu),

    /**
     * An error response from the authenticator to the platform
     */
    ERROR(0xBFu),
}

/**
 * Bytes representing the possible reasons a keepalive was sent
 */
enum class CTAPBLEKeepalivePayload(val value: UByte) {
    /**
     * The request is being processed, but needs more time
     */
    PROCESSING(0x01u),

    /**
     * The request requires user presence and that hasn't yet been obtained.
     *
     * This could take a long time.
     */
    UP_NEEDED(0x02u),
}

/**
 * Code for handling the BLE transport of CTAP, communicating with the authenticators that support it
 */
@OptIn(ExperimentalUnsignedTypes::class, ExperimentalStdlibApi::class)
class CTAPBLE {
    companion object {
        /**
         * Breaks a byte array into a series of packets for sending over BLE.
         *
         * Generally, you should use [sendAndReceive] instead, but this is
         * available in the event you need packetization but not send/recv
         * logic handling.
         *
         * @param command The type of message being sent
         * @param bytes The message body, likely CBOR-encoded CTAP bytes
         * @param packetSize The maximum size of an individual packet. This should
         *                   probably come from reading `fidoControlPointLength` via GATT
         * @return A list of byte arrays, each one of which represents a single packet
         *         to send. If sent in sequence they will deliver the requested message over BLE
         */
        @JvmStatic
        fun packetizeMessage(
            command: CTAPBLECommand,
            bytes: UByteArray,
            packetSize: Int,
        ): List<UByteArray> {
            val bytesForFirstPacket = bytes.copyOfRange(0, min(bytes.size, packetSize - 3))
            val lenHigh = (bytes.size and 0xFF00) shr 8
            val lenLow = bytes.size and 0x00FF
            val pkt = (listOf(command.value, lenHigh.toUByte(), lenLow.toUByte()) + bytesForFirstPacket.toList()).toUByteArray()
            val packets = arrayListOf(pkt)

            var seq: UByte = 0u
            var sent = bytesForFirstPacket.size
            while (sent < bytes.size) {
                val bytesForNextPacket = bytes.copyOfRange(sent, min(bytes.size, sent + packetSize - 1))
                Logger.v { "Continuation packet: ${bytesForNextPacket.size} bytes" }
                val nextPacket = (listOf(seq) + bytesForNextPacket.toList()).toUByteArray()
                packets.add(nextPacket)
                sent += bytesForNextPacket.size
                if (++seq > 0x7Fu) {
                    seq = 0u
                }
            }

            return packets
        }

        /**
         * Send and receive (transceive) bytes to/from a Bluetooth LE authenticator
         *
         * This method will take the incoming command, packetize it, and sequentially invoke
         * the `sender` method with them. Then it will call the `receiver` method, and return
         * its result.
         *
         * @param sender Method that delivers bytes to the device
         * @param receiver Method that returns a response from the device
         * @param command CTAP-BLE command type
         * @param bytes Payload for the CTAP-BLE message: probably CBOR-encoded bytes
         * @param packetSize Maximum size for a single packet, probably from reading `fidoControlPointLength`
         */
        @Throws(DeviceCommunicationException::class)
        @JvmStatic
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
                    throw IncorrectDataException("Initial packet of response has incorrect start: ${packet[0].toHexString()}")
                }
                if (packet[0] == 0xBFu.toUByte()) {
                    throw DeviceCommunicationException("BLE error: ${packet[0].toHexString()}")
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
                    throw IncorrectDataException("Unexpected sequence number: expectd $seq, got ${packet[0]}")
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
