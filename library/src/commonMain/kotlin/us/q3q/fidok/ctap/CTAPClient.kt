package us.q3q.fidok.ctap

import co.touchlab.kermit.Logger
import kotlinx.serialization.DeserializationStrategy
import us.q3q.fidok.crypto.KeyAgreementPlatformKey
import us.q3q.fidok.crypto.P256Point
import us.q3q.fidok.crypto.PinUVProtocol
import us.q3q.fidok.crypto.PinUVProtocolV1
import us.q3q.fidok.crypto.PinUVProtocolV2
import us.q3q.fidok.ctap.commands.AttestationTypes
import us.q3q.fidok.ctap.commands.COSEAlgorithmIdentifier
import us.q3q.fidok.ctap.commands.COSEKey
import us.q3q.fidok.ctap.commands.CTAPCBORDecoder
import us.q3q.fidok.ctap.commands.ClientPinCommand
import us.q3q.fidok.ctap.commands.ClientPinGetKeyAgreementResponse
import us.q3q.fidok.ctap.commands.ClientPinGetRetriesResponse
import us.q3q.fidok.ctap.commands.ClientPinGetTokenResponse
import us.q3q.fidok.ctap.commands.ClientPinUvRetriesResponse
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
    OPERATION_DENIED(0x27u),
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
    PLATFORM_AUTHENTICATOR("plat"),
    DISCOVERABLE_CREDENTIALS("rk"),
    CLIENT_PIN("clientPin"),
    USER_PRESENCE("up"),
    INTERNAL_USER_VERIFICATION("uv"),
    PIN_UV_AUTH_TOKEN("pinUvAuthToken"),
    NO_MC_GA_PERMISSIONS_WITH_CLIENT_PIN("noMcGaPermissionsWithClientPin"),
    LARGE_BLOBS("largeBlobs"),
    ENTERPRISE_ATTESTATION("ep"),
    BIO_ENROLLMENT("bioEnroll"),
    USER_VERIFICATION_MANAGEMENT_PREVIEW("userVerificationMgmtPreview"),
    USER_VERIFICATION_BIO_ENROLLMENT("uvBioEnroll"),
    AUTHENTICATOR_CONFIG("authnrCfg"),
    USER_VERIFICATION_AUTHENTICATION_CONFIG_PERMISSION("uvAcfg"),
    CREDENTIALS_MANAGEMENT("credMgmt"),
    CREDENTIALS_MANAGEMENT_PREVIEW("credentialMgmtPreview"),
    SET_MIN_PIN_LENGTH("setMinPINLength"),
    MAKE_CREDENTIAL_UV_NOT_REQUIRED("makeCredUvNotRqd"),
    ALWAYS_UV("alwaysUv"),
}

enum class CTAPPinPermissions(val value: UByte) {
    MAKE_CREDENTIAL(0x01u),
    GET_ASSERTION(0x02u),
    CREDENTIAL_MANAGEMENT(0x04u),
    BIO_ENROLLMENT(0x08u),
    LARGE_BLOB_WRITE(0x10u),
    AUTHENTICATOR_CONFIGURATION(0x20u),
}

open class CTAPError(val code: UByte, message: String? = null) : RuntimeException(
    message ?: "CTAP error: ${CTAPResponse.entries.find { it.value == code } ?: "unknown ($code)"}",
)

class PermissionDeniedError(message: String) : CTAPError(CTAPResponse.OPERATION_DENIED.value, message)

class InvalidAttestationError : IncorrectDataException(
    "Attestation failed to validate",
)

class InvalidSignatureError : IncorrectDataException(
    "Assertion signature failed to validate",
)

private const val FIDO_2_1 = "FIDO_2_1"
private const val FIDO_2_0 = "FIDO_2_0"

@OptIn(ExperimentalStdlibApi::class)
class CTAPClient(
    private val library: FIDOkLibrary,
    private val device: AuthenticatorDevice,
    private val collectPinFromUser: () -> String? = { null },
) {

    private val PP_2_AES_INFO = "CTAP2 AES key".encodeToByteArray()
    private val PP_2_HMAC_INFO = "CTAP2 HMAC key".encodeToByteArray()
    private val DEFAULT_CREDENTIAL_ALGORITHMS = listOf(
        PublicKeyCredentialParameters(alg = COSEAlgorithmIdentifier.ES256),
    )

    private var platformKey: KeyAgreementPlatformKey? = null
    private var pinUvProtocolInUse: UByte = 0u
    private var info: GetInfoResponse? = null

    private var cachedPin: String? = null

    private fun checkResponseStatus(response: ByteArray): ByteArray {
        if (response.isEmpty()) {
            throw IncorrectDataException("Empty response!")
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
        pinUvProtocol: UByte? = null,
        pinUvToken: PinUVToken? = null,
        enterpiseAttestation: UInt? = null,
        extensions: ExtensionSetup? = null,
        validateAttestation: Boolean = true,
    ): MakeCredentialResponse {
        require(clientDataHash == null || clientDataHash.size == 32)
        require(enterpiseAttestation == null || enterpiseAttestation == 1u || enterpiseAttestation == 2u)
        require(
            (pinUvToken == null && pinUvProtocol == null) ||
                pinUvToken != null,
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

        var pinUvProtocolVersion: UByte? = null
        val pinUvAuthParam = if (pinUvToken != null) {
            val pp = getPinProtocol(pinUvProtocol)
            pinUvProtocolVersion = pp.getVersion()

            Logger.d { "Authenticating request using PIN protocol $pinUvProtocolVersion" }

            pp.authenticate(pinUvToken, effectiveClientDataHash)
        } else {
            null
        }

        var ka: KeyAgreementPlatformKey? = null
        var pp: PinUVProtocol? = null
        if (extensions?.isKeyAgreementRequired() == true) {
            pp = getPinProtocol(pinUvProtocol)
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
            pinUvAuthProtocol = pinUvProtocolVersion,
            pinUvAuthParam = pinUvAuthParam,
            enterpriseAttestation = enterpiseAttestation,
            extensions = extensions?.makeCredential(ka, pp),
        )
        val ret = xmit(request, MakeCredentialResponse.serializer())

        if (validateAttestation) {
            validateCredentialAttestation(ret, effectiveClientDataHash)
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
        val signedBytes = (makeCredentialResponse.authData.rawBytes.toList() + clientDataHash.toList()).toByteArray()

        Logger.v { "Signature-verifying ${signedBytes.size} bytes" }

        if (!library.cryptoProvider.es256SignatureValidate(
                signedBytes = signedBytes,
                key = P256Point(keyX, keyY),
                sig = sig,
            )
        ) {
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

        val pubKey = makeCredentialResponse.getCredentialPublicKey()

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

        val x509Info = library.cryptoProvider.parseES256X509(caCert)

        validateES256UsingPubKey(makeCredentialResponse, clientDataHash, x509Info.publicX, x509Info.publicY, sig)
    }

    fun validateCredentialAttestation(makeCredentialResponse: MakeCredentialResponse, clientDataHash: ByteArray) {
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

    fun validateAssertionSignature(getAssertionResponse: GetAssertionResponse, clientDataHash: ByteArray, publicKey: COSEKey) {
        if (publicKey.alg != COSEAlgorithmIdentifier.ES256.value) {
            throw NotImplementedError("Only ES256 signatures are currently implemented")
        }

        val signedBytes = (getAssertionResponse.authData.rawBytes.toList() + clientDataHash.toList()).toByteArray()

        Logger.v { "Signature-verifying ${signedBytes.size} bytes" }

        if (!library.cryptoProvider.es256SignatureValidate(
                signedBytes = signedBytes,
                P256Point(publicKey.x, publicKey.y),
                sig = getAssertionResponse.signature,
            )
        ) {
            throw InvalidSignatureError()
        }
    }

    fun getAssertions(
        clientDataHash: ByteArray,
        rpId: String,
        allowList: List<PublicKeyCredentialDescriptor>? = null,
        extensions: ExtensionSetup? = null,
        userPresence: Boolean? = null,
        pinUvProtocol: UByte? = null,
        pinUvToken: PinUVToken? = null,
    ): List<GetAssertionResponse> {
        require(clientDataHash.size == 32)
        require(
            (pinUvToken == null && pinUvProtocol == null) ||
                pinUvToken != null,
        )

        val info = getInfoIfUnset()
        if (extensions?.checkSupport(info) == false) {
            throw IllegalArgumentException("Authenticator does not support requested extension(s)")
        }

        var pinUvProtocolVersion: UByte? = null
        val pinUvAuthParam = if (pinUvToken != null) {
            val pp = getPinProtocol(pinUvProtocol)
            pinUvProtocolVersion = pp.getVersion()
            pp.authenticate(pinUvToken, clientDataHash)
        } else {
            null
        }

        var ka: KeyAgreementPlatformKey? = null
        var pp: PinUVProtocol? = null
        if (extensions?.isKeyAgreementRequired() == true) {
            pp = getPinProtocol(pinUvProtocol)
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
                extensions = extensions?.getAssertion(keyAgreement = ka, pinUVProtocol = pp),
                options = options,
                pinUvAuthParam = pinUvAuthParam,
                pinUvAuthProtocol = pinUvProtocolVersion,
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

    fun getKeyAgreement(pinUvProtocol: UByte = 2u): KeyAgreementPlatformKey {
        require(pinUvProtocol == 1u.toUByte() || pinUvProtocol == 2u.toUByte())

        val crypto = library.cryptoProvider

        val decoded = xmit(
            ClientPinCommand.getKeyAgreement(pinUvProtocol),
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
                pinUvProtocol1Key = pp1Key,
                pinUvProtocol2HMACKey = pp2HMAC,
                pinUvProtocol2AESKey = pp2AES,
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

    fun setPIN(newPinUnicode: String, pinUvProtocol: UByte? = null) {
        val pp = getPinProtocol(pinUvProtocol)
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

    internal fun getPinProtocol(pinUvProtocol: UByte?): PinUVProtocol {
        val supportedProtocols = getInfoIfUnset().pinUvAuthProtocols
        return when (pinUvProtocol?.toUInt()) {
            1u -> {
                if (supportedProtocols != null && !supportedProtocols.contains(1u)) {
                    throw IllegalArgumentException("Authenticator doesn't support PIN protocol one")
                }
                PinUVProtocolV1(library.cryptoProvider)
            }
            2u -> {
                if (supportedProtocols?.contains(2u) != true) {
                    throw IllegalArgumentException("Authenticator doesn't support PIN protocol two")
                }
                PinUVProtocolV2(library.cryptoProvider)
            }
            null -> {
                if (supportedProtocols?.contains(2u) == true) {
                    return PinUVProtocolV2(library.cryptoProvider)
                }
                PinUVProtocolV1(library.cryptoProvider)
            }
            else -> throw IllegalArgumentException("Unsupported PIN protocol $pinUvProtocol")
        }
    }

    @Throws(CTAPError::class, DeviceCommunicationException::class)
    fun changePIN(currentPinUnicode: String, newPinUnicode: String, pinUvProtocol: UByte? = null) {
        val pp = getPinProtocol(pinUvProtocol)
        val pk = ensurePlatformKey(pp)

        val newPINBytes = checkAndPadPIN(newPinUnicode)
        val newPinEnc = pp.encrypt(pk, newPINBytes)

        val left16 = library.cryptoProvider.sha256(currentPinUnicode.encodeToByteArray()).hash.copyOfRange(0, 16)
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

    private fun ensurePlatformKey(pp: PinUVProtocol): KeyAgreementPlatformKey {
        var pk = platformKey
        if (pk == null || pinUvProtocolInUse != pp.getVersion()) {
            pk = getKeyAgreement(pp.getVersion())
            pinUvProtocolInUse = pp.getVersion()
        }
        return pk
    }

    @Throws(PinNotAvailableException::class, CTAPError::class, DeviceCommunicationException::class)
    fun getPinUVTokenUsingAppropriateMethod(
        desiredPermissions: UByte,
        desiredRpId: String? = null,
        pinUvProtocol: UByte? = null,
    ): PinUVToken {
        val info = getInfoIfUnset()
        if (info.options?.get(CTAPOptions.PIN_UV_AUTH_TOKEN.value) == true) {
            // supports permissions

            if (info.options[CTAPOptions.INTERNAL_USER_VERIFICATION.value] == true) {
                // try UV first (with permissions)

                val remainingTries = getUvRetries().uvRetries

                var retries = info.preferredPlatformUvAttempts ?: 1u
                if (retries > remainingTries) {
                    // Not trying this if the remaining
                    retries = remainingTries
                }

                for (i in 1..retries.toInt()) {
                    Logger.i { "Authenticator $device supports onboard UV: trying it, attempt $i of $retries" }

                    try {
                        return getPinTokenUsingUv(
                            pinUvProtocol = pinUvProtocol,
                            permissions = desiredPermissions,
                            rpId = desiredRpId,
                        )
                    } catch (e: CTAPError) {
                        Logger.w { "Attempt $i at UV with $device failed: $e" }

                        if (e.code == CTAPResponse.USER_ACTION_TIMEOUT.value) {
                            // let's go around and try UV again
                            continue
                        }
                        if (e.code == CTAPResponse.OPERATION_DENIED.value) {
                            // this just means we need to try again with the PIN
                            break
                        }
                        if (e.code == CTAPResponse.UV_BLOCKED.value) {
                            // Out of UV retries... fall back to the PIN if available
                            break
                        }

                        // This threw a "real" exception
                        throw e
                    }
                }
            }

            if (info.options[CTAPOptions.CLIENT_PIN.value] != true) {
                // we want to try a PIN, but it's not set :-(
                throw PinNotAvailableException()
            }

            if (desiredPermissions.toUInt() and
                (
                    CTAPPinPermissions.MAKE_CREDENTIAL.value.toUInt()
                        or CTAPPinPermissions.GET_ASSERTION.value.toUInt()
                    ) != 0u
            ) {
                // we're asking for MC or GA, but the authenticator doesn't allow those to work
                if (info.options[CTAPOptions.NO_MC_GA_PERMISSIONS_WITH_CLIENT_PIN.value] == true) {
                    // but the authenticator says that's not allowed with a PIN, only with UV
                    throw PermissionDeniedError(
                        "Using a PIN for making credentials or getting assertions" +
                            " is prohibited by the authenticator options",
                    )
                }
            }

            // try PIN (with permissions)
            val pin = (if (cachedPin != null) cachedPin else collectPinFromUser())
                ?: throw PinNotAvailableException()
            cachedPin = pin

            return getPinTokenWithPermissions(
                currentPinUnicode = pin,
                pinUvProtocol = pinUvProtocol,
                permissions = desiredPermissions,
                rpId = desiredRpId,
            )
        }

        // if we're here, we don't support permissions
        Logger.i { "Authenticator $device does not support permissions, so using CTAP2.0 getPinToken method" }

        val pin = (if (cachedPin != null) cachedPin else collectPinFromUser())
            ?: throw PinNotAvailableException()
        cachedPin = pin

        return getPinToken(
            currentPinUnicode = pin,
            pinUvProtocol = pinUvProtocol,
        )
    }

    fun getPinTokenUsingUv(
        pinUvProtocol: UByte? = null,
        permissions: UByte,
        rpId: String? = null,
    ): PinUVToken {
        val pp = getPinProtocol(pinUvProtocol)
        val pk = ensurePlatformKey(pp)

        val command = ClientPinCommand.getPinUvAuthTokenUsingUvWithPermissions(
            pinUvAuthProtocol = pp.getVersion(),
            keyAgreement = pk.getCOSE(),
            permissions = permissions,
            rpId = rpId,
        )

        val ret = xmit(command, ClientPinGetTokenResponse.serializer())

        Logger.d { "Got PIN token: $ret" }

        return PinUVToken(pp.decrypt(pk, ret.pinUvAuthToken))
    }

    fun getUvRetries(): ClientPinUvRetriesResponse {
        val command = ClientPinCommand.getUVRetries()

        val ret = xmit(command, ClientPinUvRetriesResponse.serializer())

        Logger.d { "Remaining UV retries: $ret" }

        return ret
    }

    fun getPinToken(currentPinUnicode: String, pinUvProtocol: UByte? = null): PinUVToken {
        val pp = getPinProtocol(pinUvProtocol)
        val pk = ensurePlatformKey(pp)

        val left16 = library.cryptoProvider.sha256(currentPinUnicode.encodeToByteArray()).hash.copyOfRange(0, 16)
        val pinHashEnc = pp.encrypt(pk, left16)

        val command = ClientPinCommand.getPinToken(
            pinUvAuthProtocol = pp.getVersion(),
            keyAgreement = pk.getCOSE(),
            pinHashEnc = pinHashEnc,
        )

        val ret = xmit(command, ClientPinGetTokenResponse.serializer())

        Logger.d { "Got PIN token: $ret" }

        return PinUVToken(pp.decrypt(pk, ret.pinUvAuthToken))
    }

    fun getPinTokenWithPermissions(
        currentPinUnicode: String,
        pinUvProtocol: UByte? = null,
        permissions: UByte,
        rpId: String? = null,
    ): PinUVToken {
        val pp = getPinProtocol(pinUvProtocol)
        val pk = ensurePlatformKey(pp)

        val left16 = library.cryptoProvider.sha256(currentPinUnicode.encodeToByteArray()).hash.copyOfRange(0, 16)
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

        return PinUVToken(pp.decrypt(pk, ret.pinUvAuthToken))
    }

    fun getPINRetries(): ClientPinGetRetriesResponse {
        val command = ClientPinCommand.getPINRetries()
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

class PinNotAvailableException : Exception()

data class PinUVToken(val token: ByteArray) {
    init {
        require(token.size == 32)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as PinUVToken

        return token.contentEquals(other.token)
    }

    override fun hashCode(): Int {
        return token.contentHashCode()
    }
}
