/**
 * CaBLE - Cloud Assisted Bluetooth Low Energy - is a method of connecting a
 * FIDO Platform with an Authenticator using an Internet-based relay as the
 * primary transport, and Bluetooth as a way to confirm vague physical
 * proximity.
 */

package us.q3q.fidok.cable

import us.q3q.fidok.ctap.FIDOkLibrary
import us.q3q.fidok.ctap.commands.CTAPCBOREncoder
import kotlin.jvm.JvmStatic

/**
 * Code for supporting CaBLE, and hybrid transports generally
 */
class CaBLESupport(val library: FIDOkLibrary) {
    companion object {
        private val assignedTunnelServerDomains = listOf("cable.ua5v.com", "cable.auth.com")

        /**
         * The number of fixed/known tunnel server domains baked into this implementation
         */
        @JvmStatic
        val KNOWN_TUNNEL_SERVER_DOMAINS = assignedTunnelServerDomains.size.toUByte()
        private val tlds = listOf(".com", ".org", ".net", ".info")

        private const val base32Chars = "abcdefghijklmnopqrstuvwxyz234567"
        private const val chunkSize = 7
        private const val chunkDigits = 17
        private const val partialChunkDigits = 0x0fda8530
    }

    private fun bytesToULong(bytes: ByteArray, startOffset: Int = 0): ULong {
        var v: ULong = 0u
        for (i in 0..7) {
            if (i + startOffset < bytes.size) {
                v += ((bytes[i + startOffset].toULong() and 0xFFu) shl (8 * i))
            }
        }
        return v
    }

    /**
     * Determine the domain name to which a particular integer corresponds.
     *
     * Values below 256 are pre-assigned domains; values above 256 are deterministically
     * derived from the number.
     *
     * @param encoded The encoded integer representation of the domain; must be a value between 0 and 65535.
     * @return The domain name to which to connect
     *
     * @throws IllegalArgumentException if the input integer cannot represent a valid domain
     */
    @Throws(IllegalArgumentException::class)
    fun decodeTunnelServerDomain(encoded: Int): String {
        if (encoded < 256) {
            if (encoded >= assignedTunnelServerDomains.size) {
                throw IllegalArgumentException("Unknown tunnel server domain number $encoded")
            }
            return assignedTunnelServerDomains[encoded]
        }

        if (encoded > UShort.MAX_VALUE.toInt()) {
            throw IllegalArgumentException("Encoded value is too large to be a valid tunnel domain!")
        }

        val shaInput = byteArrayOf(
            0x63, 0x61, 0x42, 0x4c, 0x45, 0x76, 0x32, 0x20,
            0x74, 0x75, 0x6e, 0x6e, 0x65, 0x6c, 0x20, 0x73,
            0x65, 0x72, 0x76, 0x65, 0x72, 0x20, 0x64, 0x6f,
            0x6d, 0x61, 0x69, 0x6e,
            (encoded and 0x00FF).toByte(), (encoded shr 8).toByte(), 0x00,
        )
        val digest = library.cryptoProvider.sha256(shaInput).hash

        var v = bytesToULong(digest)
        val tldIndex = (v and 3u).toInt()
        v = v shr 2

        var ret = "cable."
        while (v != 0.toULong()) {
            ret += base32Chars[(v and 31u).toInt()]
            v = v shr 5
        }

        ret += tlds[tldIndex and 3]

        return ret
    }

    private fun digitEncode(d: ByteArray): String {
        var ret = ""
        var offset = 0
        while (offset + chunkSize < d.size) {
            val chunk = ByteArray(chunkSize) { d[offset + it] }
            var vStr = bytesToULong(chunk).toString(10)
            while (vStr.length < chunkDigits) {
                vStr = "0$vStr"
            }
            ret += vStr

            offset += chunkSize
        }

        if (offset < d.size) {
            val remaining = d.size - offset
            val digits = 15 and (partialChunkDigits shr (4 * remaining))
            val chunk = ByteArray(8) { if (offset + it < d.size) { d[offset + it] } else { 0x00 } }
            var vStr = bytesToULong(chunk).toString(10)
            while (vStr.length < digits) {
                vStr = "0$vStr"
            }
            ret += vStr
        }

        return ret
    }

    /**
     * Construct a CaBLE quasi-URL from a CaBLECode instance.
     *
     * @param code A CaBLECode to, well, encode
     * @return Data suitable for embedding into a QR code for display
     */
    fun buildCaBLEURL(code: CaBLECode): String {
        val encoder = CTAPCBOREncoder()
        encoder.encodeSerializableValue(CaBLECode.serializer(), code)
        val cbor = encoder.getBytes()
        return "FIDO:/" + digitEncode(cbor)
    }
}
