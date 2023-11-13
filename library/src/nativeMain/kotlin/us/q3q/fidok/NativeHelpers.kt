package us.q3q.fidok

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CArrayPointer
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import platform.posix.uint8_tVar

@OptIn(ExperimentalForeignApi::class)
internal fun inAsByteArray(
    ptr: COpaquePointer,
    len: Int,
): ByteArray {
    return ptr.readBytes(len)
}

@OptIn(ExperimentalForeignApi::class)
internal fun outFill(
    data: ByteArray,
    out: COpaquePointer,
) {
    val outB = out.reinterpret<ByteVar>()
    for (i in data.indices) {
        outB[i] = data[i]
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun <R> withInBuffer(
    bytes: ByteArray,
    f: (array: CArrayPointer<uint8_tVar>) -> R,
): R {
    return memScoped {
        val arr = this.allocArray<uint8_tVar>(bytes.size)
        for (i in bytes.indices) {
            arr[i] = bytes[i].toUByte().convert()
        }
        f(arr)
    }
}
