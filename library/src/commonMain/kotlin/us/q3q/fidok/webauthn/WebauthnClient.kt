package us.q3q.fidok.webauthn

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import us.q3q.fidok.ctap.AuthenticatorTransport
import us.q3q.fidok.ctap.CTAPClient
import us.q3q.fidok.ctap.CTAPError
import us.q3q.fidok.ctap.CTAPOption
import us.q3q.fidok.ctap.CTAPPinPermission
import us.q3q.fidok.ctap.CTAPResponse
import us.q3q.fidok.ctap.DeviceCommunicationException
import us.q3q.fidok.ctap.FIDOkLibrary
import us.q3q.fidok.ctap.PinUVToken
import us.q3q.fidok.ctap.commands.COSEAlgorithmIdentifier
import us.q3q.fidok.ctap.commands.CTAPCBORDecoder
import us.q3q.fidok.ctap.commands.Extension
import us.q3q.fidok.ctap.commands.ExtensionSetup
import us.q3q.fidok.ctap.commands.GetAssertionResponse
import us.q3q.fidok.ctap.commands.GetInfoResponse
import us.q3q.fidok.ctap.commands.HMACSecretExtension
import us.q3q.fidok.ctap.commands.MakeCredentialResponse
import us.q3q.fidok.ctap.commands.PublicKeyCredentialParameters
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration.Companion.milliseconds

class WebauthnClient(private val library: FIDOkLibrary) {

    private fun isUVCapable(info: GetInfoResponse): Boolean =
        info.options?.get(CTAPOption.CLIENT_PIN.value) == true ||
            info.options?.get(CTAPOption.INTERNAL_USER_VERIFICATION.value) == true

    private fun isRKCapable(info: GetInfoResponse): Boolean =
        info.options?.get(CTAPOption.DISCOVERABLE_CREDENTIALS.value) == true

    private fun getAttachment(info: GetInfoResponse): AuthenticatorAttachment =
        if (info.options?.get(CTAPOption.PLATFORM_AUTHENTICATOR.value) == true) {
            AuthenticatorAttachment.PLATFORM
        } else {
            AuthenticatorAttachment.CROSS_PLATFORM
        }

    private suspend fun authenticatorFilter(
        preFilter: suspend (client: CTAPClient) -> Boolean,
        activeSelection: suspend (client: CTAPClient) -> Boolean,
        timeout: Long,
    ): CTAPClient {
        val devices = library.listDevices()

        Logger.d { "${devices.size} device(s) available" }

        val potentialCTAPClients: ArrayList<CTAPClient> = arrayListOf()

        for (device in devices) {
            val client = library.ctapClient(device)

            if (!preFilter(client)) {
                continue
            }

            potentialCTAPClients.add(client)
        }

        if (potentialCTAPClients.isEmpty()) {
            throw IllegalStateException("Could not find a usable CTAP device")
        }

        var selectedClient: CTAPClient = potentialCTAPClients[0]

        if (potentialCTAPClients.size > 1) {
            // TODO: prefer authenticators that better match the requested parameters
            coroutineScope {
                var ok = false

                try {
                    withTimeout(timeout.milliseconds) {
                        val receiver = Channel<CTAPClient?>(capacity = potentialCTAPClients.size)
                        val jobs = potentialCTAPClients.map {
                            launch {
                                val working = activeSelection(it)

                                if (working) {
                                    receiver.send(it)
                                } else {
                                    receiver.send(null)
                                }
                            }
                        }

                        for (i in 1..potentialCTAPClients.size) {
                            val clientOrNull = receiver.receive()
                            if (clientOrNull != null) {
                                // cancel anything left
                                for (job in jobs) {
                                    if (job.isActive) {
                                        job.cancel()
                                    }
                                }
                                selectedClient = clientOrNull
                                ok = true
                                break
                            }
                        }
                    }
                } catch (_: TimeoutCancellationException) {
                    Logger.w { "Timed out waiting for authenticator(s) to answer selection call" }
                }

                if (!ok) {
                    throw IllegalStateException("No CTAP client answered selection call")
                }
            }
        }

        return selectedClient
    }

    /**
     * The Webauthn standard way to create a new Credential.
     *
     * @param origin The hostname in which the code is executing
     * @param options All the Webauthn-standard credential creation options
     * @param sameOriginWithAncestors A parameter designed for browsers
     *
     * @return Newly created [PublicKeyCredential]
     */
    @OptIn(ExperimentalEncodingApi::class)
    @Throws(
        DeviceCommunicationException::class,
        CTAPError::class,
        kotlin.coroutines.cancellation.CancellationException::class,
    )
    suspend fun create(origin: String? = null, options: CredentialCreationOptions, sameOriginWithAncestors: Boolean = true): PublicKeyCredential {
        val effectiveOrigin = origin ?: options.publicKey.rp.id

        Logger.d { "Creating a credential for RP '${options.publicKey.rp.name}'" }

        val timeout = (options.publicKey.timeout ?: 10_000UL).toLong()

        val selectedClient = authenticatorFilter({ client ->
            val info = client.getInfoIfUnset()

            if ((
                    (
                        options.publicKey.authenticatorSelectionCriteria?.residentKey == null &&
                            options.publicKey.authenticatorSelectionCriteria?.requireResidentKey == true
                        ) ||
                        options.publicKey.authenticatorSelectionCriteria?.residentKey == ResidentKeyRequirement.REQUIRED.value
                    ) &&
                !isRKCapable(info)
            ) {
                Logger.i { "Ignoring $client because it does not support discoverable credentials" }
                return@authenticatorFilter false
            }

            if (options.publicKey.authenticatorSelectionCriteria?.userVerification
                == UserVerificationRequirement.REQUIRED.value && !isUVCapable(info)
            ) {
                Logger.i { "Ignoring $client because it does not have a means of user verification available" }
                return@authenticatorFilter false
            }

            if (options.publicKey.authenticatorSelectionCriteria?.authenticatorAttachment != null &&
                options.publicKey.authenticatorSelectionCriteria.authenticatorAttachment != getAttachment(info).value
            ) {
                Logger.i { "Ignoring $client because it doesn't match the desired attachment modality" }
                return@authenticatorFilter false
            }

            val supportedAlgorithms = info.algorithms?.toList() ?: listOf(PublicKeyCredentialParameters(COSEAlgorithmIdentifier.ES256))
            if (options.publicKey.pubKeyCredParams.find { supportedAlgorithms.contains(it) } == null) {
                Logger.i { "Ignoring $client because it doesn't support any of the requested algorithms" }
                return@authenticatorFilter false
            }

            true
        }, { client ->
            try {
                while (true) {
                    val res = client.select()
                    if (res == true) {
                        return@authenticatorFilter true
                    }
                    if (res == false) {
                        break
                    }
                    delay(500.milliseconds)
                }
            } catch (e: DeviceCommunicationException) {
                Logger.d { "Device communication exception during select: $e" }
            } catch (e: CTAPError) {
                if (e.code == CTAPResponse.INVALID_COMMAND.value) {
                    // This authenticator does not support selection
                    // TODO: use "silent" authentication instead
                    Logger.d { "Device $client does not support selection so is auto-used" }
                    return@authenticatorFilter true
                }
            }
            return@authenticatorFilter false
        }, timeout)

        val infoForSelected = selectedClient.getInfoIfUnset()
        val usingUV = isUVCapable(infoForSelected) && (
            options.publicKey.authenticatorSelectionCriteria?.userVerification == UserVerificationRequirement.REQUIRED.value ||
                options.publicKey.authenticatorSelectionCriteria?.userVerification == UserVerificationRequirement.PREFERRED.value
            )
        val usingDisco = isRKCapable(infoForSelected) &&
            (
                (
                    options.publicKey.authenticatorSelectionCriteria?.residentKey == null &&
                        options.publicKey.authenticatorSelectionCriteria?.requireResidentKey == true
                    ) ||
                    options.publicKey.authenticatorSelectionCriteria?.residentKey == ResidentKeyRequirement.REQUIRED.value ||
                    options.publicKey.authenticatorSelectionCriteria?.residentKey == ResidentKeyRequirement.PREFERRED.value ||
                    infoForSelected.options?.get(CTAPOption.ALWAYS_UV.value) == true
                )

        var pinUvToken: PinUVToken? = null
        if (usingUV) {
            pinUvToken = selectedClient.getPinUvTokenUsingAppropriateMethod(
                desiredPermissions = CTAPPinPermission.MAKE_CREDENTIAL.value,
                desiredRpId = options.publicKey.rp.id,
            )
        }

        val usingEP = options.publicKey.attestation == AttestationConveyancePreference.ENTERPRISE.value &&
            infoForSelected.options?.get(CTAPOption.ENTERPRISE_ATTESTATION.value) == true

        val supportedAlgorithms = infoForSelected.algorithms?.toList() ?: listOf(PublicKeyCredentialParameters(COSEAlgorithmIdentifier.ES256))
        val effectivePubKeyCredParams = options.publicKey.pubKeyCredParams.filter { supportedAlgorithms.contains(it) }

        val clientData = mutableMapOf(
            "type" to JsonPrimitive("webauthn.create"),
            "challenge" to JsonPrimitive(Base64.UrlSafe.encode(options.publicKey.challenge)),
            "origin" to JsonPrimitive(effectiveOrigin),
            "crossOrigin" to JsonPrimitive(!sameOriginWithAncestors),
        )
        if (!sameOriginWithAncestors) {
            clientData["topOrigin"] = JsonPrimitive(effectiveOrigin) // TODO this for real
        }
        val clientDataAsString = Json.encodeToString(JsonObject.serializer(), JsonObject(clientData))
        val clientDataJson = clientDataAsString.encodeToByteArray()
        val clientDataHash = library.cryptoProvider.sha256(clientDataJson).hash

        val extensions = arrayListOf<Extension>()
        var hmacSecretExtension: HMACSecretExtension? = null
        if (options.publicKey.extensions?.get("hmacCreateSecret") == true) {
            hmacSecretExtension = HMACSecretExtension()
            extensions.add(hmacSecretExtension)
        }

        val extensionSetup = ExtensionSetup(extensions)

        val rawResult = selectedClient.makeCredentialRaw(
            rpId = options.publicKey.rp.id!!,
            rpName = options.publicKey.rp.name,
            clientDataHash = clientDataHash,
            userId = options.publicKey.user.id,
            userName = options.publicKey.user.name,
            userDisplayName = options.publicKey.user.displayName,
            discoverableCredential = usingDisco,
            userVerification = false, // TODO: CTAP2.0
            pubKeyCredParams = effectivePubKeyCredParams,
            excludeList = options.publicKey.excludeCredentials,
            pinUvToken = pinUvToken,
            enterpriseAttestation = if (usingEP) 1u else null,
            extensions = extensionSetup,
        )

        val ret = CTAPCBORDecoder(rawResult).decodeSerializableValue(
            MakeCredentialResponse.serializer(),
        )

        extensionSetup.makeCredentialResponse(ret)

        val filteredTransports = infoForSelected.transports?.mapNotNull { transportName ->
            if (AuthenticatorTransport.entries.find { it.value == transportName } != null) {
                transportName
            } else {
                null
            }
        }?.sortedBy { it }?.toList() ?: listOf()

        val extensionResults = hashMapOf<String, Any>()
        if (hmacSecretExtension != null) {
            extensionResults["hmacCreateSecret"] = hmacSecretExtension.wasCreated()
        }

        return PublicKeyCredential(
            id = Base64.UrlSafe.encode(ret.getCredentialID()),
            rawId = ret.getCredentialID(),
            authenticatorAttachment = getAttachment(infoForSelected).value,
            response = AuthenticatorAttestationResponse(
                clientDataJson,
                attestationObject = rawResult,
                transports = filteredTransports,
            ),
            clientExtensionResults = extensionResults,
        )
    }

    /**
     * Shortcut overload of [create], taking more convenient parameters
     *
     * @param options The various options necessary for making a credential
     *
     * @return Newly created [PublicKeyCredential]
     */
    @Throws(
        DeviceCommunicationException::class,
        CTAPError::class,
        kotlin.coroutines.cancellation.CancellationException::class,
    )
    suspend fun create(options: PublicKeyCredentialCreationOptions): PublicKeyCredential {
        return create(options = CredentialCreationOptions(options))
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Throws(
        DeviceCommunicationException::class,
        CTAPError::class,
        kotlin.coroutines.cancellation.CancellationException::class,
    )
    suspend fun get(origin: String, options: CredentialRequestOptions, sameOriginWithAncestors: Boolean = true): PublicKeyCredential {
        Logger.d { "Getting a credential for RP ID '${options.publicKey.rpId}'" }

        val timeout = (options.publicKey.timeout ?: 10_000UL).toLong()

        val client = authenticatorFilter({ true }, { true }, timeout) // FIXME: pick authenticator sanely

        val infoForSelected = client.getInfoIfUnset()

        val usingUV = isUVCapable(infoForSelected) && (
            options.publicKey.userVerification == UserVerificationRequirement.REQUIRED.value ||
                options.publicKey.userVerification == UserVerificationRequirement.PREFERRED.value ||
                infoForSelected.options?.get(CTAPOption.ALWAYS_UV.value) == true
            )

        var pinUvToken: PinUVToken? = null
        if (usingUV) {
            pinUvToken = client.getPinUvTokenUsingAppropriateMethod(
                desiredPermissions = CTAPPinPermission.GET_ASSERTION.value,
                desiredRpId = options.publicKey.rpId,
            )
        }

        val clientData = mutableMapOf(
            "type" to JsonPrimitive("webauthn.get"),
            "challenge" to JsonPrimitive(Base64.UrlSafe.encode(options.publicKey.challenge)),
            "origin" to JsonPrimitive(origin),
            "crossOrigin" to JsonPrimitive(!sameOriginWithAncestors),
        )
        if (!sameOriginWithAncestors) {
            clientData["topOrigin"] = JsonPrimitive(origin) // TODO this for real
        }
        val clientDataAsString = Json.encodeToString(JsonObject.serializer(), JsonObject(clientData))
        val clientDataJson = clientDataAsString.encodeToByteArray()
        val clientDataHash = library.cryptoProvider.sha256(clientDataJson).hash

        val extensions = arrayListOf<Extension>()
        val hmacSecretParams = options.publicKey.extensions?.get("hmacGetSecret") as Map<*, *>?
        var hmacSecretExtension: HMACSecretExtension? = null
        if (hmacSecretParams != null) {
            hmacSecretExtension = HMACSecretExtension(
                salt1 = hmacSecretParams["salt1"] as ByteArray?,
                salt2 = hmacSecretParams["salt2"] as ByteArray?,
            )
            extensions.add(hmacSecretExtension)
        }

        val extensionSetup = ExtensionSetup(extensions)

        val assertionBytes = client.getAssertionRaw(
            rpId = options.publicKey.rpId ?: "", // TODO: default origin?
            clientDataHash = clientDataHash,
            allowList = options.publicKey.allowCredentials,
            extensions = extensionSetup,
            pinUvToken = pinUvToken,
        )

        val ret = CTAPCBORDecoder(assertionBytes).decodeSerializableValue(GetAssertionResponse.serializer())

        extensionSetup.getAssertionResponse(ret)

        val extensionResults = hashMapOf<String, Any>()
        if (hmacSecretExtension != null) {
            val result = hmacSecretExtension.getResult()
            val first = result.first
            val second = result.second
            val mp = hashMapOf<String, ByteArray>()
            if (first != null) {
                mp["output1"] = first
            }
            if (second != null) {
                mp["output2"] = second
            }
            extensionResults["hmacGetSecret"] = mp
        }

        return PublicKeyCredential(
            id = Base64.UrlSafe.encode(ret.credential.id),
            rawId = ret.credential.id,
            authenticatorAttachment = getAttachment(infoForSelected).value,
            response = AuthenticatorAssertionResponse(
                clientDataJson,
                authenticatorData = assertionBytes,
                signature = ret.signature,
                userHandle = ret.user?.id,
                attestationObject = null, // Why would this ever be set?
            ),
            clientExtensionResults = extensionResults,
        )
    }

    @Throws(
        DeviceCommunicationException::class,
        CTAPError::class,
        kotlin.coroutines.cancellation.CancellationException::class,
    )
    suspend fun get(options: PublicKeyCredentialRequestOptions): PublicKeyCredential {
        return get(origin = options.rpId ?: "", options = CredentialRequestOptions(options))
    }

    fun preventSilentAccess() {
    }
}
