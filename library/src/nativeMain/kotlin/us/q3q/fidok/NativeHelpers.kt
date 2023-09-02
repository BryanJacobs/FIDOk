package us.q3q.fidok

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set

@OptIn(ExperimentalForeignApi::class)
internal fun inAsByteArray(ptr: COpaquePointer, len: Int): ByteArray {
    return ptr.readBytes(len)
}

@OptIn(ExperimentalForeignApi::class)
internal fun outFill(data: ByteArray, out: COpaquePointer) {
    val outB = out.reinterpret<ByteVar>()
    for (i in data.indices) {
        outB[i] = data[i]
    }
}
