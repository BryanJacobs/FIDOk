package us.q3q.fidok

import co.touchlab.kermit.Logger
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import us.q3q.fidok.ble.reader.ACRBLEResponseType
import us.q3q.fidok.ble.reader.ACRCompatibleBLE
import us.q3q.fidok.ble.reader.ACR_BLE_READER_COMMAND_UUID
import us.q3q.fidok.ble.reader.ACR_BLE_READER_RESPONSE_UUID
import us.q3q.fidok.ble.reader.ACR_BLE_READER_SERVICE_UUID
import us.q3q.fidok.ble.reader.ACR_DEFAULT_CMK
import us.q3q.fidok.ble.reader.APDUCommand
import us.q3q.fidok.ble.reader.AuthenticationReqPhase1
import us.q3q.fidok.ble.reader.AuthenticationReqPhase2
import us.q3q.fidok.ble.reader.CardPowerOnCommand
import us.q3q.fidok.crypto.AESKey
import us.q3q.fidok.crypto.CryptoProvider
import us.q3q.fidok.ctap.AuthenticatorDevice
import us.q3q.fidok.ctap.AuthenticatorTransport
import us.q3q.fidok.ctap.DeviceCommunicationException
import us.q3q.fidok.pcsc.CTAPPCSC
import kotlin.random.Random
import kotlin.random.nextUBytes

class LinuxBluetoothReader(
    private val address: String,
    private val name: String,
    private val listing: LinuxBluetoothDeviceListing,
) : AuthenticatorDevice {
    init {
        listing.ref()
    }

    protected fun finalize() {
        listing.unref()
    }

    private var connected: Boolean = false
    private var sessionKey: AESKey? = null
    private val cmk =
        AESKey(
            key = ACR_DEFAULT_CMK.toByteArray(),
            iv =
                byteArrayOf(
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                ),
        )

    private fun <R> withNotifyResponses(
        block: (
            send: (UByteArray) -> Unit,
            recv: () -> UByteArray,
        ) -> R,
    ): R {
        val readResult = Channel<UByteArray>(Channel.BUFFERED)

        val send: (UByteArray) -> Unit = {
            runBlocking {
                listing.write(
                    address,
                    ACR_BLE_READER_SERVICE_UUID,
                    ACR_BLE_READER_COMMAND_UUID,
                    it,
                    false,
                )
            }
        }

        val recv: () -> UByteArray = {
            runBlocking {
                readResult.receive()
            }
        }

        val ok = listing.setNotify(address, ACR_BLE_READER_SERVICE_UUID, ACR_BLE_READER_RESPONSE_UUID, readResult)
        if (!ok) {
            throw DeviceCommunicationException("Failed to set up notifications for responses")
        }
        try {
            return block(send, recv)
        } finally {
            listing.setNotify(address, ACR_BLE_READER_SERVICE_UUID, ACR_BLE_READER_RESPONSE_UUID, null)
        }
    }

    private suspend fun connect(cryptoProvider: CryptoProvider) {
        if (!connected) {
            try {
                connected = listing.connect(address)
            } catch (e: Exception) {
                throw DeviceCommunicationException(e.message)
            }

            withNotifyResponses { send, recv ->
                val auth1Response =
                    ACRCompatibleBLE.sendAndReceive(
                        AuthenticationReqPhase1(),
                        send,
                        recv,
                        null,
                        null,
                    )

                val auth2Req =
                    AuthenticationReqPhase2(
                        cryptoProvider,
                        cmk,
                        Random.nextUBytes(16),
                        auth1Response,
                    )

                val auth2Response =
                    ACRCompatibleBLE.sendAndReceive(
                        auth2Req,
                        send,
                        recv,
                        null,
                        null,
                    )

                sessionKey =
                    ACRCompatibleBLE.buildSessionKey(
                        cryptoProvider,
                        cmk,
                        phase1 = auth1Response,
                        phase2Req = auth2Req,
                        phase2Res = auth2Response,
                    )

                val powerOnResponse =
                    ACRCompatibleBLE.sendAndReceive(
                        CardPowerOnCommand(),
                        send,
                        recv,
                        cryptoProvider,
                        sessionKey,
                    )

                Logger.v { "Power-on response: $powerOnResponse" }

                if (powerOnResponse.type != ACRBLEResponseType.DataBlock) {
                    throw DeviceCommunicationException("Incorrect response to card power-on")
                }

                if ((powerOnResponse.param and 0x40u) != 0x00u.toUByte()) {
                    throw DeviceCommunicationException("Error in response to card power-on")
                }
            }
        }
    }

    override fun sendBytes(bytes: ByteArray): ByteArray {
        val cryptoProvider = listing.getLibrary().cryptoProvider

        runBlocking {
            connect(cryptoProvider)
        }

        Logger.d { "Connected to BLE reader $name" }

        return CTAPPCSC.sendAndReceive(
            bytes,
            selectApplet = true,
            useExtendedMessages = true,
        ) {
            val apduResponse =
                withNotifyResponses { send, recv ->
                    ACRCompatibleBLE.sendAndReceive(
                        APDUCommand(it.toUByteArray()),
                        send,
                        recv,
                        cryptoProvider,
                        sessionKey,
                    )
                }

            Logger.v { "APDU response: $apduResponse" }

            if (apduResponse.type != ACRBLEResponseType.DataBlock) {
                throw DeviceCommunicationException("Incorrect response type to BLE APDU")
            }

            if ((apduResponse.param and 0x40u) != 0x00u.toUByte()) {
                throw DeviceCommunicationException("Error in response to APDU command")
            }

            apduResponse.payload.toByteArray()
        }
    }

    override fun getTransports(): List<AuthenticatorTransport> {
        return listOf(AuthenticatorTransport.BLE)
    }

    override fun toString(): String {
        return "BLE-RDR:$name ($address)"
    }
}
