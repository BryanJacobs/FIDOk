package us.q3q.fidok.crypto

data class SHA256Result(val hash: ByteArray) {
    init {
        require(hash.size == 32)
    }
}
