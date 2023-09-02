package us.q3q.fidok.crypto

import us.q3q.fidok.ctap.commands.COSEKey

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

    fun getCOSE(): COSEKey {
        return COSEKey(
            kty = 2,
            alg = -25,
            crv = 1,
            x = publicX,
            y = publicY,
        )
    }
}
