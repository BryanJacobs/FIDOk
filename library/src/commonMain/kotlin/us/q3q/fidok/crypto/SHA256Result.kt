package us.q3q.fidok.crypto

/**
 * Represents the result of performing a SHA-256 hash of some data
 *
 * @property hash The raw 32-byte-long hash result
 */
data class SHA256Result(val hash: ByteArray) {
    init {
        require(hash.size == 32)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as SHA256Result

        return hash.contentEquals(other.hash)
    }

    override fun hashCode(): Int {
        return hash.contentHashCode()
    }
}
