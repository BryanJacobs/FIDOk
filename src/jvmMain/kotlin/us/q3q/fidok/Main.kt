package us.q3q.fidok

import co.touchlab.kermit.Logger
import jnr.ffi.LibraryLoader
import jnr.ffi.LibraryOption
import jnr.ffi.annotations.In
import us.q3q.fidok.crypto.AES256Key
import us.q3q.fidok.crypto.CryptoProvider
import us.q3q.fidok.crypto.KeyAgreementResult
import us.q3q.fidok.crypto.KeyAgreementState
import us.q3q.fidok.crypto.P256Point
import us.q3q.fidok.crypto.SHA256Result
import us.q3q.fidok.ctap.CTAPClient
import us.q3q.fidok.ctap.Device
import us.q3q.fidok.ctap.Library
import java.nio.ByteBuffer

@Suppress("FunctionName")
interface KotlinNative {
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

    fun fidok_count_devices(): Int
    fun fidok_send_bytes(
        deviceNumber: Int,
        @In input: ByteBuffer,
        inputLen: Int,
        output: ByteBuffer,
        outputLen: ByteBuffer,
    ): Int
}

open class NativeLibraryUser(libraryPath: String) {
    protected val native: KotlinNative

    init {
        native = LibraryLoader.loadLibrary(
            KotlinNative::class.java,
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

class NativeBackedCryptoProvider(libraryPath: String) : NativeLibraryUser(libraryPath), CryptoProvider {

    override fun ecdhKeyAgreementInit(otherPublicKeyPoint: P256Point): KeyAgreementState {
        val outX = ByteBuffer.allocateDirect(32)
        val outY = ByteBuffer.allocateDirect(32)
        val ret = native.fidok_crypto_ecdh_init(
            toBB(otherPublicKeyPoint.x),
            toBB(otherPublicKeyPoint.y),
            outX,
            outY,
        )
        return KeyAgreementState(toBA(outX), toBA(outY), ret)
    }

    override fun ecdhKeyAgreementKDF(
        state: KeyAgreementState,
        otherPublicKeyPoint: P256Point,
        useHKDF: Boolean,
        salt: ByteArray,
        info: ByteArray,
    ): KeyAgreementResult {
        val ret = ByteBuffer.allocateDirect(32)
        native.fidok_crypto_ecdh_kdf(
            state.opaqueState as Long, toBB(state.localPublicX),
            toBB(state.localPublicY), useHKDF, toBB(salt), salt.size,
            toBB(info), info.size, ret,
        )
        return KeyAgreementResult(toBA(ret))
    }

    override fun ecdhKeyAgreementDestroy(state: KeyAgreementState) {
        native.fidok_crypto_ecdh_destroy(state.opaqueState as Long)
    }

    override fun sha256(data: ByteArray): SHA256Result {
        val ret = ByteBuffer.allocateDirect(32)
        native.fidok_crypto_sha256(toBB(data), data.size, ret)
        return SHA256Result(toBA(ret))
    }

    override fun secureRandom(numBytes: Int): ByteArray {
        val buffer = ByteBuffer.allocateDirect(numBytes)
        native.fidok_crypto_secure_random(numBytes, buffer)
        return toBA(buffer)
    }

    override fun aes256CBCEncrypt(bytes: ByteArray, key: AES256Key): ByteArray {
        if (key.iv == null) {
            throw IllegalArgumentException("AES encryption requires an IV")
        }
        val ret = ByteBuffer.allocateDirect(bytes.size)
        native.fidok_crypto_aes_256_cbc_encrypt(
            toBB(bytes),
            bytes.size,
            toBB(key.key),
            toBB(key.iv),
            ret,
        )
        return toBA(ret)
    }

    override fun aes256CBCDecrypt(bytes: ByteArray, key: AES256Key): ByteArray {
        if (key.iv == null) {
            throw IllegalArgumentException("AES decryption requires an IV")
        }
        val ret = ByteBuffer.allocateDirect(bytes.size)
        native.fidok_crypto_aes_256_cbc_decrypt(
            toBB(bytes),
            bytes.size,
            toBB(key.key),
            toBB(key.iv),
            ret,
        )
        return toBA(ret)
    }

    override fun hmacSHA256(bytes: ByteArray, key: AES256Key): SHA256Result {
        val ret = ByteBuffer.allocateDirect(32)
        native.fidok_crypto_hmac_sha256(
            toBB(bytes),
            bytes.size,
            toBB(key.key),
            ret,
        )
        return SHA256Result(toBA(ret))
    }
}

class NativeBackedDevice(libraryPath: String, private val deviceNumber: Int) : NativeLibraryUser(libraryPath), Device {
    override fun sendBytes(bytes: ByteArray): ByteArray {
        val capacity = 32768
        val output = ByteBuffer.allocateDirect(capacity)
        val capacityBacking = ByteBuffer.allocateDirect(4)
        capacityBacking.putInt(capacity)
        capacityBacking.rewind()

        val res = native.fidok_send_bytes(
            deviceNumber,
            toBB(bytes),
            bytes.size,
            output,
            capacityBacking,
        )
        if (res != 0) {
            throw RuntimeException("Native device communication failed")
        }

        capacityBacking.rewind()

        val responseSize = capacityBacking.getInt(0)

        Logger.d { "Received $responseSize response bytes from native" }

        return toBA(output, responseSize)
    }
}

class NativeDeviceListing(libraryPath: String) : NativeLibraryUser(libraryPath) {
    fun list(): Int {
        return native.fidok_count_devices()
    }
}

fun main() {
    val libraryPath = "build/bin/native/debugShared/libfidok.so"

    Library.init(NativeBackedCryptoProvider(libraryPath))

    val numDevices = NativeDeviceListing(libraryPath).list()
    if (numDevices < 1) {
        throw RuntimeException("No native devices found")
    }

    val device = NativeBackedDevice(libraryPath, 0)
    val client = CTAPClient(device)

    println(client.getInfo())
}
