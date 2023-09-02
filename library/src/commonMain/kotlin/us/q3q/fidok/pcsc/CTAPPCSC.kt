package us.q3q.fidok.pcsc

import co.touchlab.kermit.Logger

@OptIn(ExperimentalUnsignedTypes::class, ExperimentalStdlibApi::class)
class CTAPPCSC {
    companion object {
        val APPLET_SELECT_BYTES: ByteArray = ubyteArrayOf(
            0x00u, 0xA4u, 0x04u, 0x00u, 0x08u, 0xA0u,
            0x00u, 0x00u, 0x06u, 0x47u, 0x2Fu, 0x00u,
            0x01u, 0x00u,
        ).toByteArray()

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
                        bytes.size.toByte(),
                    ) + bytes.toList() + listOf(
                        0x00.toByte(),
                    )
                    ).toByteArray(),
            )
        }

        fun packetizeMessageChained(bytes: ByteArray): List<ByteArray> {
            val ret = arrayListOf<ByteArray>()
            var sent = 0
            while (sent < bytes.size) {
                val remaining = bytes.size - sent
                val toSend = if (remaining < 255) remaining else 255
                val payloadBytes = bytes.copyOfRange(sent, sent + toSend)
                val last = (sent + toSend) == bytes.size
                val cla = (if (last) 0x80 else 0x90).toByte()
                val chunk = (
                    listOf(
                        cla,
                        0x10.toByte(),
                        0x00.toByte(),
                        0x00.toByte(),
                        payloadBytes.size.toByte(),
                    ) + payloadBytes.toList() + listOf(
                        0x00.toByte(),
                    )
                    ).toByteArray()
                ret.add(chunk)
                sent += toSend
            }
            return ret
        }

        fun sendAndReceive(bytes: ByteArray, selectApplet: Boolean, xmit: (bytes: ByteArray) -> ByteArray): ByteArray {
            if (selectApplet) {
                val selectResponse = xmit(APPLET_SELECT_BYTES)
                Logger.i { "Got applet select response ${selectResponse.toHexString()}" }
            }

            val packets = packetizeMessageChained(bytes)
            if (packets.isEmpty()) {
                return byteArrayOf()
            }
            var resp: ByteArray = byteArrayOf()
            var status = 0x9000
            for (packet in packets) {
                if (status != 0x9000) {
                    throw RuntimeException("Failure response from card: 0x${status.toHexString()}")
                }
                resp = xmit(packet)
                if (resp.size < 2) {
                    throw RuntimeException("Short response from card: <2 bytes")
                }
                status = (resp[resp.size - 2].toUByte().toInt() shl 8) + resp[resp.size - 1].toUByte().toInt()
            }

            var finalResult = resp.copyOfRange(0, resp.size - 2)
            while (status in 0x6101..0x61FF) {
                // val expected = status - 0x6100

                resp = xmit(byteArrayOf(0x00, 0xC0.toByte(), 0x00, 0x00, 0x00))
                status = (resp[resp.size - 2].toUByte().toInt() shl 8) + resp[resp.size - 1].toUByte().toInt()
                if (status != 0x9000 && status !in 0x6101..0x61FF) {
                    throw RuntimeException("Failure response from card: 0x${status.toHexString()}")
                }
                finalResult = (finalResult.toList() + resp.toList().subList(0, resp.size - 2)).toByteArray()
            }
            return finalResult
        }
    }
}
