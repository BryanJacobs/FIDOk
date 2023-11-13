@file:Suppress("unused", "FunctionName", "LocalVariableName")
@file:OptIn(ExperimentalNativeApi::class)

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.asStableRef
import kotlinx.coroutines.runBlocking
import us.q3q.fidok.ctap.FIDOkLibrary
import us.q3q.fidok.ez.EZHmac
import us.q3q.fidok.inAsByteArray
import us.q3q.fidok.outFill
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalForeignApi::class)
@CName("fidok_ez_hmac_setup")
fun ez_hmac_setup(
    library: COpaquePointer,
    out: COpaquePointer,
): Int {
    val fidok = library.asStableRef<FIDOkLibrary>().get()

    val setup =
        runBlocking {
            EZHmac(fidok).setup()
        }
    outFill(setup, out)
    return setup.size
}

@OptIn(ExperimentalForeignApi::class)
internal fun ez_hmac_underlying(
    library: COpaquePointer,
    setup: COpaquePointer,
    setup_len: Int,
    data: COpaquePointer,
    data_len: Int,
    salt: COpaquePointer,
    out: COpaquePointer,
    cbFunc: (EZHmac, ByteArray, ByteArray, ByteArray) -> ByteArray,
): Int {
    val fidok = library.asStableRef<FIDOkLibrary>().get()

    val setupB = inAsByteArray(setup, setup_len)
    val inB = inAsByteArray(data, data_len)
    val saltB = inAsByteArray(salt, 32)

    val gotten =
        cbFunc(
            EZHmac(fidok),
            setupB,
            inB,
            saltB,
        )
    outFill(gotten, out)
    return gotten.size
}

@OptIn(ExperimentalForeignApi::class)
@CName("fidok_ez_hmac_encrypt")
fun ez_hmac_encrypt(
    library: COpaquePointer,
    setup: COpaquePointer,
    setup_len: Int,
    data: COpaquePointer,
    data_len: Int,
    salt: COpaquePointer,
    out: COpaquePointer,
): Int {
    return ez_hmac_underlying(library, setup, setup_len, data, data_len, salt, out) { hmac, setupB, dataB, saltB ->
        runBlocking {
            hmac.encrypt(setupB, dataB, saltB)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
@CName("fidok_ez_hmac_decrypt")
fun ez_hmac_decrypt(
    library: COpaquePointer,
    setup: COpaquePointer,
    setup_len: Int,
    data: COpaquePointer,
    data_len: Int,
    salt: COpaquePointer,
    out: COpaquePointer,
): Int {
    return ez_hmac_underlying(library, setup, setup_len, data, data_len, salt, out) { hmac, setupB, dataB, saltB ->
        runBlocking {
            hmac.decrypt(setupB, dataB, saltB)
        }
    }
}

@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
@CName("fidok_ez_hmac_rotate")
fun ez_hmac_rotate(
    library: COpaquePointer,
    setup: COpaquePointer,
    setup_len: Int,
    data: COpaquePointer,
    data_len: Int,
    salt1: COpaquePointer,
    salt2: COpaquePointer,
    out1: COpaquePointer,
    out2: COpaquePointer,
): Int {
    val fidok = library.asStableRef<FIDOkLibrary>().get()

    val setupB = inAsByteArray(setup, setup_len)
    val inB = inAsByteArray(data, data_len)
    val salt1B = inAsByteArray(salt1, 32)
    val salt2B = inAsByteArray(salt2, 32)

    val values =
        runBlocking {
            EZHmac(fidok).encryptAndRotate(setupB, inB, salt1B, salt2B)
        }
    outFill(values.first, out1)
    outFill(values.second, out2)

    assert(values.first.size == values.second.size)

    return values.first.size
}
