package us.q3q.fidok.crypto

/**
 * Represents an AES-128 or AES-256 encryption/decryption key
 *
 * @property key Raw key data - 16 or 32 bytes
 * @property iv An optional 16-byte Initialization Vector for cryptographic operations
 */
data class AESKey(val key: ByteArray, val iv: ByteArray? = null) {
    init {
        require(key.size == 16 || key.size == 32)
        require(iv == null || iv.size == 16)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as AESKey

        if (!key.contentEquals(other.key)) return false
        if (iv != null) {
            if (other.iv == null) return false
            if (!iv.contentEquals(other.iv)) return false
        } else if (other.iv != null) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = key.contentHashCode()
        result = 31 * result + (iv?.contentHashCode() ?: 0)
        return result
    }
}
