package us.q3q.fidok

import co.touchlab.kermit.Logger
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import pcsc.SCARDCONTEXT
import pcsc.SCARDCONTEXTVar
import pcsc.SCARDHANDLE
import pcsc.SCARD_E_NO_SMARTCARD
import pcsc.SCARD_LEAVE_CARD
import pcsc.SCARD_PCI_RAW
import pcsc.SCARD_PCI_T0
import pcsc.SCARD_PCI_T1
import pcsc.SCARD_PROTOCOL_ANY
import pcsc.SCARD_PROTOCOL_RAW
import pcsc.SCARD_PROTOCOL_T0
import pcsc.SCARD_PROTOCOL_T1
import pcsc.SCARD_SCOPE_SYSTEM
import pcsc.SCARD_SHARE_SHARED
import pcsc.SCARD_S_SUCCESS
import pcsc.SCardConnect
import pcsc.SCardDisconnect
import pcsc.SCardEstablishContext
import pcsc.SCardListReaders
import pcsc.SCardReleaseContext
import pcsc.SCardTransmit
import pcsc.pcsc_stringify_error
import platform.posix.uint32_tVar
import us.q3q.fidok.ctap.AuthenticatorDevice
import us.q3q.fidok.ctap.AuthenticatorListing
import us.q3q.fidok.ctap.AuthenticatorTransport
import us.q3q.fidok.ctap.DeviceCommunicationException
import us.q3q.fidok.ctap.IncorrectDataException
import us.q3q.fidok.pcsc.CTAPPCSC.Companion.APPLET_SELECT_BYTES
import us.q3q.fidok.pcsc.CTAPPCSC.Companion.sendAndReceive

@OptIn(ExperimentalForeignApi::class)
class MacPCSCLiteDevice(private val readerName: String, private val useExtendedMessages: Boolean = false) : AuthenticatorDevice {
    private var appletSelected = false

    @OptIn(ExperimentalStdlibApi::class, ExperimentalForeignApi::class)
    companion object : AuthenticatorListing {
        override fun providedTransports(): List<AuthenticatorTransport> {
            return listOf(AuthenticatorTransport.NFC, AuthenticatorTransport.SMART_CARD)
        }

        private fun checkOp(
            msg: String,
            f: () -> platform.posix.int32_t,
        ): Boolean? {
            val ret = f()
            if (ret != SCARD_S_SUCCESS) {
                Logger.e(
                    "Failed to $msg: ${
                        pcsc_stringify_error(ret)?.toKString()}",
                )
                return null
            }
            return true
        }

        private fun <T> withContext(f: (ctx: SCARDCONTEXT) -> T): T? {
            memScoped {
                val ctx = nativeHeap.alloc<SCARDCONTEXTVar>()
                checkOp("establish context") {
                    SCardEstablishContext(
                        SCARD_SCOPE_SYSTEM.convert(), null, null,
                        ctx.ptr,
                    )
                } ?: return null

                val ret = f(ctx.value)

                SCardReleaseContext(ctx.value)

                return ret
            }
        }

        private fun <T> withConnection(
            ctx: SCARDCONTEXT,
            readerName: String,
            f: (conn: SCARDHANDLE, protocol: Int) -> T,
        ): T? {
            memScoped {
                val protocol = nativeHeap.alloc<uint32_tVar>()
                val scard = nativeHeap.alloc<SCARDCONTEXTVar>()
                val connectResult =
                    SCardConnect(
                        ctx,
                        readerName,
                        SCARD_SHARE_SHARED.convert(),
                        SCARD_PROTOCOL_ANY.convert(),
                        scard.ptr,
                        protocol.ptr,
                    )
                if (connectResult == SCARD_E_NO_SMARTCARD.toInt()) {
                    // oops, empty reader
                    Logger.d("No card in reader $readerName")
                    return null
                }
                if (connectResult != SCARD_S_SUCCESS) {
                    // this error is more serious...
                    throw DeviceCommunicationException(
                        "Error connecting to reader $readerName: ${
                            pcsc_stringify_error(connectResult)?.toKString()}",
                    )
                }
                Logger.d("Connected to card in reader $readerName with protocol ${protocol.value}")

                val ret = f(scard.value, protocol.value.convert())

                SCardDisconnect(scard.value, SCARD_LEAVE_CARD.convert())

                return ret
            }
        }

        private fun xmitBytes(
            conn: SCARDHANDLE,
            protocol: Int,
            bytes: ByteArray,
            checkErrors: Boolean = true,
        ): ByteArray? {
            val proto =
                when (protocol) {
                    SCARD_PROTOCOL_T0 ->
                        SCARD_PCI_T0
                    SCARD_PROTOCOL_T1 ->
                        SCARD_PCI_T1
                    SCARD_PROTOCOL_RAW ->
                        SCARD_PCI_RAW
                    else ->
                        throw NotImplementedError("Protocols other than T=0 and T=1 not supported!")
                }

            val sendLength = bytes.size
            val sendBuffer = nativeHeap.allocArray<UByteVar>(sendLength)
            for (i in bytes.indices) {
                sendBuffer[i] = bytes[i].convert()
            }
            val recvBufferLength = 4096
            val recvLength = nativeHeap.alloc<uint32_tVar>()
            recvLength.value = recvBufferLength.convert()
            val recvBuffer = nativeHeap.allocArray<UByteVar>(recvBufferLength)

            Logger.v { "Sending $sendLength bytes to PC/SC: ${bytes.toHexString()}" }

            checkOp("xmit") {
                SCardTransmit(
                    conn, proto, sendBuffer, sendLength.convert(), null,
                    recvBuffer, recvLength.ptr,
                )
            } ?: return null

            Logger.v { "Received ${recvLength.value} response bytes" }

            var received = recvLength.value.toInt()

            if (checkErrors) {
                if (received < 2) {
                    throw IncorrectDataException("Short receive: only $received bytes")
                }
                val status = recvBuffer[received - 2].toInt() shl 8 + recvBuffer[received - 1].toInt()
                if (status != 0x9000) {
                    throw IncorrectDataException("Failure response from card: 0x${status.toHexString()}")
                }
                received -= 2
            }

            val readBytes = ByteArray(received)
            for (i in 0..<received) {
                readBytes[i] = recvBuffer[i].convert()
            }

            return readBytes
        }

        private fun checkReader(
            ctx: SCARDCONTEXT,
            readerName: String,
        ): Boolean {
            return withConnection(ctx, readerName) { conn, protocol ->

                Logger.v("Selecting PC/SC applet on $readerName")

                val readBytes = xmitBytes(conn, protocol, APPLET_SELECT_BYTES, false) ?: return@withConnection null

                Logger.v("Gotten response to select: ${readBytes.toHexString()}")

                if (readBytes[readBytes.size - 2] != 0x90.toByte() || readBytes[readBytes.size - 1] != 0x00.toByte()) {
                    return@withConnection null
                } else {
                    // OK response to applet selection - we're good to use this card/reader
                    true
                }
            } != null
        }

        override fun listDevices(): List<MacPCSCLiteDevice> {
            Logger.v("Listing PCSC devices")
            return withContext { ctx ->
                memScoped {
                    val numReaders = nativeHeap.alloc<uint32_tVar>()
                    checkOp("fetch number of readers") {
                        SCardListReaders(ctx, null, null, numReaders.ptr)
                    } ?: return@withContext listOf<MacPCSCLiteDevice>()

                    val readers = nativeHeap.allocArray<ByteVar>(numReaders.value.convert())
                    checkOp("List readers") {
                        SCardListReaders(ctx, null, readers, numReaders.ptr)
                    } ?: return@withContext listOf<MacPCSCLiteDevice>()

                    val ret = arrayListOf<MacPCSCLiteDevice>()
                    var accumulator = ""
                    for (i in 0..<numReaders.value.toInt()) {
                        if (readers[i] == 0x00.toByte()) {
                            if (accumulator.isNotEmpty()) {
                                if (checkReader(ctx, accumulator)) {
                                    ret.add(MacPCSCLiteDevice(accumulator, useExtendedMessages = false))
                                }
                                accumulator = ""
                            }
                            continue
                        }
                        accumulator += readers[i].toInt().toChar()
                    }

                    Logger.d("Found ${ret.size} valid readers")

                    return@withContext ret
                }
            } ?: listOf()
        }
    }

    override fun sendBytes(bytes: ByteArray): ByteArray {
        return withContext { ctx ->
            return@withContext withConnection(ctx, readerName) { conn, protocol ->
                return@withConnection sendAndReceive(bytes, !appletSelected, useExtendedMessages) {
                    appletSelected = true
                    xmitBytes(conn, protocol, it, false)
                        ?: throw DeviceCommunicationException("Failed to send bytes to $readerName")
                }
            } ?: throw DeviceCommunicationException("Failed to connect to $readerName")
        } ?: throw DeviceCommunicationException("Failed to open smartcard context")
    }

    override fun getTransports(): List<AuthenticatorTransport> {
        return providedTransports()
    }
}
