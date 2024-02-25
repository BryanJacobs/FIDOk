package us.q3q.fidok.ctap

import co.touchlab.kermit.Logger
import kotlinx.serialization.DeserializationStrategy
import us.q3q.fidok.crypto.KeyAgreementPlatformKey
import us.q3q.fidok.crypto.P256Point
import us.q3q.fidok.crypto.PinUVProtocol
import us.q3q.fidok.crypto.PinUVProtocolV1
import us.q3q.fidok.crypto.PinUVProtocolV2
import us.q3q.fidok.ctap.commands.AttestationTypes
import us.q3q.fidok.ctap.commands.AuthenticatorSelectionCommand
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
import us.q3q.fidok.ctap.commands.Examples
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
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min
import kotlin.random.Random

/**
 * Possible responses to a [CtapCommand] - one success, and many types of error
 *
 * @property value The CTAP assigned integer representing the response
 */
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

/**
 * Different options that may occur in a [CTAP GetInfo response][GetInfoResponse.options]
 *
 * @property value The string representing the option in the underlying CTAP exchange
 */
enum class CTAPOption(val value: String) {
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

/**
 * The different permissions that [may be requested][CTAPClient.getPinUvTokenUsingAppropriateMethod] from an
 * Authenticator.
 *
 * @property value The bitfield encoding of the permissions
 */
enum class CTAPPermission(val value: UByte) {
    /**
     * Allows creating new Credentials.
     */
    MAKE_CREDENTIAL(0x01u),

    /**
     * Allows getting Assertions.
     */
    GET_ASSERTION(0x02u),

    /**
     * Allows managing Discoverable Credentials.
     */
    CREDENTIAL_MANAGEMENT(0x04u),

    /**
     * Allows enrolling new biometric User Verification information.
     */
    BIO_ENROLLMENT(0x08u),

    /**
     * Allows writing to the Large Blob Store.
     */
    LARGE_BLOB_WRITE(0x10u),

    /**
     * Allows changing the Authenticator's own configuration.
     */
    AUTHENTICATOR_CONFIGURATION(0x20u),
}

/**
 * The known "levels" of enterprise attestation.
 *
 * @property value The CTAP value passed as [MakeCredentialCommand.enterpriseAttestation]
 */
enum class EnterpriseAttestationLevel(val value: UByte) {
    /**
     * The Authenticator will check the provided Relying Party ID against its own built-in list,
     * probably set by the Authenticator vendor.
     */
    VENDOR_FACILITATED(1u),

    /**
     * The Authenticator won't check the provided Relying Party ID, and will just check the Platform
     * has already done some kind of appropriate checking.
     */
    PLATFORM_MANAGED(2u),
}

/**
 * Represents a CTAP-level error.
 *
 * This means an Authenticator was successfully reached, but it returned an error code
 * instead of a success. This could be due to an invalid request, a problem with the
 * Authenticator itself, or a user action.
 *
 * @property code The CTAP error code, from [CTAPResponse]. Should not be [CTAPResponse.OK]
 * @property message A human-readable description of the error if one is known. If set to
 *                   null when creating the object, will be derived from the `code`
 */
open class CTAPError(val code: UByte, message: String? = null) : RuntimeException(
    message ?: "CTAP error: ${CTAPResponse.entries.find { it.value == code } ?: "unknown ($code)"}",
)

/**
 * A special case of [CTAPError] representing a permission denial.
 *
 * This may be sent by the Platform itself, instead of just the Authenticator.
 *
 * @property message A human-readable description of the reason permission was denied
 */
class PermissionDeniedError(message: String) : CTAPError(CTAPResponse.OPERATION_DENIED.value, message)

/**
 * Thrown when an attestation from the Authenticator doesn't match the content it's attesting
 * (usually a newly created credential), or when the attestation itself can't be validated
 */
class InvalidAttestationError : IncorrectDataException(
    "Attestation failed to validate",
)

/**
 * Thrown when the signature on an assertion cannot tbe validated
 */
class InvalidSignatureError : IncorrectDataException(
    "Assertion signature failed to validate",
)

/**
 * Thrown when a credential is not created due to a given [excludeList][MakeCredentialCommand.excludeList]
 */
class CredentialExcludedError : CTAPError(
    CTAPResponse.CREDENTIAL_EXCLUDED.value,
    "Credential was present on the exclude list",
)

/**
 * Core class for accessing the FIDOk CTAP layer.
 *
 * A CTAP client provides the ability to interact with a particular [AuthenticatorDevice],
 * and abstracts away the transport layer.
 *
 * A client is stateful, and only one should be used for a given device.
 *
 * To obtain a client, call [FIDOkLibrary.ctapClient].
 *
 * @param library Initialized FIDOk library instance
 * @param device The authenticator with which to communicate
 * @param collectPinFromUser A function for retrieving a PIN from the user when necessary
 *
 * @sample ctapClientExample
 */
@OptIn(ExperimentalStdlibApi::class)
class CTAPClient(
    private val library: FIDOkLibrary,
    private val device: AuthenticatorDevice,
    private val collectPinFromUser: suspend (client: CTAPClient) -> String? = { null },
) {
    private val pp2AesInfo = "CTAP2 AES key".encodeToByteArray()
    private val pp2HmacInfo = "CTAP2 HMAC key".encodeToByteArray()
    private val defaultCredentialAlgorithms =
        listOf(
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

    internal fun <T> xmit(
        command: CtapCommand,
        deserializationStrategy: DeserializationStrategy<T>,
    ): T {
        val result = xmit(command)

        return CTAPCBORDecoder(result).decodeSerializableValue(
            deserializationStrategy,
        )
    }

    /**
     * Get info from the Authenticator, potentially using a cached copy.
     *
     * @see [getInfo].
     * @return A [GetInfoResponse], which might be stale.
     */
    internal fun getInfoIfUnset(): GetInfoResponse {
        val info = info
        if (info != null) {
            return info
        }
        return getInfo()
    }

    /**
     * Sends a [GetInfoCommand] to the Authenticator and returns a [GetInfoResponse].
     *
     * Used to read all sorts of useful state and meta-information from the Authenticator.
     *
     * @return The Authenticator info object
     */
    @Throws(DeviceCommunicationException::class, CTAPError::class)
    fun getInfo(): GetInfoResponse {
        val ret = xmit(GetInfoCommand(), GetInfoResponse.serializer())
        info = ret
        Logger.d { "Device info: $ret" }
        return ret
    }

    /**
     * Sends an [AuthenticatorSelectionCommand] to the Authenticator.
     *
     * @return true if the authenticator accepts the selection; false if the
     *         authenticator EXPLICITLY rejects the selection; null if the
     *         authenticator didn't affirmatively deny the selection,
     *         but didn't accept it either
     */
    @Throws(CTAPError::class)
    fun select(): Boolean? {
        try {
            xmit(AuthenticatorSelectionCommand())
            return true
        } catch (_: DeviceCommunicationException) {
        } catch (e: CTAPError) {
            if (e.code == CTAPResponse.OPERATION_DENIED.value) {
                return false
            }
            if (e.code == CTAPResponse.USER_ACTION_TIMEOUT.value) {
                return null
            }
            throw e
        }
        return null
    }

    /**
     * Sends a [ResetCommand] to the Authenticator, fully clearing its state. Returns nothing.
     */
    @Throws(DeviceCommunicationException::class)
    fun authenticatorReset() {
        val ret = device.sendBytes(ResetCommand().getCBOR())
        checkResponseStatus(ret)
    }

    /**
     * As [makeCredential], but returns raw CTAP bytes instead of a parsed object.
     *
     * @return CTAP bytes representing a [MakeCredentialResponse]
     */
    @Throws(DeviceCommunicationException::class, CTAPError::class)
    internal fun makeCredentialRaw(
        rpId: String,
        clientDataHash: ByteArray,
        rpName: String? = null,
        userId: ByteArray? = null,
        userName: String? = null,
        userDisplayName: String? = null,
        discoverableCredential: Boolean = false,
        userVerification: Boolean = false,
        pubKeyCredParams: List<PublicKeyCredentialParameters> = defaultCredentialAlgorithms,
        excludeList: List<PublicKeyCredentialDescriptor>? = null,
        pinUvProtocol: UByte? = null,
        pinUvToken: PinUVToken? = null,
        enterpriseAttestation: UInt? = null,
        extensions: ExtensionSetup? = null,
    ): ByteArray {
        require(enterpriseAttestation == null || enterpriseAttestation == 1u || enterpriseAttestation == 2u)
        require(
            (pinUvToken == null && pinUvProtocol == null) ||
                pinUvToken != null,
        )
        val info = getInfoIfUnset()
        if (enterpriseAttestation != null && info.options?.get("ep") != true) {
            throw IllegalArgumentException("Authenticator enterprise attestation isn't enabled")
        }
        if (extensions?.checkSupport(info) == false) {
            throw IllegalArgumentException("Authenticator does not support requested extension(s)")
        }
        val effectiveRpName = rpName ?: rpId

        val effectiveUserId = userId ?: Random.nextBytes(32)

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

        Logger.d { "Making credential with effective client data hash ${clientDataHash.toHexString()}" }

        var pinUvProtocolVersion: UByte? = null
        val pinUvAuthParam =
            if (pinUvToken != null) {
                val pp = getPinProtocol(pinUvProtocol)
                pinUvProtocolVersion = pp.getVersion()

                Logger.d { "Authenticating request using PIN protocol $pinUvProtocolVersion" }

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

        val request =
            MakeCredentialCommand(
                clientDataHash,
                PublicKeyCredentialRpEntity(
                    id = rpId,
                    name = effectiveRpName,
                ),
                user =
                    PublicKeyCredentialUserEntity(
                        id = effectiveUserId,
                        displayName = userDisplayName,
                        name = userName,
                    ),
                pubKeyCredParams = pubKeyCredParams,
                options = options,
                excludeList = excludeList,
                pinUvAuthProtocol = pinUvProtocolVersion,
                pinUvAuthParam = pinUvAuthParam,
                enterpriseAttestation = enterpriseAttestation,
                extensions = extensions?.makeCredential(ka, pp),
            )

        val ret =
            try {
                xmit(request)
            } catch (e: CTAPError) {
                if (e.code == CTAPResponse.CREDENTIAL_EXCLUDED.value) {
                    throw CredentialExcludedError()
                }
                throw e
            }

        return ret
    }

    /**
     * Creates a new CTAP credential via a [MakeCredentialCommand], returning a [MakeCredentialResponse].
     *
     * This is the way to create a new key usable for later calls to [getAssertions]. The easiest way to
     * get the credential itself is to call [MakeCredentialResponse.getCredentialID]
     * and [MakeCredentialResponse.getCredentialPublicKey].
     *
     * @param rpId The unique identifier of the Relying Party to which the new credential pertains. In CTAP
     *             this can be any string, but in practice it's usually a domain name (no URI schema or port number).
     * @param rpName The human-readable name of the Relying Party - defaults to the [rpId]
     * @param clientDataHash A 32-byte-long identifier for the credential request. This is intended to be the
     *                       SHA-256 hash of a "client data object", but in practice can be any non-colliding 32 bytes.
     *                       If not set, this will be generated randomly
     * @param userId A 32-byte long user ID. This determines whether two different credentials will be created by the
     *               Authenticator for the same `rpId`, or whether the previous credential will be replaced. If not
     *               provided, this will be generated randomly
     * @param userName The "username" to associate with the credential being created. This might be displayed to the
     *                 Authenticator's user to help them select a credential, but is more likely unused.
     * @param userDisplayName The "display" name of the user to associate with the credential being created. This might
     *                 be displayed to the Authenticator's user to help them select a credential, but is more likely
     *                 irrelevant.
     * @param discoverableCredential If true, try to create a "discoverable" credential - one that can later be used
     *                               by providing the same `rpId` to the Authenticator even without the credential ID.
     * @param userVerification DEPRECATED field. If true, request the Authenticator "verify" the user. How this happens
     *                         depends on the device. If you're think about using this, you probably wanted to pass
     *                         a `pinUvToken` instead
     * @param pubKeyCredParams The cryptographic algorithms and parameters that the caller can handle. The Authenticator
     *                         will choose one that it supports, and reply with a credential of that type
     * @param excludeList A list of previously-created credentials. If any of them are valid for this Authenticator,
     *                    a `CredentialExcludedError` will be raised.
     * @param pinUvProtocol The CTAP number of the PIN/UV protocol to use. If not set, will be none or version 1,
     *                      depending on whether a `pinUvToken` is supplied
     * @param pinUvToken A token obtained from [getPinUvTokenUsingAppropriateMethod] or similar method
     * @param enterpriseAttestation If set, the [enterprise attestation "level"][EnterpriseAttestationLevel] to request
     * @param extensions An [ExtensionSetup] indicating any extension(s) active for the makeCredential call.
     *                   Each extension has a change to alter the request going to the Authenticator, and to collect
     *                   data from the response
     * @param validateAttestation If true, throw an [InvalidAttestationError] in the event the attestation from the
     *                            Authenticator doesn't validate
     * @return The newly created credential, attestation, extension responses, etc
     *
     * @sample ctapClientExample
     */
    @Throws(DeviceCommunicationException::class, CTAPError::class)
    fun makeCredential(
        rpId: String,
        rpName: String? = null,
        clientDataHash: ByteArray? = null,
        userId: ByteArray? = null,
        userName: String? = null,
        userDisplayName: String? = null,
        discoverableCredential: Boolean = false,
        userVerification: Boolean = false,
        pubKeyCredParams: List<PublicKeyCredentialParameters> = defaultCredentialAlgorithms,
        excludeList: List<PublicKeyCredentialDescriptor>? = null,
        pinUvProtocol: UByte? = null,
        pinUvToken: PinUVToken? = null,
        enterpriseAttestation: UInt? = null,
        extensions: ExtensionSetup? = null,
        validateAttestation: Boolean = true,
    ): MakeCredentialResponse {
        require(clientDataHash == null || clientDataHash.size == 32)

        val effectiveClientDataHash = clientDataHash ?: Random.nextBytes(32)

        val rawResult =
            makeCredentialRaw(
                rpId,
                effectiveClientDataHash,
                rpName,
                userId,
                userName,
                userDisplayName,
                discoverableCredential,
                userVerification,
                pubKeyCredParams,
                excludeList,
                pinUvProtocol,
                pinUvToken,
                enterpriseAttestation,
                extensions,
            )

        val ret =
            CTAPCBORDecoder(rawResult).decodeSerializableValue(
                MakeCredentialResponse.serializer(),
            )

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

    /**
     * Validates a "self" attestation - a credential signed with its own private key.
     *
     * @param makeCredentialResponse The response from the Authenticator to a [makeCredential]
     * @param clientDataHash The [MakeCredentialCommand.clientDataHash] given to the Authenticator
     * @param alg The [COSEAlgorithmIdentifier] used for the signature
     * @param sig The signature to validate
     */
    @Throws(InvalidAttestationError::class)
    fun validateSelfAttestation(
        makeCredentialResponse: MakeCredentialResponse,
        clientDataHash: ByteArray,
        alg: Long,
        sig: ByteArray,
    ) {
        if (alg != COSEAlgorithmIdentifier.ES256.value) {
            // Unsupported, presently
            Logger.w { "Skipping signature validation for unsupported algorithm $alg" }
            return
        }

        val pubKey = makeCredentialResponse.getCredentialPublicKey()

        validateES256UsingPubKey(makeCredentialResponse, clientDataHash, pubKey.x, pubKey.y!!, sig)
    }

    private fun validateBasicAttestation(
        makeCredentialResponse: MakeCredentialResponse,
        clientDataHash: ByteArray,
        alg: Long,
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

    /**
     * Checks the attestation of a given freshly-created credential
     *
     * @param makeCredentialResponse The result of a [makeCredential] operation
     * @param clientDataHash The 32 bytes given to a [makeCredential] operation
     */
    @Throws(InvalidAttestationError::class)
    fun validateCredentialAttestation(
        makeCredentialResponse: MakeCredentialResponse,
        clientDataHash: ByteArray,
    ) {
        when (makeCredentialResponse.fmt) {
            AttestationTypes.PACKED.value -> {
                val alg = makeCredentialResponse.attStmt["alg"] as Long
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

    /**
     * Checks that an assertion is signed with the private key corresponding to the given public key
     *
     * @param getAssertionResponse A response to a [getAssertions] invocation
     * @param clientDataHash The 32 bytes given as [GetAssertionCommand.clientDataHash] to the Authenticator
     * @param publicKey The public key, such as from [MakeCredentialResponse.getCredentialPublicKey], to use in
     *                  validating the assertion's signature
     */
    @Throws(InvalidSignatureError::class)
    fun validateAssertionSignature(
        getAssertionResponse: GetAssertionResponse,
        clientDataHash: ByteArray,
        publicKey: COSEKey,
    ) {
        if (publicKey.alg != COSEAlgorithmIdentifier.ES256.value) {
            throw NotImplementedError("Only ES256 signatures are currently implemented")
        }

        val signedBytes = (getAssertionResponse.authData.rawBytes.toList() + clientDataHash.toList()).toByteArray()

        Logger.v { "Signature-verifying ${signedBytes.size} bytes" }

        if (!library.cryptoProvider.es256SignatureValidate(
                signedBytes = signedBytes,
                P256Point(publicKey.x, publicKey.y!!),
                sig = getAssertionResponse.signature,
            )
        ) {
            throw InvalidSignatureError()
        }
    }

    /**
     * Like [getAssertions], but returns a single assertion as raw CTAP bytes.
     *
     * @return Bytes representing an encoded [GetAssertionResponse]
     */
    @Throws(DeviceCommunicationException::class, CTAPError::class)
    internal fun getAssertionRaw(
        rpId: String,
        clientDataHash: ByteArray,
        allowList: List<PublicKeyCredentialDescriptor>? = null,
        extensions: ExtensionSetup? = null,
        userPresence: Boolean? = null,
        pinUvProtocol: UByte? = null,
        pinUvToken: PinUVToken? = null,
    ): ByteArray {
        require(
            (pinUvToken == null && pinUvProtocol == null) ||
                pinUvToken != null,
        )

        val info = getInfoIfUnset()
        if (extensions?.checkSupport(info) == false) {
            throw IllegalArgumentException("Authenticator does not support requested extension(s)")
        }

        var pinUvProtocolVersion: UByte? = null
        val pinUvAuthParam =
            if (pinUvToken != null) {
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
            options =
                hashMapOf(
                    GetAssertionOption.UP to false,
                )
        }

        val numAllowListEntriesPerBatch = info.maxCredentialCountInList?.toInt() ?: 10000
        var allowListSent = 0
        while (allowListSent < (allowList?.size ?: 1)) {
            val thisRequestAllowList =
                allowList?.subList(allowListSent, min(allowListSent + numAllowListEntriesPerBatch, allowList.size))

            Logger.v("PIN/UV auth length for getAssertion: ${pinUvAuthParam?.size}")

            val request =
                GetAssertionCommand(
                    clientDataHash = clientDataHash,
                    rpId = rpId,
                    allowList = thisRequestAllowList,
                    extensions = extensions?.getAssertion(keyAgreement = ka, pinUVProtocol = pp),
                    options = options,
                    pinUvAuthParam = pinUvAuthParam,
                    pinUvAuthProtocol = pinUvProtocolVersion,
                )

            val rawFirstResponse =
                try {
                    xmit(request)
                } catch (e: CTAPError) {
                    if (e.code == CTAPResponse.NO_CREDENTIALS.value) {
                        // OK, fine, try some other creds
                        allowListSent += thisRequestAllowList?.size ?: 1
                        continue
                    }
                    throw e
                }

            return rawFirstResponse
        }

        throw CTAPError(CTAPResponse.NO_CREDENTIALS.value)
    }

    /**
     * Core call to get assertion(s) from the Authenticator
     *
     * This will use the [allowList] (or, for discoverable credentials, the credentials stored on the
     * Authenticator itself) to generate a list of assertions. Each assertion is signed by the Authenticator.
     *
     * Note that the number of responses might not match the length of the [allowList]: only credentials
     * that are still valid, allowed to be used, etc will generate assertions.
     *
     * To use discoverable credentials, the [allowList] must be empty.
     *
     * @param rpId The unique identifier of the Relying Party to assert. Should match the value used for the
     *             `makeCredential` invocation, whether that happened here or elsewhere
     * @param clientDataHash A 32-byte-long value that is signed by the assertion. In some senses, this is really
     *                       what is being verified. Designed to be a SHA-256 hash of some client data object, but
     *                       at the CTAP level can be any 32 bytes, ideally unpredictable
     * @param allowList Optional list of descriptors of previously-created credentials. If not set, discoverable
     *                  credentials will be used instead; if set, discoverable credentials are ignored
     * @param extensions Any assertion-time extensions to use
     * @param userPresence If true, request the Authenticator check user presence
     * @param pinUvProtocol PIN/UV protocol number to use, if any
     * @param pinUvToken A user verification token obtained from [getPinUvTokenUsingAppropriateMethod] or similar method
     *
     * @return A list of [GetAssertionResponse] objects, each one representing a separate assertion. The
     *         assertion signature(s) are unvalidated; it's the responsibility of the calling code to check them
     *
     * @sample getAssertionsExample
     */
    @Throws(DeviceCommunicationException::class, CTAPError::class)
    fun getAssertions(
        rpId: String,
        clientDataHash: ByteArray? = null,
        allowList: List<PublicKeyCredentialDescriptor>? = null,
        extensions: ExtensionSetup? = null,
        userPresence: Boolean? = null,
        pinUvProtocol: UByte? = null,
        pinUvToken: PinUVToken? = null,
    ): List<GetAssertionResponse> {
        require(clientDataHash == null || clientDataHash.size == 32)
        val effectiveClientDataHash = clientDataHash ?: Random.nextBytes(32)

        val ret: ArrayList<GetAssertionResponse> = arrayListOf()

        val info = getInfoIfUnset()
        if (extensions?.checkSupport(info) == false) {
            throw IllegalArgumentException("Authenticator does not support requested extension(s)")
        }

        val numAllowListEntriesPerBatch = info.maxCredentialCountInList?.toInt() ?: 10000
        var allowListSent = 0
        while (allowListSent < (allowList?.size ?: 1)) {
            val thisRequestAllowList =
                allowList?.subList(allowListSent, min(allowListSent + numAllowListEntriesPerBatch, allowList.size))

            val rawFirstResponse =
                try {
                    getAssertionRaw(
                        rpId = rpId,
                        clientDataHash = effectiveClientDataHash,
                        allowList = thisRequestAllowList,
                        extensions = extensions,
                        userPresence = userPresence,
                        pinUvProtocol = pinUvProtocol,
                        pinUvToken = pinUvToken,
                    )
                } catch (e: CTAPError) {
                    if (e.code == CTAPResponse.NO_CREDENTIALS.value) {
                        // OK, fine, try some other creds
                        allowListSent += thisRequestAllowList?.size ?: 1
                        continue
                    }
                    throw e
                }

            val firstResponse =
                CTAPCBORDecoder(rawFirstResponse)
                    .decodeSerializableValue(GetAssertionResponse.serializer())

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

        if (ret.isEmpty()) {
            throw CTAPError(CTAPResponse.NO_CREDENTIALS.value)
        }

        return ret
    }

    /**
     * Obtain the Platform-Authenticator shared secret
     *
     * This method will create a new Platform key, retrieve the Authenticator key, and perform an ECDH agreement.
     * It will also build the various cryptographic objects necessary for the CTAP protocol.
     *
     * @param pinUvProtocol The PIN/UV protocol number to use; this must match the version used for later commands
     *
     * @return An initialized key agreement object
     */
    @Throws(DeviceCommunicationException::class, CTAPError::class)
    fun getKeyAgreement(pinUvProtocol: UByte = 2u): KeyAgreementPlatformKey {
        require(pinUvProtocol == 1u.toUByte() || pinUvProtocol == 2u.toUByte())

        val crypto = library.cryptoProvider

        val decoded =
            xmit(
                ClientPinCommand.getKeyAgreement(pinUvProtocol),
                ClientPinGetKeyAgreementResponse.serializer(),
            )

        val otherPublic = P256Point(decoded.key.x, decoded.key.y!!)
        val state = crypto.ecdhKeyAgreementInit(otherPublic)
        try {
            val pp1Key =
                crypto.ecdhKeyAgreementKDF(
                    state,
                    otherPublic,
                    false,
                    byteArrayOf(),
                    byteArrayOf(),
                ).bytes
            val pp2AES =
                crypto.ecdhKeyAgreementKDF(
                    state,
                    otherPublic,
                    true,
                    ByteArray(32) { 0x00 },
                    pp2AesInfo,
                ).bytes
            val pp2HMAC =
                crypto.ecdhKeyAgreementKDF(
                    state,
                    otherPublic,
                    true,
                    ByteArray(32) { 0x00 },
                    pp2HmacInfo,
                ).bytes
            val key =
                KeyAgreementPlatformKey(
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

    /**
     * Sets a new PIN for the Authenticator, which must not already have one set
     *
     * @param newPinUnicode New user PIN, as a UTF-8 valid string
     * @param pinUvProtocol PIN/UV protocol version number to use
     */
    @Throws(DeviceCommunicationException::class, CTAPError::class)
    fun setPIN(
        newPinUnicode: String,
        pinUvProtocol: UByte? = null,
    ) {
        val pp = getPinProtocol(pinUvProtocol)
        val pk = ensurePlatformKey(pp)

        val newPINBytes = checkAndPadPIN(newPinUnicode)
        val newPinEnc = pp.encrypt(pk, newPINBytes)
        val pinHashEnc = pp.authenticate(pk, newPinEnc)

        val command =
            ClientPinCommand.setPIN(
                pinUvAuthProtocol = pp.getVersion(),
                keyAgreement = pk.getCOSE(),
                newPinEnc = newPinEnc,
                pinUvAuthParam = pinHashEnc,
            )

        val res = device.sendBytes(command.getCBOR())
        checkResponseStatus(res)

        cachedPin = newPinUnicode
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

    /**
     * Call the appropriate one of [setPIN] or [changePIN], depending on the Authenticator state.
     */
    @Throws(
        CTAPError::class,
        DeviceCommunicationException::class,
        PinNotAvailableException::class,
        CancellationException::class,
    )
    suspend fun setOrChangePIN(
        newPinUnicode: String,
        pinUvProtocol: UByte? = null,
    ) {
        Logger.v { "Changing/setting PIN to new ${newPinUnicode.length} character value" }

        val info = getInfoIfUnset()
        val cpOption =
            info.options?.get(CTAPOption.CLIENT_PIN.value)
                ?: throw IllegalStateException("Authenticator does not support PINs!")

        if (cpOption) {
            changePIN(newPinUnicode, pinUvProtocol)
        } else {
            setPIN(newPinUnicode, pinUvProtocol)
        }
    }

    /**
     * Changes the authenticator's existing PIN
     *
     * @param newPinUnicode New user PIN, as a UTF-8 valid string
     * @param pinUvProtocol PIN/UV protocol version number to use
     */
    @Throws(
        CTAPError::class,
        DeviceCommunicationException::class,
        PinNotAvailableException::class,
        CancellationException::class,
    )
    suspend fun changePIN(
        newPinUnicode: String,
        pinUvProtocol: UByte? = null,
    ) {
        val pp = getPinProtocol(pinUvProtocol)
        val pk = ensurePlatformKey(pp)

        val pin =
            (if (cachedPin != null) cachedPin else collectPinFromUser(this))
                ?: throw PinNotAvailableException()

        val newPINBytes = checkAndPadPIN(newPinUnicode)
        val newPinEnc = pp.encrypt(pk, newPINBytes)

        val left16 = library.cryptoProvider.sha256(pin.encodeToByteArray()).hash.copyOfRange(0, 16)
        val pinHashEnc = pp.encrypt(pk, left16)

        val authBlob = (newPinEnc.toList() + pinHashEnc.toList()).toByteArray()

        val pinUvAuthParam = pp.authenticate(pk, authBlob)

        val command =
            ClientPinCommand.changePIN(
                pinHashEnc = pinHashEnc,
                pinUvAuthProtocol = pp.getVersion(),
                keyAgreement = pk.getCOSE(),
                newPinEnc = newPinEnc,
                pinUvAuthParam = pinUvAuthParam,
            )

        val res = device.sendBytes(command.getCBOR())
        checkResponseStatus(res)

        cachedPin = newPinUnicode
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

    /**
     * Gets a PIN/UV token using the best available method for the underlying Authenticator
     *
     * @param desiredPermissions A bitfield of [CTAPPermission]s to request. May be ignored,
     *                           depending on Authenticator support, but should include the operation(s)
     *                           for which the token will be used
     * @param desiredRpId The Relying Party ID to which the gotten token should be bound. May be ignored,
     *                    depending on Authenticator support
     * @param pinUvProtocol The number of the PIN/UV protocol to use
     *
     * @return A token that may be passed to methods requiring User Verification
     */
    @Throws(
        PinNotAvailableException::class,
        CTAPError::class,
        DeviceCommunicationException::class,
        CancellationException::class,
    )
    suspend fun getPinUvTokenUsingAppropriateMethod(
        desiredPermissions: UByte,
        desiredRpId: String? = null,
        pinUvProtocol: UByte? = null,
    ): PinUVToken {
        val info = getInfoIfUnset()
        if (info.options?.get(CTAPOption.PIN_UV_AUTH_TOKEN.value) == true) {
            // supports permissions

            if (info.options[CTAPOption.INTERNAL_USER_VERIFICATION.value] == true) {
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

            if (info.options[CTAPOption.CLIENT_PIN.value] != true) {
                // we want to try a PIN, but it's not set :-(
                throw PinNotAvailableException("Authenticator is not configured to use a PIN, but must be")
            }

            if (desiredPermissions.toUInt() and
                (
                    CTAPPermission.MAKE_CREDENTIAL.value.toUInt()
                        or CTAPPermission.GET_ASSERTION.value.toUInt()
                ) != 0u
            ) {
                // we're asking for MC or GA...
                if (info.options[CTAPOption.NO_MC_GA_PERMISSIONS_WITH_CLIENT_PIN.value] == true) {
                    // but the authenticator says that's not allowed with a PIN, only with UV
                    throw PermissionDeniedError(
                        "Using a PIN for making credentials or getting assertions" +
                            " is prohibited by the authenticator options",
                    )
                }
            }

            // try PIN (with permissions)
            val pin =
                (if (cachedPin != null) cachedPin else collectPinFromUser(this))
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

        val pin =
            (if (cachedPin != null) cachedPin else collectPinFromUser(this))
                ?: throw PinNotAvailableException()
        cachedPin = pin

        return getPinToken(
            currentPinUnicode = pin,
            pinUvProtocol = pinUvProtocol,
        )
    }

    /**
     * Get a PIN/UV token using an Authenticator's built-in User Verification method(s)
     *
     * @param pinUvProtocol The CTAP PIN/UV protcol version number
     * @param permissions Any [permissions][CTAPPermission] desired
     * @param rpId An optional Relying Party ID to which the fetched token will be bound
     * @return 32-byte PIN/UV token, valid until the Authenticator decides it's not
     */
    @Throws(DeviceCommunicationException::class, CTAPError::class)
    fun getPinTokenUsingUv(
        pinUvProtocol: UByte? = null,
        permissions: UByte,
        rpId: String? = null,
    ): PinUVToken {
        val pp = getPinProtocol(pinUvProtocol)
        val pk = ensurePlatformKey(pp)

        val command =
            ClientPinCommand.getPinUvAuthTokenUsingUvWithPermissions(
                pinUvAuthProtocol = pp.getVersion(),
                keyAgreement = pk.getCOSE(),
                permissions = permissions,
                rpId = rpId,
            )

        val ret = xmit(command, ClientPinGetTokenResponse.serializer())

        Logger.d { "Got PIN token: $ret" }

        return PinUVToken(pp.decrypt(pk, ret.pinUvAuthToken))
    }

    @Throws(DeviceCommunicationException::class, CTAPError::class)
    fun getUvRetries(): ClientPinUvRetriesResponse {
        val command = ClientPinCommand.getUVRetries()

        val ret = xmit(command, ClientPinUvRetriesResponse.serializer())

        Logger.d { "Remaining UV retries: $ret" }

        return ret
    }

    @Throws(DeviceCommunicationException::class, CTAPError::class)
    fun getPinToken(
        currentPinUnicode: String,
        pinUvProtocol: UByte? = null,
    ): PinUVToken {
        val pp = getPinProtocol(pinUvProtocol)
        val pk = ensurePlatformKey(pp)

        val left16 = library.cryptoProvider.sha256(currentPinUnicode.encodeToByteArray()).hash.copyOfRange(0, 16)
        val pinHashEnc = pp.encrypt(pk, left16)

        val command =
            ClientPinCommand.getPinToken(
                pinUvAuthProtocol = pp.getVersion(),
                keyAgreement = pk.getCOSE(),
                pinHashEnc = pinHashEnc,
            )

        val ret = xmit(command, ClientPinGetTokenResponse.serializer())

        Logger.d { "Got PIN token: $ret" }

        return PinUVToken(pp.decrypt(pk, ret.pinUvAuthToken))
    }

    @Throws(DeviceCommunicationException::class, CTAPError::class)
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

        val command =
            ClientPinCommand.getPinUvAuthTokenUsingPinWithPermissions(
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

    /**
     * Get the number of remaining PIN retries before blocking.
     *
     * @return CTAP response object with retry count and power-cycle-required state
     */
    @Throws(DeviceCommunicationException::class, CTAPError::class)
    fun getPINRetries(): ClientPinGetRetriesResponse {
        val command = ClientPinCommand.getPINRetries()
        return xmit(command, ClientPinGetRetriesResponse.serializer())
    }

    /**
     * Get a client for performing authenticator-config-related CTAP operations.
     *
     * @return Authenticator config object
     * @throws IllegalStateException If the authenticator does not support configuration
     */
    fun authenticatorConfig(): AuthenticatorConfigClient {
        val info = getInfoIfUnset()
        if (info.options?.get(CTAPOption.AUTHENTICATOR_CONFIG.value) != true) {
            throw IllegalStateException("Authenticator config commands not supported on $device")
        }
        return AuthenticatorConfigClient(this)
    }

    /**
     * Get a client for performing credential-management-related CTAP operations.
     *
     * @return Credential management object
     * @throws IllegalStateException If the authenticator does not support credentials management in some form
     */
    fun credentialManagement(): CredentialManagementClient {
        val info = getInfoIfUnset()
        val fullySupported = info.options?.get(CTAPOption.CREDENTIALS_MANAGEMENT.value) == true
        val prototypeSupported = info.options?.get(CTAPOption.CREDENTIALS_MANAGEMENT_PREVIEW.value) == true
        if (!fullySupported && !prototypeSupported) {
            throw IllegalStateException("Credential management commands not supported on $device")
        }
        return CredentialManagementClient(this)
    }

    override fun toString(): String {
        return device.toString()
    }
}

class PinNotAvailableException(msg: String = "A PIN was required, but not available") : Exception(msg)

data class PinUVToken(val token: ByteArray) {
    init {
        require(token.size == 16 || token.size == 32)
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

@Suppress("UNUSED_VARIABLE")
internal fun ctapClientExample() {
    val library = Examples.getLibrary()

    val devices = library.listDevices()
    val client = library.ctapClient(devices.first())

    val credential =
        client.makeCredential(
            rpId = "my.great.example",
        )
}

@OptIn(ExperimentalStdlibApi::class)
internal fun getAssertionsExample() {
    val client = Examples.getCTAPClient()

    val assertions =
        client.getAssertions(
            rpId = "my.assertion.example",
        )

    for (assertion in assertions) {
        println("Credential ID ${assertion.credential.id.toHexString()}")
        println("Got back username ${assertion.user?.name}")
    }
}
