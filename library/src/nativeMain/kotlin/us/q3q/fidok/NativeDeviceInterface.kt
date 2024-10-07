@file:Suppress("unused")
@file:OptIn(ExperimentalNativeApi::class)

package us.q3q.fidok

import co.touchlab.kermit.Logger
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.coroutines.runBlocking
import us.q3q.fidok.ctap.AuthenticatorDevice
import us.q3q.fidok.ctap.AuthenticatorListing
import kotlin.experimental.ExperimentalNativeApi

expect fun platformDeviceProviders(): List<AuthenticatorListing>

data class DeviceListingResult(
    val devices: List<AuthenticatorDevice>?,
)

@OptIn(ExperimentalForeignApi::class)
@CName("fidok_device_list")
fun listDevices(): COpaquePointer {
    val providers = platformDeviceProviders()

    val foundDevices = arrayListOf<AuthenticatorDevice>()
    for (provider in providers) {
        runBlocking {
            foundDevices.addAll(provider.listDevices())
        }
    }

    val ret = DeviceListingResult(foundDevices)

    return StableRef.create(ret).asCPointer()
}

@OptIn(ExperimentalForeignApi::class)
@CName("fidok_device_count")
fun countDevicesInList(listing: COpaquePointer): Int {
    return listing.asStableRef<DeviceListingResult>().get().devices?.size ?: 0
}

@OptIn(ExperimentalForeignApi::class)
@CName("fidok_free_device_list")
fun freeDeviceList(listing: COpaquePointer) {
    return listing.asStableRef<DeviceListingResult>().dispose()
}

@Suppress("LocalVariableName")
@OptIn(ExperimentalForeignApi::class)
@CName("fidok_send_bytes")
fun sendToDevice(
    listing: COpaquePointer,
    device_number: Int,
    input: COpaquePointer,
    input_len: Int,
    output: COpaquePointer,
    output_len: COpaquePointer,
): Int {
    val validDevices = listing.asStableRef<DeviceListingResult>().get().devices
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
