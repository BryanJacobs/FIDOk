package us.q3q.fidok

import jnr.ffi.LibraryLoader
import jnr.ffi.LibraryOption
import jnr.ffi.annotations.In
import java.nio.ByteBuffer

open class NativeLibraryUser(libraryPath: String) {
    protected val native: FIDOkNative

    init {
        native = LibraryLoader.loadLibrary(
            FIDOkNative::class.java,
            mapOf(
                LibraryOption.LoadNow to true,
            ),
            libraryPath,
        )
    }

    protected fun toBB(data: ByteArray): ByteBuffer {
        val ret = ByteBuffer.allocateDirect(data.size)
        ret.put(data)
        ret.rewind()
        return ret
    }

    protected fun toBA(data: ByteBuffer, len: Int? = null): ByteArray {
        return ByteArray(len ?: data.capacity()) {
            data[it]
        }
    }
}

@Suppress("FunctionName")
interface FIDOkNative {
    fun fidok_crypto_ecdh_init(
        @In x: ByteBuffer,
        @In y: ByteBuffer,
        outX: ByteBuffer,
        outY: ByteBuffer,
    ): Long
    fun fidok_crypto_ecdh_kdf(
        state: Long,
        @In x: ByteBuffer,
        @In y: ByteBuffer,
        useHKDF: Boolean,
        @In salt: ByteBuffer,
        saltLen: Int,
        @In info: ByteBuffer,
        infoLen: Int,
        out: ByteBuffer,
    ): Int

    fun fidok_crypto_ecdh_destroy(state: Long)
    fun fidok_crypto_secure_random(numBytes: Int, out: ByteBuffer)
    fun fidok_crypto_sha256(@In data: ByteBuffer, len: Int, out: ByteBuffer)
    fun fidok_crypto_aes_256_cbc_encrypt(
        @In data: ByteBuffer,
        len: Int,
        @In key: ByteBuffer,
        @In iv: ByteBuffer,
        out: ByteBuffer,
    )
    fun fidok_crypto_aes_256_cbc_decrypt(
        @In data: ByteBuffer,
        len: Int,
        @In key: ByteBuffer,
        @In iv: ByteBuffer,
        out: ByteBuffer,
    )
    fun fidok_crypto_hmac_sha256(
        @In data: ByteBuffer,
        len: Int,
        @In key: ByteBuffer,
        out: ByteBuffer,
    )

    fun fidok_crypto_es256_signature_validate(
        @In signedBytes: ByteBuffer,
        signedBytesLen: Int,
        @In keyX: ByteBuffer,
        @In keyY: ByteBuffer,
        @In signature: ByteBuffer,
        signatureLen: Int,
    ): Boolean

    fun fidok_count_devices(): Int
    fun fidok_send_bytes(
        deviceNumber: Int,
        @In input: ByteBuffer,
        inputLen: Int,
        output: ByteBuffer,
        outputLen: ByteBuffer,
    ): Int
}
