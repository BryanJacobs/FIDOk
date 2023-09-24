package us.q3q.fidok.crypto

/**
 * Represents an elliptic curve point on the NIST P-256 curve.
 *
 * @property x The point's X-value, as 32 bytes
 * @property y The point's Y-value, as 32 bytes
 */
data class P256Point(val x: ByteArray, val y: ByteArray) {
    init {
        require(x.size == 32)
        require(y.size == 32)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as P256Point

        if (!x.contentEquals(other.x)) return false
        if (!y.contentEquals(other.y)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = x.contentHashCode()
        result = 31 * result + y.contentHashCode()
        return result
    }
}
