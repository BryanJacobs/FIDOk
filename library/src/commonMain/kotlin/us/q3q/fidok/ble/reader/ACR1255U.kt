import us.q3q.fidok.crypto.AESKey
import us.q3q.fidok.crypto.CryptoProvider
import us.q3q.fidok.ctap.DeviceCommunicationException
import kotlin.jvm.JvmStatic
import kotlin.math.min

/**
 * The UUID of the service used for transmitting commands to a reader connected
 * over Bluetooth Low Energy (BLE)
 */
const val BLE_READER_COMMAND_UUID = "3C4AFFF1-4783-3DE5-A983-D348718EF133"

/**
 * The UUID of the service used for receiving responses from a reader connected
 * over Bluetooth Low Energy (BLE)
 */
const val BLE_READER_RESPONSE_UUID = "3C4AFFF2-4783-3DE5-A983-D348718EF133"

/**
 * The default Customer Master Key used for mutual authentication with a BLE reader
 */
@OptIn(ExperimentalUnsignedTypes::class)
val DEFAULT_CMK =
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
                ((data.size and 0x0000FF00) shr 8).toUByte(),
                ((data.size and 0x000000FF)).toUByte(),
                slot,
                seq,
                param,
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

@OptIn(ExperimentalUnsignedTypes::class)
class CardPowerOnCommand : ACRBLECommand(
    type = ACRBLECommandType.CardPowerOn,
)

@OptIn(ExperimentalUnsignedTypes::class)
class CardPowerOffCommand : ACRBLECommand(
    type = ACRBLECommandType.CardPowerOff,
)

@OptIn(ExperimentalUnsignedTypes::class)
class GetSlotStatusCommand : ACRBLECommand(
    type = ACRBLECommandType.GetCardStatus,
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
data class EscapeCommand(val param: UByte, val data: UByteArray) : ACRBLECommand(
    type = ACRBLECommandType.Escape,
) {
    override fun getFrames(): List<UByteArray> {
        return listOf(
            getBytes(
                param = param,
                seq = 0x00u,
                data = data,
            ),
        )
    }
}

/**
 * Code for interacting with (or emulating) an ACR1255U-J1 BLE smartcard reader
 */
@OptIn(ExperimentalUnsignedTypes::class, ExperimentalStdlibApi::class)
class ACRCompatibleBLE {
    companion object {
        @JvmStatic
        fun wrapFrame(
            frame: UByteArray,
            cryptoProvider: CryptoProvider,
            key: AESKey,
        ): UByteArray {
            if (key.key.size != 16) {
                throw IllegalStateException("ACR-compatible BLE devices only use AES-128")
            }
            val encryptedFrame = cryptoProvider.aes128CBCEncrypt(frame.toByteArray(), key).toUByteArray()

            val totalLength = encryptedFrame.size + 5

            val lengthBytes =
                ubyteArrayOf(
                    ((totalLength and 0x0000FF00) shr 8).toUByte(),
                    (totalLength and 0x000000FF).toUByte(),
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

        @JvmStatic
        fun packetizeMessage(
            command: ACRBLECommand,
            seq: UByte,
            payload: UByteArray,
        ): List<UByteArray> {
            TODO("Not yet implemented")
        }
    }
}
