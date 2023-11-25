package us.q3q.fidok.pcsc

import co.touchlab.kermit.Logger
import us.q3q.fidok.ctap.DeviceCommunicationException
import us.q3q.fidok.ctap.IncorrectDataException
import us.q3q.fidok.ctap.OutOfBandErrorResponseException

/**
 * Support code for communicating with Authenticators over PC/SC - the Smartcard protocol
 */
@OptIn(ExperimentalUnsignedTypes::class, ExperimentalStdlibApi::class)
class CTAPPCSC {
    companion object {
        /**
         * Sequence of bytes to select a CTAP1/U2F or CTAP2 applet
         */
        val APPLET_SELECT_BYTES: ByteArray =
            ubyteArrayOf(
                0x00u, 0xA4u, 0x04u, 0x00u, 0x08u, 0xA0u,
                0x00u, 0x00u, 0x06u, 0x47u, 0x2Fu, 0x00u,
                0x01u, 0x00u,
            ).toByteArray()

        /**
         * Break a message into chunks suitable for delivery as Extended APDUs.
         *
         * Not all readers support E-APDUs, but they are more efficient when they can be used
         * Each returned packet will have a preamble suitable for CTAP2 messaging
         *
         * @param bytes Bytes to send
         * @return List of E-APDU packets
         */
        fun packetizeMessageExtended(bytes: ByteArray): List<ByteArray> {
            if (bytes.size > 65535) {
                throw IllegalArgumentException("PC/SC transmit value longer than E-APDU can hold!")
            }
            return listOf(
                (
                    listOf(
                        0x80.toByte(),
                        0x10.toByte(),
                        0x00.toByte(),
                        0x00.toByte(),
                        ((bytes.size and 0x00FF0000) shr 16).toByte(),
                        ((bytes.size and 0x0000FF00) shr 8).toByte(),
                        (bytes.size and 0x000000FF).toByte(),
                    ) + bytes.toList() +
                        listOf(
                            0x00.toByte(),
                            0x00.toByte(),
                        )
                ).toByteArray(),
            )
        }

        /**
         * Break a message into chunks suitable for delivery as Chained APDUs.
         *
         * Each returned packet will have a preamble suitable for CTAP2 messaging.
         *
         * @param bytes Bytes to send
         * @return List of packets, each one of which can be sent as a single APDU
         */
        fun packetizeMessageChained(bytes: ByteArray): List<ByteArray> {
            val ret = arrayListOf<ByteArray>()
            var sent = 0
            while (sent < bytes.size) {
                val remaining = bytes.size - sent
                val toSend = if (remaining < 254) remaining else 254
                val payloadBytes = bytes.copyOfRange(sent, sent + toSend)
                val last = (sent + toSend) == bytes.size
                val cla = (if (last) 0x80 else 0x90).toByte()
                val chunk =
                    (
                        listOf(
                            cla,
                            0x10.toByte(),
                            0x00.toByte(),
                            0x00.toByte(),
                            payloadBytes.size.toByte(),
                        ) + payloadBytes.toList() +
                            listOf(
                                0x00.toByte(),
                            )
                    ).toByteArray()
                ret.add(chunk)
                sent += toSend
            }
            return ret
        }

        /**
         * Communicate with a PC/SC Authenticator.
         *
         * Sends a single logical CTAP2 request, and receives its response from the Authenticator.
         *
         * @param bytes Encoded bytes representing one message
         * @param selectApplet If true, send an applet selection command prior to sending the message
         * @param useExtendedMessages If true, use E-APDUs (see [packetizeMessageExtended])
         * @param xmit Function that delivers bytes to the Authenticator and receives its response back
         * @return Response payload from the Authenticator
         * @throws DeviceCommunicationException When the card returns a non-successful status (note: CTAP errors
         * are successes)
         */
        @Throws(DeviceCommunicationException::class)
        fun sendAndReceive(
            bytes: ByteArray,
            selectApplet: Boolean,
            useExtendedMessages: Boolean,
            xmit: (bytes: ByteArray) -> ByteArray,
        ): ByteArray {
            if (selectApplet) {
                val selectResponse = xmit(APPLET_SELECT_BYTES)
                Logger.i { "Got applet select response ${selectResponse.toHexString()}" }
            }

            val packets = if (useExtendedMessages) packetizeMessageExtended(bytes) else packetizeMessageChained(bytes)
            if (packets.isEmpty()) {
                return byteArrayOf()
            }
            var resp: ByteArray = byteArrayOf()
            var status = 0x9000
            for (packet in packets) {
                if (status != 0x9000) {
                    throw OutOfBandErrorResponseException("Failure response from card in accepting input", code = status)
                }
                resp = xmit(packet)
                if (resp.size < 2) {
                    throw IncorrectDataException("Short response from card: <2 bytes")
                }
                status = (resp[resp.size - 2].toUByte().toInt() shl 8) + resp[resp.size - 1].toUByte().toInt()
                Logger.v { "Raw status: ${status.toHexString()}" }
            }

            var finalResult = resp.copyOfRange(0, resp.size - 2)
            while (status in 0x6100..0x61FF) {
                // val expected = status - 0x6100

                resp = xmit(byteArrayOf(0x00, 0xC0.toByte(), 0x00, 0x00, 0x00))
                status = (resp[resp.size - 2].toUByte().toInt() shl 8) + resp[resp.size - 1].toUByte().toInt()
                Logger.v { "Raw status: ${status.toHexString()}" }
                if (status != 0x9000 && status !in 0x6100..0x61FF) {
                    throw OutOfBandErrorResponseException("Failure response from card in returning output", code = status)
                }
                finalResult = (finalResult.toList() + resp.toList().subList(0, resp.size - 2)).toByteArray()
            }
            return finalResult
        }
    }
}
