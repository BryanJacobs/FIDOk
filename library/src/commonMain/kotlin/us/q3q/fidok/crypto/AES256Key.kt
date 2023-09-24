package us.q3q.fidok.crypto

/**
 * Represents an AES-256 encryption/decryption key
 *
 * @property key Raw key data - 32 bytes
 * @property iv An optional 16-byte Initialization Vector for cryptographic operations
 */
data class AES256Key(val key: ByteArray, val iv: ByteArray? = null) {
    init {
        require(key.size == 32)
        require(iv == null || iv.size == 16)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as AES256Key

        if (!key.contentEquals(other.key)) return false
        if (iv != null) {
            if (other.iv == null) return false
            if (!iv.contentEquals(other.iv)) return false
        } else if (other.iv != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = key.contentHashCode()
        result = 31 * result + (iv?.contentHashCode() ?: 0)
        return result
    }
}
