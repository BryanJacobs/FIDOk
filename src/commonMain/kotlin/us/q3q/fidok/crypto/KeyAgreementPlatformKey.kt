package us.q3q.fidok.crypto

data class KeyAgreementPlatformKey(
    val publicX: ByteArray,
    val publicY: ByteArray,
    val pinProtocol1Key: ByteArray,
    val pinProtocol2HMACKey: ByteArray,
    val pinProtocol2AESKey: ByteArray,
) {
    init {
        require(publicX.size == 32)
        require(publicY.size == 32)
        require(pinProtocol1Key.size == 32)
        require(pinProtocol2HMACKey.size == 32)
        require(pinProtocol2AESKey.size == 32)
    }
}
