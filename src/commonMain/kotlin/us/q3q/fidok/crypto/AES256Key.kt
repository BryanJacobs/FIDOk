package us.q3q.fidok.crypto

data class AES256Key(val key: ByteArray, val iv: ByteArray? = null) {
    init {
        require(key.size == 32)
        require(iv == null || iv.size == 16)
    }
}
