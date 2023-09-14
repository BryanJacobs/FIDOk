package us.q3q.fidok.ctap

import co.touchlab.kermit.Logger
import kotlinx.serialization.DeserializationStrategy
import us.q3q.fidok.crypto.KeyAgreementPlatformKey
import us.q3q.fidok.crypto.P256Point
import us.q3q.fidok.crypto.PinProtocol
import us.q3q.fidok.crypto.PinProtocolV1
import us.q3q.fidok.crypto.PinProtocolV2
import us.q3q.fidok.ctap.commands.AttestationTypes
import us.q3q.fidok.ctap.commands.COSEAlgorithmIdentifier
import us.q3q.fidok.ctap.commands.CTAPCBORDecoder
import us.q3q.fidok.ctap.commands.ClientPinCommand
import us.q3q.fidok.ctap.commands.ClientPinGetKeyAgreementResponse
import us.q3q.fidok.ctap.commands.ClientPinGetRetriesResponse
import us.q3q.fidok.ctap.commands.ClientPinGetTokenResponse
import us.q3q.fidok.ctap.commands.CredentialCreationOption
import us.q3q.fidok.ctap.commands.CtapCommand
import us.q3q.fidok.ctap.commands.ExtensionSetup
import us.q3q.fidok.ctap.commands.GetAssertionCommand
import us.q3q.fidok.ctap.commands.GetAssertionOption
import us.q3q.fidok.ctap.commands.GetAssertionResponse
import us.q3q.fidok.ctap.commands.GetInfoCommand
import us.q3q.fidok.ctap.commands.GetInfoResponse
import us.q3q.fidok.ctap.commands.GetNextAssertionCommand
import us.q3q.fidok.ctap.commands.MakeCredentialCommand
import us.q3q.fidok.ctap.commands.MakeCredentialResponse
import us.q3q.fidok.ctap.commands.PublicKeyCredentialDescriptor
import us.q3q.fidok.ctap.commands.PublicKeyCredentialParameters
import us.q3q.fidok.ctap.commands.PublicKeyCredentialRpEntity
import us.q3q.fidok.ctap.commands.PublicKeyCredentialUserEntity
import us.q3q.fidok.ctap.commands.ResetCommand
import kotlin.math.min
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
    "CTAP error: ${CTAPResponse.entries.find { it.value == status } ?: "unknown ($status)"}",
)

class InvalidAttestationError : RuntimeException(
    "Attestation failed to validate",
)

@OptIn(ExperimentalStdlibApi::class)
class CTAPClient(private val device: Device) {

    private val PP_2_AES_INFO = "CTAP2 AES key".encodeToByteArray()
    private val PP_2_HMAC_INFO = "CTAP2 HMAC key".encodeToByteArray()
    private val DEFAULT_CREDENTIAL_ALGORITHMS = listOf(
        PublicKeyCredentialParameters(alg = COSEAlgorithmIdentifier.ES256),
    )

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

    fun makeCredential(
        clientDataHash: ByteArray? = null,
        rpId: String,
        userId: ByteArray? = null,
        userDisplayName: String,
        discoverableCredential: Boolean = false,
        userVerification: Boolean = false,
        pubKeyCredParams: List<PublicKeyCredentialParameters> = DEFAULT_CREDENTIAL_ALGORITHMS,
        excludeList: List<PublicKeyCredentialDescriptor>? = null,
        pinProtocol: UByte? = null,
        pinToken: PinToken? = null,
        enterpiseAttestation: UInt? = null,
        extensions: ExtensionSetup? = null,
        validateAttestation: Boolean = true,
    ): MakeCredentialResponse {
        require(clientDataHash == null || clientDataHash.size == 32)
        require(userId == null || userId.size == 32)
        require(enterpiseAttestation == null || enterpiseAttestation == 1u || enterpiseAttestation == 2u)
        require(
            (pinToken == null && pinProtocol == null) ||
                pinToken != null,
        )
        val info = getInfoIfUnset()
        if (enterpiseAttestation != null && info.options?.get("ep") != true) {
            throw IllegalArgumentException("Authenticator enterprise attestation isn't enabled")
        }
        if (extensions?.checkSupport(info) == false) {
            throw IllegalArgumentException("Authenticator does not support requested extension(s)")
        }

        val effectiveUserId = userId ?: Random.nextBytes(32)
        val effectiveClientDataHash = clientDataHash ?: Random.nextBytes(32)

        val options = hashMapOf<CredentialCreationOption, Boolean>()
        if (discoverableCredential) {
            if (info.options?.containsKey("rk") != true) {
                throw IllegalArgumentException("Authenticator does not support discoverable credentials")
            }
            options[CredentialCreationOption.RK] = true
        }
        if (userVerification) {
            options[CredentialCreationOption.UV] = true
        }

        Logger.d { "Making credential with effective client data hash ${effectiveClientDataHash.toHexString()}" }

        var pinProtocolVersion: UByte? = null
        val pinUvAuthParam = if (pinToken != null) {
            val pp = getPinProtocol(pinProtocol)
            pinProtocolVersion = pp.getVersion()
            pp.authenticate(pinToken, effectiveClientDataHash)
        } else {
            null
        }

        var ka: KeyAgreementPlatformKey? = null
        var pp: PinProtocol? = null
        if (extensions?.isKeyAgreementRequired() == true) {
            pp = getPinProtocol(pinProtocol)
            ka = getKeyAgreement(pp.getVersion())
        }

        val request = MakeCredentialCommand(
            effectiveClientDataHash,
            PublicKeyCredentialRpEntity(rpId),
            user = PublicKeyCredentialUserEntity(
                id = effectiveUserId,
                displayName = userDisplayName,
            ),
            pubKeyCredParams = pubKeyCredParams,
            options = options,
            excludeList = excludeList,
            pinUvAuthProtocol = pinProtocolVersion,
            pinUvAuthParam = pinUvAuthParam,
            enterpriseAttestation = enterpiseAttestation,
            extensions = extensions?.makeCredential(ka, pp),
        )
        val ret = xmit(request, MakeCredentialResponse.serializer())

        if (validateAttestation) {
            validateCredentialSignature(ret, effectiveClientDataHash)
        }

        extensions?.makeCredentialResponse(ret)

        return ret
    }

    private fun validateES256UsingPubKey(
        makeCredentialResponse: MakeCredentialResponse,
        clientDataHash: ByteArray,
        keyX: ByteArray,
        keyY: ByteArray,
        sig: ByteArray,
    ) {
        val crypto = Library.cryptoProvider ?: throw RuntimeException("Library not initialized")

        val signedBytes = (makeCredentialResponse.authData.rawBytes.toList() + clientDataHash.toList()).toByteArray()

        Logger.v { "Signature-verifying ${signedBytes.size} bytes" }

        if (!crypto.es256SignatureValidate(signedBytes = signedBytes, keyX = keyX, keyY = keyY, sig = sig)) {
            throw InvalidAttestationError()
        }
    }

    fun validateSelfAttestation(
        makeCredentialResponse: MakeCredentialResponse,
        clientDataHash: ByteArray,
        alg: Int,
        sig: ByteArray,
    ) {
        if (alg != COSEAlgorithmIdentifier.ES256.value) {
            // Unsupported, presently
            Logger.w { "Skipping signature validation for unsupported algorithm $alg" }
            return
        }

        val pubKey = makeCredentialResponse.authData.attestedCredentialData!!.credentialPublicKey

        validateES256UsingPubKey(makeCredentialResponse, clientDataHash, pubKey.x, pubKey.y, sig)
    }

    private fun validateBasicAttestation(
        makeCredentialResponse: MakeCredentialResponse,
        clientDataHash: ByteArray,
        alg: Int,
        sig: ByteArray,
        caCert: ByteArray,
    ) {
        if (alg != COSEAlgorithmIdentifier.ES256.value) {
            // Unsupported, presently
            Logger.w { "Skipping signature validation for unsupported algorithm $alg" }
            return
        }

        val crypto = Library.cryptoProvider ?: throw RuntimeException("Library not initialized")

        val x509Info = crypto.parseES256X509(caCert)

        validateES256UsingPubKey(makeCredentialResponse, clientDataHash, x509Info.publicX, x509Info.publicY, sig)
    }

    fun validateCredentialSignature(makeCredentialResponse: MakeCredentialResponse, clientDataHash: ByteArray) {
        when (makeCredentialResponse.fmt) {
            AttestationTypes.PACKED.value -> {
                val alg = makeCredentialResponse.attStmt["alg"] as Int
                val sig = makeCredentialResponse.attStmt["sig"] as ByteArray
                val x5c = makeCredentialResponse.attStmt["x5c"] as Array<*>?
                if (x5c == null) {
                    validateSelfAttestation(makeCredentialResponse, clientDataHash, alg, sig)
                } else {
                    validateBasicAttestation(makeCredentialResponse, clientDataHash, alg, sig, x5c[0] as ByteArray)
                }
            }
            AttestationTypes.NONE.value -> {
                // Nothing to do, here...
            }
            else -> {
                // we don't understand this attestation type, so ignore it and assume it's valid
                Logger.w { "Could not validate unsupported attestation type ${makeCredentialResponse.fmt}" }
            }
        }
    }

    fun getAssertions(
        clientDataHash: ByteArray,
        rpId: String,
        allowList: List<PublicKeyCredentialDescriptor>? = null,
        extensions: ExtensionSetup? = null,
        userPresence: Boolean? = null,
        pinProtocol: UByte? = null,
        pinToken: PinToken? = null,
    ): List<GetAssertionResponse> {
        require(clientDataHash.size == 32)
        require(
            (pinToken == null && pinProtocol == null) ||
                pinToken != null,
        )

        val info = getInfoIfUnset()
        if (extensions?.checkSupport(info) == false) {
            throw IllegalArgumentException("Authenticator does not support requested extension(s)")
        }

        var pinProtocolVersion: UByte? = null
        val pinUvAuthParam = if (pinToken != null) {
            val pp = getPinProtocol(pinProtocol)
            pinProtocolVersion = pp.getVersion()
            pp.authenticate(pinToken, clientDataHash)
        } else {
            null
        }

        var ka: KeyAgreementPlatformKey? = null
        var pp: PinProtocol? = null
        if (extensions?.isKeyAgreementRequired() == true) {
            pp = getPinProtocol(pinProtocol)
            ka = getKeyAgreement(pp.getVersion())
        }

        var options: HashMap<GetAssertionOption, Boolean>? = null
        if (userPresence == false) {
            options = hashMapOf(
                GetAssertionOption.UP to false,
            )
        }

        val ret = arrayListOf<GetAssertionResponse>()

        val numAllowListEntriesPerBatch = info.maxCredentialCountInList?.toInt() ?: 10000
        var allowListSent = 0
        while (allowListSent < (allowList?.size ?: 1)) {
            val thisRequestAllowList =
                allowList?.subList(allowListSent, min(allowListSent + numAllowListEntriesPerBatch, allowList.size))

            val request = GetAssertionCommand(
                clientDataHash = clientDataHash,
                rpId = rpId,
                allowList = thisRequestAllowList,
                extensions = extensions?.getAssertion(keyAgreement = ka, pinProtocol = pp),
                options = options,
                pinUvAuthParam = pinUvAuthParam,
                pinUvAuthProtocol = pinProtocolVersion,
            )

            val firstResponse = xmit(request, GetAssertionResponse.serializer())

            extensions?.getAssertionResponse(firstResponse)

            ret.add(firstResponse)

            val numberOfCredentials = firstResponse.numberOfCredentials ?: 1
            if (numberOfCredentials > 1) {
                for (i in 2..numberOfCredentials) {
                    val followUpRequest = GetNextAssertionCommand()
                    val laterResponse = xmit(followUpRequest, GetAssertionResponse.serializer())
                    ret.add(laterResponse)
                }
            }

            allowListSent += thisRequestAllowList?.size ?: 1
        }

        return ret
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
            keyAgreement = pk.getCOSE(),
            newPinEnc = newPinEnc,
            pinUvAuthParam = pinHashEnc,
        )

        val res = device.sendBytes(command.getCBOR())
        checkResponseStatus(res)

        platformKey = null // After setting the PIN, re-get the PIN token etc
    }

    internal fun getPinProtocol(pinProtocol: UByte?): PinProtocol {
        val supportedProtocols = getInfoIfUnset().pinUvAuthProtocols
        return when (pinProtocol?.toUInt()) {
            1u -> {
                if (supportedProtocols != null && !supportedProtocols.contains(1u)) {
                    throw IllegalArgumentException("Authenticator doesn't support PIN protocol one")
                }
                PinProtocolV1()
            }
            2u -> {
                if (supportedProtocols?.contains(2u) != true) {
                    throw IllegalArgumentException("Authenticator doesn't support PIN protocol two")
                }
                PinProtocolV2()
            }
            null -> {
                if (supportedProtocols?.contains(2u) == true) {
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
            keyAgreement = pk.getCOSE(),
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
            keyAgreement = pk.getCOSE(),
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
            keyAgreement = pk.getCOSE(),
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
