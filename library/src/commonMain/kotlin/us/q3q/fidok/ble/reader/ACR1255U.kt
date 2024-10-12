package us.q3q.fidok.ble.reader

import us.q3q.fidok.crypto.AESKey
import us.q3q.fidok.crypto.CryptoProvider
import us.q3q.fidok.ctap.DeviceCommunicationException
import kotlin.jvm.JvmStatic
import kotlin.math.min

/**
 * Proprietary service used for communication with an ACR-like reader
 * over Bluetooth Low Energy (BLE)
 */
const val ACR_BLE_READER_SERVICE_UUID = "3c4afff0-4783-3de5-a983-d348718ef133"

/**
 * The UUID of the characteristic used for transmitting commands to a reader connected
 * over Bluetooth Low Energy (BLE)
 */
const val ACR_BLE_READER_COMMAND_UUID = "3c4afff1-4783-3de5-a983-d348718ef133"

/**
 * The UUID of the characteristic used for receiving responses from a reader connected
 * over Bluetooth Low Energy (BLE)
 */
const val ACR_BLE_READER_RESPONSE_UUID = "3c4afff2-4783-3de5-a983-d348718ef133"

/**
 * The default Customer Master Key used for mutual authentication with a BLE reader
 */
@OptIn(ExperimentalUnsignedTypes::class)
val ACR_DEFAULT_CMK =
    ubyteArrayOf(
        0x41u, 0x43u, 0x52u, 0x31u, 0x32u, 0x35u, 0x35u, 0x55u,
        0x2Du, 0x4Au, 0x31u, 0x20u, 0x41u, 0x75u, 0x74u, 0x68u,
    )

/**
 * The type of command sent to a reader
 *
 * @property value byte-level representation of the command type
 */
enum class ACRBLECommandType(val value: UByte) {
    /**
     * Turn on the reader/card
     */
    CardPowerOn(0x62u),

    /**
     * Turn off the reader/card
     */
    CardPowerOff(0x63u),

    /**
     * Get the status of an (inserted? Nearby?) card
     */
    GetCardStatus(0x65u),

    /**
     * Send a smartcard Application Protocol Data Unit, AKA a real command
     */
    APDU(0x6Fu),

    /**
     * Communicate with the reader out-of-band
     */
    Escape(0x6Bu),
}

enum class ACRBLEResponseType(val value: UByte) {
    DataBlock(0x80u),
    SlotStatus(0x81u),
    Escape(0x83u),
    NotifyCardStatus(0x50u),
    HardwareError(0x51u),
}

enum class ACRBLEErrorCode(val value: UByte) {
    Checksum(0x01u),
    Timeout(0x02u),
    Command(0x03u),
    Unauthorized(0x04u),
    Undefined(0x05u),
    ReceivedData(0x06u),
    ExceededAuthenticationRetries(0x07u),
}

@OptIn(ExperimentalUnsignedTypes::class)
abstract class ACRBLECommand(val type: ACRBLECommandType, val slot: UByte = 0x00u) {
    /**
     * Serializes this command for transmission over BLE
     *
     * @param seq The sequence number for the encoded command
     * @param param The "parameter" byte for the encoded command
     * @param data Payload for the command
     */
    fun getBytes(
        param: UByte,
        seq: UByte,
        data: UByteArray,
    ): UByteArray {
        if (data.size > UShort.MAX_VALUE.toInt()) {
            throw DeviceCommunicationException("Overly long data payload")
        }
        val ret =
            ubyteArrayOf(
                type.value,
                (data.size / 256).toUByte(),
                (data.size % 256).toUByte(),
                slot,
                seq,
                param,
                0x00u,
            ) + data

        setChecksum(ret)

        return ret
    }

    open fun getFrames(): List<UByteArray> {
        return listOf(
            getBytes(
                param = 0x00u,
                seq = 0x00u,
                data = ubyteArrayOf(),
            ),
        )
    }

    companion object {
        fun checkSumOf(bytes: UByteArray): UByte {
            var checkSum: UByte = 0x00u
            bytes.forEach {
                checkSum = checkSum xor it
            }
            return checkSum
        }
    }

    /**
     * Computes the checksum of the given encoded command array, inserting it in place
     *
     * @param encodedCommand The command into which to insert the checksum
     */
    private fun setChecksum(encodedCommand: UByteArray) {
        encodedCommand[6] = 0x00u

        encodedCommand[6] = checkSumOf(encodedCommand)
    }
}

class CardPowerOnCommand : ACRBLECommand(
    type = ACRBLECommandType.CardPowerOn,
)

class CardPowerOffCommand : ACRBLECommand(
    type = ACRBLECommandType.CardPowerOff,
)

class GetSlotStatusCommand(slot: UByte = 0x00u) : ACRBLECommand(
    type = ACRBLECommandType.GetCardStatus,
    slot,
)

@OptIn(ExperimentalUnsignedTypes::class)
class SetLEDAndBuzzer(
    batteryChargingStatus: Boolean = true,
    piccPollingStatus: Boolean = false,
    piccActivationStatus: Boolean = true,
    cardInsertionBuzzer: Boolean = true,
    cardRemovalBuzzer: Boolean = true,
    readerPowerOnBuzzer: Boolean = true,
    rfu: Boolean = false,
    cardOperationBlink: Boolean = true,
) : EscapeCommand(
        param = 0x00u,
        data =
            ubyteArrayOf(
                0xE0u,
                0x00u,
                0x00u,
                0x21u,
                0x01u,
                (
                    (if (batteryChargingStatus) 0x01u else 0x00u) or
                        (if (piccPollingStatus) 0x02u else 0x00u) or
                        (if (piccActivationStatus) 0x04u else 0x00u) or
                        (if (cardInsertionBuzzer) 0x08u else 0x00u) or
                        (if (cardRemovalBuzzer) 0x10u else 0x00u) or
                        (if (readerPowerOnBuzzer) 0x20u else 0x00u) or
                        (if (rfu) 0x02u else 0x40u) or
                        (if (cardOperationBlink) 0x80u else 0x00u)
                ).toUByte(),
            ),
    )

@OptIn(ExperimentalUnsignedTypes::class)
data class APDUCommand(val data: UByteArray) : ACRBLECommand(
    type = ACRBLECommandType.APDU,
) {
    override fun getFrames(): List<UByteArray> {
        var remaining = data.size
        var firstSend = true
        val ret = arrayListOf<UByteArray>()
        var startIndex = 0

        while (remaining > 0) {
            val bytesToSend = min(remaining, 256)
            val lastSend = bytesToSend == remaining

            var param: UByte = 0x00u
            if (!firstSend) {
                param = param or 0x02u
            }
            if (!lastSend) {
                param = param or 0x01u
            }

            val theseBytes = data.copyOfRange(startIndex, startIndex + bytesToSend)

            val packet =
                getBytes(
                    param = param,
                    seq = 0x00u,
                    data = theseBytes,
                )
            ret.add(packet)

            firstSend = false
            remaining -= bytesToSend
            startIndex += bytesToSend
        }

        return ret
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
open class EscapeCommand(val param: UByte, val data: UByteArray) : ACRBLECommand(
    type = ACRBLECommandType.Escape,
) {
    override fun getFrames(): List<UByteArray> {
        return listOf(
            getBytes(
                param,
                seq = 0x00u,
                data,
            ),
        )
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
class AuthenticationReqPhase1 : EscapeCommand(
    param = 0x00u,
    data = ubyteArrayOf(0xE0u, 0x00u, 0x00u, 0x45u, 0x00u),
)

@OptIn(ExperimentalUnsignedTypes::class)
class GetFirmwareVersionCommand : EscapeCommand(
    param = 0x00u,
    data = ubyteArrayOf(0xE0u, 0x00u, 0x00u, 0x18u, 0x00u),
)

@OptIn(ExperimentalUnsignedTypes::class)
private fun transformPhase1RespIntoPhase2Req(
    cryptoProvider: CryptoProvider,
    cmk: AESKey,
    serverRandom: UByteArray,
    authPhase1Response: ReaderResponse,
): UByteArray {
    if (serverRandom.size != 16) {
        throw IllegalArgumentException("Invalid random byte length: should be 16, was ${serverRandom.size}")
    }

    val p1Bytes = authPhase1Response.getAuthPhase1Bytes(cryptoProvider, cmk)

    // Yes, this is supposed to be a DEcrypt.
    // For some reason, the values sent to the reader in phase2 represent the raw values put through
    // a DEcryption process. The reader will, on its own side, ENcrypt the value again to check.
    return cryptoProvider.aes128CBCDecrypt((serverRandom + p1Bytes).toByteArray(), cmk).toUByteArray()
}

@OptIn(ExperimentalUnsignedTypes::class)
class AuthenticationReqPhase2(
    cryptoProvider: CryptoProvider,
    cmk: AESKey,
    val serverRandom: UByteArray,
    phase1Response: ReaderResponse,
) : EscapeCommand(
        param = 0x00u,
        data =
            ubyteArrayOf(0xE0u, 0x00u, 0x00u, 0x46u, 0x00u) +
                transformPhase1RespIntoPhase2Req(cryptoProvider, cmk, serverRandom, phase1Response),
    )

@OptIn(ExperimentalUnsignedTypes::class)
data class ReaderResponse(
    val type: ACRBLEResponseType,
    val slot: UByte,
    val seq: UByte,
    val param: UByte,
    val payload: UByteArray,
    val addlInfo: String?,
) {
    private fun getAuthPhaseBytes(
        cryptoProvider: CryptoProvider,
        key: AESKey,
        stageByte: UByte,
    ): UByteArray {
        if (key.key.size != 16) {
            throw IllegalArgumentException("Tried to get auth phase data using incorrect key length")
        }

        if (type != ACRBLEResponseType.Escape) {
            throw IllegalStateException("Tried to get auth phase data from a non-Escape type packet")
        }

        if (payload.size != 21) {
            throw IllegalStateException("Incorrect payload size for requested auth phase")
        }

        if (payload[0] != 0xE1u.toUByte() ||
            payload[1] != 0x00u.toUByte() ||
            payload[2] != 0x00u.toUByte() ||
            payload[3] != stageByte ||
            payload[4] != 0x00u.toUByte()
        ) {
            throw IllegalStateException("Incorrect payload for requested auth phase")
        }

        return cryptoProvider.aes128CBCDecrypt(payload.copyOfRange(5, 21).toByteArray(), key).toUByteArray()
    }

    fun getAuthPhase1Bytes(
        cryptoProvider: CryptoProvider,
        cmk: AESKey,
    ): UByteArray {
        return getAuthPhaseBytes(cryptoProvider, cmk, 0x45u)
    }

    fun getAuthPhase2Bytes(
        cryptoProvider: CryptoProvider,
        cmk: AESKey,
    ): UByteArray {
        return getAuthPhaseBytes(cryptoProvider, cmk, 0x46u)
    }

    fun getFirmwareVersionBytes(): UByteArray {
        if (type != ACRBLEResponseType.Escape) {
            throw IllegalStateException("Tried to get firmware version from a non-Escape type packet")
        }

        if (payload[0] != 0xE1u.toUByte() ||
            payload[1] != 0x00u.toUByte() ||
            payload[2] != 0x00u.toUByte() ||
            payload[3] != 0x00u.toUByte()
        ) {
            throw IllegalStateException("Incorrect payload for firmware version response")
        }

        if (payload[4] != (payload.size - 5).toUByte()) {
            throw IllegalStateException("Incorrect length byte for firmware version response")
        }

        return payload.copyOfRange(5, payload.size)
    }

    companion object {
        @OptIn(ExperimentalStdlibApi::class)
        fun decode(
            cryptoProvider: CryptoProvider? = null,
            key: AESKey? = null,
            reader: () -> UByteArray,
        ): ReaderResponse {
            val rawData = reader()
            var data = ACRCompatibleBLE.unwrapFrame(rawData, cryptoProvider, key, reader)

            if (data.size < 7) {
                throw DeviceCommunicationException("Overly short data response")
            }

            val dataType = ACRBLEResponseType.entries.find { it.value == data[0] }
            if (dataType == null) {
                throw DeviceCommunicationException("Unexpected first byte: ${data[0]}")
            }

            val dataLength = data[1] * 256u + data[2]
            val expectedLength = (data.size - 7).toUInt()
            if (dataLength > expectedLength) {
                throw DeviceCommunicationException(
                    "Incorrect received data length: " +
                        "$dataLength declared but had $expectedLength bytes",
                )
            }
            data = data.copyOfRange(0, 7 + dataLength.toInt())

            if (ACRBLECommand.checkSumOf(data) != 0x00u.toUByte()) {
                throw DeviceCommunicationException("Incorrect checksum in received data")
            }

            val slot = data[3]
            val seq = data[4]
            val param = data[4]

            val payload =
                if (data.size < 7) {
                    ubyteArrayOf()
                } else {
                    data.copyOfRange(7, data.size)
                }

            val info =
                if (dataType == ACRBLEResponseType.HardwareError) {
                    ACRBLEErrorCode.entries.find { it.value == param }?.name
                } else {
                    null
                }

            return ReaderResponse(dataType, slot, seq, param, payload, info)
        }
    }
}

/**
 * Code for interacting with (or emulating) an ACR1255U-J1 BLE smartcard reader
 */
@OptIn(ExperimentalUnsignedTypes::class)
class ACRCompatibleBLE {
    companion object {
        @JvmStatic
        fun buildSessionKey(
            cryptoProvider: CryptoProvider,
            cmk: AESKey,
            phase1: ReaderResponse,
            phase2Req: AuthenticationReqPhase2,
            phase2Res: ReaderResponse,
        ): AESKey {
            val p1Bytes = phase1.getAuthPhase1Bytes(cryptoProvider, cmk)
            val randomChosen = phase2Req.serverRandom
            val confirmationOfRandomChosen = phase2Res.getAuthPhase2Bytes(cryptoProvider, cmk)

            if (!confirmationOfRandomChosen.contentEquals(randomChosen)) {
                throw DeviceCommunicationException("Failed to match chosen key with response from reader")
            }

            return AESKey(
                key = (p1Bytes.copyOfRange(0, 8) + randomChosen.copyOfRange(0, 8)).toByteArray(),
                iv =
                    byteArrayOf(
                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    ),
            )
        }

        @JvmStatic
        fun wrapFrame(
            frame: UByteArray,
            cryptoProvider: CryptoProvider? = null,
            sessionKey: AESKey? = null,
        ): UByteArray {
            val paddingLength =
                if (sessionKey == null) {
                    0
                } else {
                    (16 - (frame.size % 16)) % 16
                }

            val encryptedFrame =
                if (sessionKey == null) {
                    frame
                } else {
                    if (sessionKey.key.size != 16) {
                        throw IllegalArgumentException("ACR-compatible BLE devices only use AES-128")
                    }

                    if (cryptoProvider == null) {
                        throw IllegalArgumentException("Crypto provider required to encrypt frame")
                    }

                    val padding = ByteArray(paddingLength) { 0xFFu.toByte() }

                    cryptoProvider.aes128CBCEncrypt(
                        frame.toByteArray() + padding,
                        sessionKey,
                    ).toUByteArray()
                }

            val totalLength = encryptedFrame.size

            if (totalLength > UShort.MAX_VALUE.toInt()) {
                throw IllegalArgumentException("Maximum length cannot be represented in framing")
            }

            val lengthBytes =
                ubyteArrayOf(
                    (totalLength / 256).toUByte(),
                    (totalLength % 256).toUByte(),
                )

            val checkSum = ACRBLECommand.checkSumOf(lengthBytes + encryptedFrame)

            return ubyteArrayOf(
                0x05u,
            ) + lengthBytes + encryptedFrame +
                ubyteArrayOf(
                    checkSum,
                    0x0Au,
                )
        }

        @OptIn(ExperimentalStdlibApi::class)
        @JvmStatic
        fun unwrapFrame(
            origFrame: UByteArray,
            cryptoProvider: CryptoProvider? = null,
            key: AESKey? = null,
            readMoreData: () -> UByteArray,
        ): UByteArray {
            val frameOverhead = 5u

            var frame = origFrame
            if (frame[0] != 0x05u.toUByte()) {
                throw DeviceCommunicationException("Invalid start of response frame")
            }
            if (frame.size.toUInt() < frameOverhead) {
                throw DeviceCommunicationException("Frame too short to be valid")
            }

            val dataLength = frame[1] * 256u + frame[2]
            if (frame.size.toUInt() > dataLength + frameOverhead) {
                throw DeviceCommunicationException("Incorrect frame length on response traffic")
            }

            while (frame.size.toUInt() < dataLength + frameOverhead) {
                val moreData = readMoreData()
                frame = frame + moreData
            }

            if (frame[frame.size - 1] != 0x0Au.toUByte()) {
                throw DeviceCommunicationException("Invalid end of response frame")
            }
            val expectedChecksum = ACRBLECommand.checkSumOf(frame.copyOfRange(1, frame.size - 2))
            val gottenChecksum = frame[frame.size - 2]
            if (expectedChecksum != gottenChecksum) {
                throw DeviceCommunicationException("Incorrect checksum on response traffic")
            }

            val frameData = frame.copyOfRange(3, frame.size - 2)

            val decryptedFrame =
                if (key == null) {
                    frameData
                } else {
                    if (key.key.size != 16) {
                        throw IllegalArgumentException("ACR-compatible BLE devices only use AES-128")
                    }

                    if (cryptoProvider == null) {
                        throw IllegalArgumentException("Crypto provider required to decrypt frame")
                    }

                    val paddingLength = (16 - (frameData.size % 16)) % 16
                    val padding = ByteArray(paddingLength)

                    val paddedDecrypt =
                        cryptoProvider.aes128CBCDecrypt(
                            frameData.toByteArray() + padding,
                            key,
                        )

                    paddedDecrypt.copyOfRange(0, frameData.size).toUByteArray()
                }

            return decryptedFrame
        }

        @OptIn(ExperimentalStdlibApi::class)
        @JvmStatic
        fun packetizeMessage(
            command: ACRBLECommand,
            cryptoProvider: CryptoProvider?,
            sessionKey: AESKey?,
        ): List<UByteArray> {
            return command.getFrames().flatMap {
                val frame = wrapFrame(it, cryptoProvider, sessionKey)

                val packets = arrayListOf<UByteArray>()
                var offset = 0
                while (offset < frame.size) {
                    val bytesForThisPacket = min(frame.size - offset, 20)
                    val packet = frame.copyOfRange(offset, offset + bytesForThisPacket)

                    packets.add(packet)
                    offset += bytesForThisPacket
                }
                packets
            }
        }

        fun sendAndReceive(
            command: ACRBLECommand,
            send: (bytes: UByteArray) -> Unit,
            recv: () -> UByteArray,
            cryptoProvider: CryptoProvider?,
            sessionKey: AESKey?,
        ): ReaderResponse {
            if ((cryptoProvider == null) != (sessionKey == null)) {
                throw IllegalArgumentException(
                    "Either both cryptoProvider and sessionKey must be null, or" +
                        "neither must be",
                )
            }

            if (sessionKey != null && sessionKey.key.size != 16) {
                throw IllegalArgumentException("Invalid session key: should be an AES128 key (16 bytes)")
            }

            val packets = packetizeMessage(command, cryptoProvider, sessionKey)
            for (packet in packets) {
                send(packet)
            }

            val ret =
                ReaderResponse.decode(cryptoProvider, sessionKey) {
                    recv()
                }

            if (ret.type == ACRBLEResponseType.HardwareError) {
                throw DeviceCommunicationException("Got hardware error from reader: $ret")
            }

            return ret
        }
    }
}
