@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package us.q3q.fidok.gateway

import co.touchlab.kermit.Logger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.coroutines.delay
import platform.posix.EAGAIN
import platform.posix.O_NONBLOCK
import platform.posix.O_RDWR
import platform.posix.close
import platform.posix.errno
import platform.posix.memcpy
import platform.posix.open
import platform.posix.read
import platform.posix.ssize_t
import platform.posix.strerror
import platform.posix.write
import uhid.uhid_create2_req
import uhid.uhid_event
import uhid.uhid_event_type
import uhid.uhid_input2_req
import uhid.uhid_output_req
import us.q3q.fidok.ctap.DeviceCommunicationException
import us.q3q.fidok.ctap.FIDOkLibrary
import kotlin.experimental.ExperimentalNativeApi
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

actual typealias HIDGateway = LinuxHIDGateway

const val UHID_FILE_PATH = "/dev/uhid"

@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class, ExperimentalStdlibApi::class)
class LinuxHIDGateway() : HIDGatewayBase {

    private var uhid: Int? = null

    suspend fun listenForever(library: FIDOkLibrary) {
        Logger.v { "Starting Linux HID gateway" }

        val f = open(UHID_FILE_PATH, O_RDWR or O_NONBLOCK)
        if (f < 0) {
            throw DeviceCommunicationException(
                "Failed to open UHID (check permissions on $UHID_FILE_PATH): " + strerror(errno)?.toKString(),
            )
        }
        uhid = f
        try {
            Logger.i { "Acquired UHID interface" }

            createDevice()

            while (true) {
                try {
                    val incomingData = recv()

                    if (incomingData.isEmpty()) {
                        delay(100.milliseconds)
                        continue
                    }
                    handlePacket(this, library, incomingData)
                } catch (e: DeviceCommunicationException) {
                    Logger.e("Device communication error in HID gateway", e)
                }
            }
        } finally {
            close(f)
            uhid = null
        }
    }

    private suspend fun readEvent(): ByteArray? {
        val hid = uhid ?: throw IllegalStateException()

        memScoped {
            val evt = nativeHeap.alloc<uhid_event>()
            val readResult = read(hid, evt.ptr, sizeOf<uhid_event>().convert())
            if (readResult < 0) {
                if (errno == EAGAIN) {
                    // No worries, just no data
                    return null
                }
                throw DeviceCommunicationException("Failed to read from UHID: ${strerror(errno)?.toKString()}")
            }
            Logger.v { "Read $readResult bytes from UHID" }

            when (evt.type) {
                uhid_event_type.UHID_START.value -> {
                    Logger.i { "UHID started" }
                }
                uhid_event_type.UHID_OPEN.value -> {
                    Logger.i { "UHID opened" }
                }
                uhid_event_type.UHID_CLOSE.value -> {
                    Logger.i { "UHID closed" }
                }
                uhid_event_type.UHID_OUTPUT.value -> {
                    Logger.d { "UHID output" }
                    val req = evt.u.reinterpret<uhid_output_req>()
                    val size = req.size.convert<Int>() - 1
                    val b = ByteArray(size)
                    // chop off the first byte - the descriptor index
                    for (i in b.indices) {
                        b[i] = req.data[i + 1].toByte()
                    }
                    // req.rtype - do we care about this? Probably not
                    return b
                }
                else ->
                    Logger.w { "Unhandled UHID event type ${evt.type}" }
            }
        }
        return null
    }

    private fun <R> withUHIDEvent(type: uhid_event_type, f: (evt: uhid_event) -> R) {
        return memScoped {
            val evt = nativeHeap.alloc<uhid_event>()
            evt.type = type.value

            f(evt)
        }
    }

    private fun createDevice() {
        val unique = Random.nextBytes(63)

        withUHIDEvent(uhid_event_type.UHID_CREATE2) { evt ->
            val req = evt.u.reinterpret<uhid_create2_req>()

            req.bus = 0x03.convert() // USB
            req.vendor = FIDOK_HID_VENDOR.convert()
            req.product = FIDOK_HID_PRODUCT.convert()
            memcpy(req.name, FIDOK_HID_DEVICE_NAME.cstr, FIDOK_HID_DEVICE_NAME.length.convert())

            req.version = 0.convert()
            req.rd_size = FIDOK_REPORT_DESCRIPTOR.size.convert()
            for (i in FIDOK_REPORT_DESCRIPTOR.indices) {
                req.rd_data[i] = FIDOK_REPORT_DESCRIPTOR[i].convert()
            }
            for (i in unique.indices) {
                req.uniq[i] = unique[i].convert()
                req.phys[i] = unique[i].convert()
            }

            req.country = 0.convert()

            val writeResult = sendEventObjectToUHID(evt)
            Logger.v { "Wrote $writeResult bytes of creation request to UHID" }
        }
    }

    suspend fun send(bytes: ByteArray) {
        Logger.v { "Outgoing HID packet: ${bytes.toHexString()}" }

        withUHIDEvent(uhid_event_type.UHID_INPUT2) { evt ->
            val req = evt.u.reinterpret<uhid_input2_req>()
            req.size = bytes.size.convert()
            for (i in bytes.indices) {
                req.data[i] = bytes[i].convert()
            }

            val sentAmount = sendEventObjectToUHID(evt)

            Logger.v { "$sentAmount bytes sent to UHID" }
        }
    }

    suspend fun recv(): ByteArray {
        val now = TimeSource.Monotonic.markNow()
        while (now.elapsedNow() < HID_TIMEOUT) {
            val incomingData = readEvent()
            if (incomingData?.isEmpty() != false) {
                delay(HID_RECV_DELAY)
                continue
            }
            Logger.v { "${incomingData.size} bytes of incoming HID data: ${incomingData.toHexString()}" }
            return incomingData
        }
        throw DeviceCommunicationException("Timed out reading from device")
    }

    private fun sendEventObjectToUHID(evt: uhid_event): ssize_t {
        val hid = uhid ?: throw IllegalStateException()
        val writeResult = write(hid, evt.ptr, sizeOf<uhid_event>().convert())
        if (writeResult == -1L) {
            throw DeviceCommunicationException("Failed to write to UHID: ${strerror(errno)?.toKString()}")
        }
        assert(writeResult == sizeOf<uhid_event>())
        return writeResult
    }
}
