@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package us.q3q.fidok.gateway

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFNumberCreate
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFNumberSInt32Type
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.IOKit.IOHIDManagerCreate
import platform.IOKit.IOHIDManagerOpen
import platform.IOKit.IOHIDUserDeviceCreateWithProperties
import platform.IOKit.IOHIDUserDeviceRef
import platform.IOKit.kIOHIDOptionsTypeNone
import platform.IOKit.kIOHIDProductIDKey
import platform.IOKit.kIOHIDReportDescriptorKey
import platform.IOKit.kIOHIDVendorIDKey
import platform.IOKit.kIOReturnError
import platform.posix.int32_tVar
import platform.posix.uint8_tVar
import us.q3q.fidok.ctap.DeviceCommunicationException
import us.q3q.fidok.ctap.FIDOkLibrary

@OptIn(ExperimentalForeignApi::class)
actual class HIDGateway : HIDGatewayBase {
    override val deviceChannelMap: MutableMap<UInt, AuthenticatorDevice> = hashMapOf()

    private enum class DictValueType {
        INT,
        BYTES,
    }

    private fun <R> withCFDict(
        map: Map<String, Pair<DictValueType, Any>>,
        callback: (ref: CFMutableDictionaryRef) -> R,
    ): R {
        memScoped {
            val dict =
                CFDictionaryCreateMutable(kCFAllocatorDefault, map.entries.size.convert(), null, null)
                    ?: throw IllegalStateException("Failed to create CFDictionary")

            val retainers = arrayListOf<CFTypeRef?>()

            for (entry in map.entries) {
                val value =
                    when (entry.value.first) {
                        DictValueType.BYTES -> {
                            val valueCast = entry.value.second as ByteArray
                            val cVal = this.allocArray<uint8_tVar>(valueCast.size)
                            for (i in valueCast.indices) {
                                cVal[i] = valueCast[i].toUByte().convert()
                            }
                            CFDataCreate(kCFAllocatorDefault, cVal, valueCast.size.convert())
                        }
                        DictValueType.INT -> {
                            val valueCast = entry.value.second as Int?
                            val cVal = this.alloc<int32_tVar>()
                            if (valueCast != null) {
                                cVal.value = valueCast
                            }
                            CFNumberCreate(kCFAllocatorDefault, kCFNumberSInt32Type, cVal.ptr)
                        }
                    }

                val retainer = CFBridgingRetain(value)
                retainers.add(retainer)

                CFDictionaryAddValue(dict, entry.key.cstr, retainer)
            }

            val ret = callback(dict)

            for (retainer in retainers) {
                CFBridgingRelease(retainer)
            }

            return ret
        }
    }

    actual suspend fun listenForever(library: FIDOkLibrary) {
        val manager = IOHIDManagerCreate(kCFAllocatorDefault, kIOHIDOptionsTypeNone)

        val openResult = IOHIDManagerOpen(manager, kIOHIDOptionsTypeNone)
        if (openResult == kIOReturnError) {
            throw DeviceCommunicationException("Could not open IO HID Manager")
        }

        val props =
            mapOf(
                kIOHIDVendorIDKey to (DictValueType.INT to FIDOK_HID_VENDOR),
                kIOHIDProductIDKey to (DictValueType.INT to FIDOK_HID_PRODUCT),
                kIOHIDReportDescriptorKey to (DictValueType.BYTES to FIDOK_REPORT_DESCRIPTOR),
                // kIOHIDRequestTimeoutKey to (DictValueType.INT to HID_TIMEOUT)
            )

        var device: IOHIDUserDeviceRef? = null

        try {
            device = withCFDict(props) { properties ->
                IOHIDUserDeviceCreateWithProperties(kCFAllocatorDefault, properties, kIOHIDOptionsTypeNone)
            } ?: throw DeviceCommunicationException("Failed to create IO HID User device")

            // IOHIDUserDeviceScheduleWithRunLoop(device, CFRunLoopGetCurrent(), kCFRunLoopDefaultMode)

            // CFRunLoopRun()
        } finally {
            if (device != null) {
                CFRelease(device)
            }
        }
    }

    actual suspend fun send(bytes: ByteArray) {
        throw NotImplementedError()
    }

    actual suspend fun recv(): ByteArray {
        throw NotImplementedError()
    }
}
