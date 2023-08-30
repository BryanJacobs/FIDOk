@file:Suppress("unused")

package us.q3q.fidok

import co.touchlab.kermit.Logger
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import us.q3q.fidok.ctap.Device
import kotlin.experimental.ExperimentalNativeApi

var devices: List<Device>? = null

@CName("fidok_count_devices")
fun listDevices(): Int {
    val foundDevices = HIDDevice.list() + PCSCDevice.list()
    devices = foundDevices
    return foundDevices.size
}

@Suppress("LocalVariableName")
@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class, ExperimentalStdlibApi::class)
@CName("fidok_send_bytes")
fun sendToDevice(
    device_number: Int,
    input: COpaquePointer,
    input_len: Int,
    output: COpaquePointer,
    output_len: COpaquePointer,
): Int {
    val validDevices = devices
    if (validDevices == null) {
        Logger.e { "Attempted to use native device $device_number when devices not listed" }
        return -1
    }
    val device = validDevices[device_number]

    val inB = inAsByteArray(input, input_len)
    val outputLenB = inAsByteArray(output_len, 4)
    var outputLen = 0
    for (i in 0..3) {
        outputLen += (outputLenB[i].toUByte().toUInt() shl (8 * (3 - i))).toInt()
    }

    val ret = device.sendBytes(inB)

    if (ret.size > outputLen) {
        Logger.e { "Response was ${ret.size} bytes long, but maximum buffer size is $outputLen" }
        return -1
    }
    val outLenB = output_len.reinterpret<ByteVar>()
    for (i in 0..3) {
        outLenB[i] = ((ret.size and (0xFF shl (8 * (3 - i)))) shr (8 * (3 - i))).toByte()
    }
    outFill(ret, output)
    return 0
}
