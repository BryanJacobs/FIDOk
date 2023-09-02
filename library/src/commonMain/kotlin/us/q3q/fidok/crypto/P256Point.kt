package us.q3q.fidok.crypto

data class P256Point(val x: ByteArray, val y: ByteArray) {
    init {
        require(x.size == 32)
        require(y.size == 32)
    }
}
