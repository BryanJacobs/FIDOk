package us.q3q.fidok.ctap

import co.touchlab.kermit.Logger
import kotlinx.serialization.DeserializationStrategy
import us.q3q.fidok.crypto.KeyAgreementPlatformKey
import us.q3q.fidok.crypto.P256Point
import us.q3q.fidok.crypto.PinProtocol
import us.q3q.fidok.crypto.PinProtocolV1
import us.q3q.fidok.crypto.PinProtocolV2
import us.q3q.fidok.ctap.commands.COSEAlgorithmIdentifier
import us.q3q.fidok.ctap.commands.COSEKey
import us.q3q.fidok.ctap.commands.CTAPCBORDecoder
import us.q3q.fidok.ctap.commands.ClientPinCommand
import us.q3q.fidok.ctap.commands.ClientPinGetKeyAgreementResponse
import us.q3q.fidok.ctap.commands.ClientPinGetRetriesResponse
import us.q3q.fidok.ctap.commands.ClientPinGetTokenResponse
import us.q3q.fidok.ctap.commands.CredentialCreationOption
import us.q3q.fidok.ctap.commands.CtapCommand
import us.q3q.fidok.ctap.commands.GetInfoCommand
import us.q3q.fidok.ctap.commands.GetInfoResponse
import us.q3q.fidok.ctap.commands.MakeCredentialCommand
import us.q3q.fidok.ctap.commands.MakeCredentialResponse
import us.q3q.fidok.ctap.commands.PublicKeyCredentialParameters
import us.q3q.fidok.ctap.commands.PublicKeyCredentialRpEntity
import us.q3q.fidok.ctap.commands.PublicKeyCredentialUserEntity
import us.q3q.fidok.ctap.commands.ResetCommand
import kotlin.random.Random

enum class CTAPResponse(val value: UByte) {
    OK(0x00u),
    INVALID_COMMAND(0x01u),
    INVALID_PARAMETER(0x02u),
    INVALID_LENGTH(0x03u),
    INVALID_SEQ(0x04u),
    TIMEOUT(0x05u),
    CHANNEL_BUSY(0x06u),
    LOCK_REQUIRED(0x0Au),
    INVALID_CHANNEL(0x0Bu),
    CBOR_UNEXPECTED_TYPE(0x11u),
    INVALID_CBOR(0x12u),
    MISSING_PARAMETER(0x14u),
    LIMIT_EXCEEDED(0x15u),
    FP_DATABASE_FULL(0x17u),
    LARGE_BLOB_STORAGE_FULL(0x18u),
    CREDENTIAL_EXCLUDED(0x19u),
    PROCESSING(0x21u),
    INVALID_CREDENTIAL(0x22u),
    USER_ACTION_PENDING(0x23u),
    OPERATION_PENDING(0x24u),
    NO_OPERATIONS(0x25u),
    UNSUPPORTED_ALGORITHM(0x26u),
    OPERATION_DENITED(0x27u),
    KEY_STORE_FULL(0x28u),
    UNSUPPORTED_OPTION(0x2Bu),
    INVALID_OPTION(0x2Cu),
    KEEPALIVE_CANCEL(0x2Du),
    NO_CREDENTIALS(0x2Eu),
    USER_ACTION_TIMEOUT(0x2Fu),
    NOT_ALLOWED(0x30u),
    PIN_INVALID(0x31u),
    PIN_BLOCKED(0x32u),
    PIN_AUTH_INVALID(0x33u),
    PIN_AUTH_BLOCKED(0x34u),
    PIN_NOT_SET(0x35u),
    PUAT_REQUIRED(0x36u),
    PIN_POLICY_VIOLATION(0x37u),
    REQUEST_TOO_LARGE(0x39u),
    ACTION_TIMEOUT(0x3Au),
    UP_REQUIRED(0x3Bu),
    UV_BLOCKED(0x3Cu),
    INTEGRITY_FAILURE(0x3Du),
    INVALID_SUBCOMMAND(0x3Eu),
    UV_INVALID(0x3Fu),
    UNAUTHORIZED_PERMISSION(0x40u),
    OTHER(0x7Fu),
    SPEC_LAST(0xDFu),
    EXTENSION_FIRST(0xE0u),
    EXTENSION_LAST(0xEFu),
    VENDOR_FIRST(0xF0u),
    VENDOR_LAST(0xFFu),
}

enum class CTAPOptions(val value: String) {
    AUTHENTICATOR_CONFIG("authnrCfg"),
    CREDENTIALS_MANAGEMENT("credMgmt"),
    CREDENTIALS_MANAGEMENT_PREVIEW("credentialMgmtPreview"),
}

enum class CTAPPinPermissions(val value: UByte) {
    MAKE_CREDENTIAL(0x01u),
    GET_ASSERTION(0x02u),
    CREDENTIAL_MANAGEMENT(0x04u),
    BIO_ENROLLMENT(0x08u),
    LARGE_BLOB_WRITE(0x10u),
    AUTHENTICATOR_CONFIGURATION(0x20u),
}

class CTAPError(status: UByte) : RuntimeException(
    "CTAP error: ${CTAPResponse.entries.find { it.value == status } ?: "unknown"}",
)

@OptIn(ExperimentalStdlibApi::class)
class CTAPClient(private val device: Device) {

    private val PP_2_AES_INFO = "CTAP2 AES key".encodeToByteArray()
    private val PP_2_HMAC_INFO = "CTAP2 HMAC key".encodeToByteArray()

    private var platformKey: KeyAgreementPlatformKey? = null
    private var pinProtocolInUse: UByte = 0u
    private var info: GetInfoResponse? = null

    private fun checkResponseStatus(response: ByteArray): ByteArray {
        if (response.isEmpty()) {
            throw RuntimeException("Empty response!")
        }
        val status = response[0]
        if (status.toUByte() != CTAPResponse.OK.value) {
            throw CTAPError(status.toUByte())
        }
        return response.copyOfRange(1, response.size)
    }

    internal fun xmit(command: CtapCommand): ByteArray {
        val cbor = command.getCBOR()
        Logger.v { "Sending command: ${cbor.toHexString()}" }
        val rawResult = device.sendBytes(cbor)
        Logger.v { "Command $command raw response ${rawResult.toHexString()}" }
        val result = checkResponseStatus(rawResult)
        return result
    }

    internal fun <T> xmit(command: CtapCommand, deserializationStrategy: DeserializationStrategy<T>): T {
        val result = xmit(command)

        return CTAPCBORDecoder(result).decodeSerializableValue(
            deserializationStrategy,
        )
    }

    internal fun getInfoIfUnset(): GetInfoResponse {
        val info = info
        if (info != null) {
            return info
        }
        return getInfo()
    }

    fun getInfo(): GetInfoResponse {
        val ret = xmit(GetInfoCommand(), GetInfoResponse.serializer())
        info = ret
        Logger.d { "Device info: $ret" }
        return ret
    }

    fun authenticatorReset() {
        val ret = device.sendBytes(ResetCommand().getCBOR())
        checkResponseStatus(ret)
    }

    fun makeCredential(clientDataHash: ByteArray, rpId: String): MakeCredentialResponse {
        val request = MakeCredentialCommand(
            clientDataHash,
            PublicKeyCredentialRpEntity(rpId),
            user = PublicKeyCredentialUserEntity(
                id = Random.nextBytes(32),
                displayName = "Bob",
            ),
            pubKeyCredParams = listOf(
                PublicKeyCredentialParameters(
                    alg = COSEAlgorithmIdentifier.ES256,
                ),
            ),
            options = mapOf(
                CredentialCreationOption.RK to true,
            ),
        )
        return xmit(request, MakeCredentialResponse.serializer())
    }

    fun getKeyAgreement(pinProtocol: UByte = 2u): KeyAgreementPlatformKey {
        require(pinProtocol == 1u.toUByte() || pinProtocol == 2u.toUByte())
        val crypto = Library.cryptoProvider ?: throw RuntimeException("Library not initialized")

        val decoded = xmit(
            ClientPinCommand.getKeyAgreement(pinProtocol),
            ClientPinGetKeyAgreementResponse.serializer(),
        )

        val otherPublic = P256Point(decoded.key.x, decoded.key.y)
        val state = crypto.ecdhKeyAgreementInit(otherPublic)
        try {
            val pp1Key = crypto.ecdhKeyAgreementKDF(
                state,
                otherPublic,
                false,
                byteArrayOf(),
                byteArrayOf(),
            ).bytes
            val pp2AES = crypto.ecdhKeyAgreementKDF(
                state,
                otherPublic,
                true,
                ByteArray(32) { 0x00 },
                PP_2_AES_INFO,
            ).bytes
            val pp2HMAC = crypto.ecdhKeyAgreementKDF(
                state,
                otherPublic,
                true,
                ByteArray(32) { 0x00 },
                PP_2_HMAC_INFO,
            ).bytes
            val key = KeyAgreementPlatformKey(
                state.localPublicX,
                state.localPublicY,
                pinProtocol1Key = pp1Key,
                pinProtocol2HMACKey = pp2HMAC,
                pinProtocol2AESKey = pp2AES,
            )
            platformKey = key
            return key
        } finally {
            crypto.ecdhKeyAgreementDestroy(state)
        }
    }

    private fun pkToCOSE(pk: KeyAgreementPlatformKey): COSEKey {
        return COSEKey(
            kty = 2,
            alg = -25,
            crv = 1,
            x = pk.publicX,
            y = pk.publicY,
        )
    }

    private fun checkAndPadPIN(pinUnicode: String): ByteArray {
        val pinByteList = pinUnicode.encodeToByteArray().toMutableList()
        if (pinByteList.size > 63) {
            throw IllegalArgumentException("PIN too long - maximum is 63 bytes")
        }
        while (pinByteList.size < 64) {
            pinByteList.add(0x00)
        }
        return pinByteList.toByteArray()
    }

    fun setPIN(newPinUnicode: String, pinProtocol: UByte? = null) {
        val pp = getPinProtocol(pinProtocol)
        val pk = ensurePlatformKey(pp)

        val newPINBytes = checkAndPadPIN(newPinUnicode)
        val newPinEnc = pp.encrypt(pk, newPINBytes)
        val pinHashEnc = pp.authenticate(pk, newPinEnc)

        val command = ClientPinCommand.setPIN(
            pinUvAuthProtocol = pp.getVersion(),
            keyAgreement = pkToCOSE(pk),
            newPinEnc = newPinEnc,
            pinUvAuthParam = pinHashEnc,
        )

        val res = device.sendBytes(command.getCBOR())
        checkResponseStatus(res)

        platformKey = null // After setting the PIN, re-get the PIN token etc
    }

    internal fun getPinProtocol(pinProtocol: UByte?): PinProtocol {
        return when (pinProtocol?.toUInt()) {
            1u -> PinProtocolV1()
            2u -> PinProtocolV2()
            null -> {
                val supportedProtocols = getInfoIfUnset().pinUvAuthProtocols
                if (supportedProtocols != null && supportedProtocols.contains(2u)) {
                    return PinProtocolV2()
                }
                PinProtocolV1()
            }
            else -> throw IllegalArgumentException("Unsupported PIN protocol $pinProtocol")
        }
    }

    fun changePIN(currentPinUnicode: String, newPinUnicode: String, pinProtocol: UByte? = null) {
        val pp = getPinProtocol(pinProtocol)
        val pk = ensurePlatformKey(pp)

        val newPINBytes = checkAndPadPIN(newPinUnicode)
        val newPinEnc = pp.encrypt(pk, newPINBytes)

        val crypto = Library.cryptoProvider ?: throw IllegalStateException("Library not initialized")
        val left16 = crypto.sha256(currentPinUnicode.encodeToByteArray()).hash.copyOfRange(0, 16)
        val pinHashEnc = pp.encrypt(pk, left16)

        val authBlob = (newPinEnc.toList() + pinHashEnc.toList()).toByteArray()

        val pinUvAuthParam = pp.authenticate(pk, authBlob)

        val command = ClientPinCommand.changePIN(
            pinHashEnc = pinHashEnc,
            pinUvAuthProtocol = pp.getVersion(),
            keyAgreement = pkToCOSE(pk),
            newPinEnc = newPinEnc,
            pinUvAuthParam = pinUvAuthParam,
        )

        val res = device.sendBytes(command.getCBOR())
        checkResponseStatus(res)

        platformKey = null // After changing the PIN, re-get the PIN token etc
    }

    internal fun ensurePlatformKey(pp: PinProtocol): KeyAgreementPlatformKey {
        var pk = platformKey
        if (pk == null || pinProtocolInUse != pp.getVersion()) {
            pk = getKeyAgreement(pp.getVersion())
            pinProtocolInUse = pp.getVersion()
        }
        return pk
    }

    fun getPinToken(currentPinUnicode: String, pinProtocol: UByte? = null): PinToken {
        val pp = getPinProtocol(pinProtocol)
        val pk = ensurePlatformKey(pp)

        val crypto = Library.cryptoProvider ?: throw IllegalStateException("Library not initialized")
        val left16 = crypto.sha256(currentPinUnicode.encodeToByteArray()).hash.copyOfRange(0, 16)
        val pinHashEnc = pp.encrypt(pk, left16)

        val command = ClientPinCommand.getPinToken(
            pinUvAuthProtocol = pp.getVersion(),
            keyAgreement = pkToCOSE(pk),
            pinHashEnc = pinHashEnc,
        )

        val ret = xmit(command, ClientPinGetTokenResponse.serializer())

        Logger.d { "Got PIN token: $ret" }

        return PinToken(pp.decrypt(pk, ret.pinUvAuthToken))
    }

    fun getPinTokenUsingPin(
        currentPinUnicode: String,
        pinProtocol: UByte? = null,
        permissions: UByte,
        rpId: String? = null,
    ): PinToken {
        val pp = getPinProtocol(pinProtocol)
        val pk = ensurePlatformKey(pp)

        val crypto = Library.cryptoProvider ?: throw IllegalStateException("Library not initialized")
        val left16 = crypto.sha256(currentPinUnicode.encodeToByteArray()).hash.copyOfRange(0, 16)
        val pinHashEnc = pp.encrypt(pk, left16)

        val command = ClientPinCommand.getPinUvAuthTokenUsingPinWithPermissions(
            pinUvAuthProtocol = pp.getVersion(),
            keyAgreement = pkToCOSE(pk),
            pinHashEnc = pinHashEnc,
            permissions = permissions,
            rpId = rpId,
        )

        val ret = xmit(command, ClientPinGetTokenResponse.serializer())

        Logger.d { "Got PIN token: $ret" }

        return PinToken(pp.decrypt(pk, ret.pinUvAuthToken))
    }

    fun getPINRetries(pinProtocol: UByte? = null): ClientPinGetRetriesResponse {
        val pp = getPinProtocol(pinProtocol)
        val command = ClientPinCommand.getPINRetries(pinUvAuthProtocol = pp.getVersion())
        return xmit(command, ClientPinGetRetriesResponse.serializer())
    }

    fun authenticatorConfig(): AuthenticatorConfigClient {
        val info = getInfoIfUnset()
        if (info.options?.get(CTAPOptions.AUTHENTICATOR_CONFIG.value) != true) {
            throw IllegalStateException("Authenticator config commands not supported on $device")
        }
        return AuthenticatorConfigClient(this)
    }

    fun credentialManagement(): CredentialManagementClient {
        val info = getInfoIfUnset()
        val fullySupported = info.options?.get(CTAPOptions.CREDENTIALS_MANAGEMENT.value) == true
        val prototypeSupported = info.options?.get(CTAPOptions.CREDENTIALS_MANAGEMENT_PREVIEW.value) == true
        if (!fullySupported && !prototypeSupported) {
            throw IllegalStateException("Credential management commands not supported on $device")
        }
        return CredentialManagementClient(this)
    }
}

data class PinToken(val token: ByteArray) {
    init {
        require(token.size == 32)
    }
}
