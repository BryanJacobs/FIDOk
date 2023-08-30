package us.q3q.fidok

import botan.BOTAN_FFI_SUCCESS
import botan.botan_cipher_destroy
import botan.botan_cipher_init
import botan.botan_cipher_output_length
import botan.botan_cipher_set_key
import botan.botan_cipher_start
import botan.botan_cipher_tVar
import botan.botan_cipher_update
import botan.botan_error_description
import botan.botan_hash_destroy
import botan.botan_hash_final
import botan.botan_hash_init
import botan.botan_hash_output_length
import botan.botan_hash_tVar
import botan.botan_hash_update
import botan.botan_kdf
import botan.botan_mac_destroy
import botan.botan_mac_final
import botan.botan_mac_init
import botan.botan_mac_output_length
import botan.botan_mac_set_key
import botan.botan_mac_tVar
import botan.botan_mac_update
import botan.botan_mp_destroy
import botan.botan_mp_init
import botan.botan_mp_tVar
import botan.botan_mp_to_bin
import botan.botan_pk_op_ka_tVar
import botan.botan_pk_op_key_agreement
import botan.botan_pk_op_key_agreement_create
import botan.botan_pk_op_key_agreement_destroy
import botan.botan_pk_op_key_agreement_size
import botan.botan_privkey_create
import botan.botan_privkey_destroy
import botan.botan_privkey_export
import botan.botan_privkey_get_field
import botan.botan_privkey_load
import botan.botan_privkey_tVar
import botan.botan_rng_destroy
import botan.botan_rng_get
import botan.botan_rng_init
import botan.botan_rng_t
import botan.botan_rng_tVar
import co.touchlab.kermit.Logger
import kotlinx.cinterop.CArrayPointer
import kotlinx.cinterop.CVariable
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.posix.size_tVar
import platform.posix.uint32_t
import platform.posix.uint8_tVar
import us.q3q.fidok.crypto.AES256Key
import us.q3q.fidok.crypto.CryptoProvider
import us.q3q.fidok.crypto.KeyAgreementResult
import us.q3q.fidok.crypto.KeyAgreementState
import us.q3q.fidok.crypto.P256Point
import us.q3q.fidok.crypto.SHA256Result

@OptIn(ExperimentalForeignApi::class)
class LinuxCrypto : CryptoProvider {

    private inline fun <reified T : CVariable, R> withBotanAlloc(destroy: (v: T) -> Unit, act: (v: T) -> R): R {
        memScoped {
            val ptr = this.alloc<T>()
            try {
                return act(ptr)
            } finally {
                destroy(ptr)
            }
        }
    }

    override fun ecdhKeyAgreementInit(otherPublicKeyPoint: P256Point): KeyAgreementState {
        Logger.i { "Creating ECDH key" }

        return withRNG { rng ->
            withBotanAlloc<botan_privkey_tVar, KeyAgreementState>({ botan_privkey_destroy(it.value) }) { privateKey ->
                botanSuccessCheck {
                    botan_privkey_create(privateKey.ptr, "ECDH", "secp256r1", rng)
                }

                val points = listOf("public_x", "public_y").map { fieldName ->
                    withBotanAlloc<botan_mp_tVar, ByteArray>({ botan_mp_destroy(it.value) }) inner@{ mp ->
                        botanSuccessCheck {
                            botan_mp_init(mp.ptr)
                        }
                        botanSuccessCheck {
                            botan_privkey_get_field(mp.value, privateKey.value, fieldName)
                        }
                        return@inner withOutBuffer(32) {
                            botanSuccessCheck {
                                botan_mp_to_bin(mp.value, it)
                            }
                        }
                    }
                }

                val exportBufferLen = 500
                val trueExportedKey = memScoped {
                    val exportBufferTrueLen = this.alloc<size_tVar>()
                    exportBufferTrueLen.value = exportBufferLen.convert()
                    val exportedKey = withOutBuffer(exportBufferLen) { keyOut ->
                        botanSuccessCheck {
                            botan_privkey_export(privateKey.value, keyOut, exportBufferTrueLen.ptr, 0u)
                        }
                    }
                    exportedKey.copyOfRange(0, exportBufferTrueLen.value.toInt())
                }

                KeyAgreementState(points[0], points[1], PrivateKeyHolder(trueExportedKey))
            }
        }
    }

    data class PrivateKeyHolder(val v: ByteArray)

    override fun ecdhKeyAgreementKDF(
        state: KeyAgreementState,
        otherPublicKeyPoint: P256Point,
        useHKDF: Boolean,
        salt: ByteArray,
        info: ByteArray,
    ): KeyAgreementResult {
        val privKeyBytes = (state.opaqueState as PrivateKeyHolder).v
        return withBotanAlloc<botan_privkey_tVar, KeyAgreementResult>({ botan_privkey_destroy(it.value) }) { privKey ->
            withInBuffer(privKeyBytes) { privKeyCBytes ->
                botanSuccessCheck {
                    botan_privkey_load(privKey.ptr, null, privKeyCBytes, privKeyBytes.size.convert(), null)
                }
            }

            withBotanAlloc<botan_pk_op_ka_tVar, KeyAgreementResult>({ botan_pk_op_key_agreement_destroy(it.value) }) { ka ->
                botanSuccessCheck {
                    botan_pk_op_key_agreement_create(ka.ptr, privKey.value, "Raw", 0u)
                }

                memScoped {
                    val pkBuf = this.allocArray<UByteVar>(65)
                    pkBuf[0] = 0x04u // uncompressed point
                    for (i in otherPublicKeyPoint.x.indices) {
                        pkBuf[i + 1] = otherPublicKeyPoint.x[i].toUByte()
                    }
                    for (i in otherPublicKeyPoint.y.indices) {
                        pkBuf[i + 33] = otherPublicKeyPoint.y[i].toUByte()
                    }

                    val outLen = this.alloc<size_tVar>()
                    botanSuccessCheck {
                        botan_pk_op_key_agreement_size(ka.value, outLen.ptr)
                    }

                    val rawKey = withOutBuffer(outLen.value.toInt()) { out ->
                        botanSuccessCheck {
                            botan_pk_op_key_agreement(
                                ka.value,
                                out,
                                outLen.ptr,
                                pkBuf,
                                65u,
                                null,
                                0u,
                            )
                        }
                    }

                    if (useHKDF) {
                        withInBuffer(rawKey) { rawKeyC ->
                            withInBuffer(info) { infoC ->
                                withInBuffer(salt) { saltC ->
                                    val res = withOutBuffer(32) { out ->
                                        botan_kdf(
                                            "HKDF(SHA-256)", out, 32u,
                                            rawKeyC, rawKey.size.convert(),
                                            saltC, salt.size.convert(),
                                            infoC, info.size.convert(),
                                        )
                                    }
                                    KeyAgreementResult(res)
                                }
                            }
                        }
                    } else {
                        // Without HKDF, just hash the result
                        KeyAgreementResult(sha256(rawKey).hash)
                    }
                }
            }
        }
    }

    override fun ecdhKeyAgreementDestroy(state: KeyAgreementState) {
        // Nothing to do here
    }

    override fun sha256(data: ByteArray): SHA256Result {
        val ret = withBotanAlloc<botan_hash_tVar, ByteArray>({ botan_hash_destroy(it.value) }) { hash ->
            botanSuccessCheck {
                botan_hash_init(hash.ptr, "SHA-256", 0u)
            }
            memScoped {
                val outputLength = this.alloc<size_tVar>()
                botanSuccessCheck {
                    botan_hash_output_length(hash.value, outputLength.ptr)
                }

                val inBuf = this.allocArray<UByteVar>(data.size)
                for (i in data.indices) {
                    inBuf[i] = data[i].toUByte()
                }
                botanSuccessCheck {
                    botan_hash_update(hash.value, inBuf, data.size.convert())
                }
                withOutBuffer(outputLength.value.convert()) {
                    botanSuccessCheck {
                        botan_hash_final(hash.value, it)
                    }
                }
            }
        }
        return SHA256Result(ret)
    }

    private fun botanSuccessCheck(f: () -> Int) {
        val result = f()
        if (result != BOTAN_FFI_SUCCESS) {
            val desc = botan_error_description(result)
            Logger.e { "Crypto error: ${desc?.toKString()}" }
            throw RuntimeException("Botan crypto failure: ${desc?.toKString()}")
        }
    }

    private fun withOutBuffer(numBytes: Int, f: (out: CArrayPointer<UByteVar>) -> Unit): ByteArray {
        memScoped {
            val out = this.allocArray<UByteVar>(numBytes)
            f(out)
            val ret = arrayListOf<Byte>()
            for (i in 0..<numBytes) {
                ret.add(out[i].toByte())
            }
            return ret.toByteArray()
        }
    }

    private fun <R> withRNG(f: (rng: botan_rng_t) -> R): R {
        return withBotanAlloc<botan_rng_tVar, R>({ botan_rng_destroy(it.value) }) { rng ->
            botanSuccessCheck {
                botan_rng_init(rng.ptr, null)
            }
            f(rng.value!!)
        }
    }

    override fun secureRandom(numBytes: Int): ByteArray {
        return withRNG { rng ->
            withOutBuffer(numBytes) {
                botanSuccessCheck {
                    botan_rng_get(rng, it, numBytes.convert())
                }
            }
        }
    }

    private fun aes256(bytes: ByteArray, key: AES256Key, flags: uint32_t): ByteArray {
        return withBotanAlloc<botan_cipher_tVar, ByteArray>({ botan_cipher_destroy(it.value) }) { bc ->
            botanSuccessCheck {
                botan_cipher_init(bc.ptr, "AES-256/CBC", flags)
            }
            withInBuffer(key.key) {
                botanSuccessCheck {
                    botan_cipher_set_key(bc.value, it, key.key.size.convert())
                }
            }
            if (key.iv == null) {
                throw IllegalArgumentException("IV required for AES-256 operations")
            }
            withInBuffer(key.iv) {
                botanSuccessCheck {
                    botan_cipher_start(bc.value, it, key.iv.size.convert())
                }
            }

            memScoped {
                val outputLenBuffer = this.alloc<size_tVar>()
                botanSuccessCheck {
                    botan_cipher_output_length(bc.value, bytes.size.convert(), outputLenBuffer.ptr)
                }
                val outputLength = outputLenBuffer.value.toInt()
                val inputLenBuffer = this.alloc<size_tVar>()
                inputLenBuffer.value = 0u
                outputLenBuffer.value = 0u

                val ret = withInBuffer(bytes) { input ->
                    withOutBuffer(outputLength) { output ->
                        botanSuccessCheck {
                            botan_cipher_update(
                                bc.value,
                                0u,
                                output,
                                outputLength.convert(),
                                outputLenBuffer.ptr,
                                input,
                                bytes.size.convert(),
                                inputLenBuffer.ptr,
                            )
                        }
                    }
                }

                Logger.v { "Stream cipher completed ${outputLenBuffer.value} bytes" }

                ret
            }
        }
    }

    override fun aes256CBCEncrypt(bytes: ByteArray, key: AES256Key): ByteArray {
        return aes256(bytes, key, 0u)
    }

    override fun aes256CBCDecrypt(bytes: ByteArray, key: AES256Key): ByteArray {
        return aes256(bytes, key, 1u)
    }

    private fun <R> withInBuffer(bytes: ByteArray, f: (array: CArrayPointer<uint8_tVar>) -> R): R {
        return memScoped {
            val arr = this.allocArray<uint8_tVar>(bytes.size)
            for (i in bytes.indices) {
                arr[i] = bytes[i].convert()
            }
            f(arr)
        }
    }

    override fun hmacSHA256(bytes: ByteArray, key: AES256Key): SHA256Result {
        return withBotanAlloc<botan_mac_tVar, SHA256Result>({ botan_mac_destroy(it.value) }) { mac ->
            botanSuccessCheck {
                botan_mac_init(mac.ptr, "HMAC(SHA-256)", 0u)
            }

            withInBuffer(key.key) {
                botanSuccessCheck {
                    botan_mac_set_key(mac.value, it, key.key.size.convert())
                }
            }

            withInBuffer(bytes) {
                botanSuccessCheck {
                    botan_mac_update(mac.value, it, bytes.size.convert())
                }
            }

            memScoped {
                val outputLength = this.alloc<size_tVar>()
                botanSuccessCheck {
                    botan_mac_output_length(mac.value, outputLength.ptr)
                }

                val ret = withOutBuffer(outputLength.value.toInt()) {
                    botan_mac_final(mac.value, it)
                }

                SHA256Result(ret)
            }
        }
    }
}
