package us.q3q.fidok.ctap.commands

import kotlinx.serialization.DeserializationStrategy
import us.q3q.fidok.crypto.KeyAgreementPlatformKey
import us.q3q.fidok.crypto.PinUVProtocol

/**
 * Represents a CTAP/Webauthn extension.
 *
 * Extensions may activate on a [makeCredential][MakeCredentialCommand] and/or [getAssertion][GetAssertionCommand]
 * operation, attaching data to the request and potentially receiving data from the response.
 *
 * In order to be able to parse extension-specific response data, the [Extension] must call [ExtensionSetup.register]
 * prior to use (such as in an `init` method).
 */
interface Extension {
    /**
     * The canonical FIDO name of this extension
     */
    fun getName(): ExtensionName

    /**
     * Declare what the extension should put into an outgoing [MakeCredentialCommand].
     *
     * @param keyAgreement The Platform-Authenticator key agreement state, if requested
     * @param pinUVProtocol The PIN/UV protocol in use, if one is being used
     * @return Parameters to attach to the outgoing request to the Authenticator from the Platform
     */
    fun makeCredential(
        keyAgreement: KeyAgreementPlatformKey?,
        pinUVProtocol: PinUVProtocol?,
    ): ExtensionParameters? = null

    /**
     * Declare what the extension should put into an outgoing [GetAssertionCommand].
     *
     * @param keyAgreement The Platform-Authenticator key agreement state, if requested
     * @param pinUVProtocol The PIN/UV protocol in use, if one is being used
     * @return Parameters to attach to the outgoing request to the Authenticator from the Platform
     */
    fun getAssertion(
        keyAgreement: KeyAgreementPlatformKey?,
        pinUVProtocol: PinUVProtocol?,
    ): ExtensionParameters? = null

    /**
     * Take any extension-specific information from the Authenticator [MakeCredentialResponse].
     *
     * Stores the relevant information inside the [Extension] instance as a side effect.
     *
     * @param response The response object from the Authenticator
     */
    fun makeCredentialResponse(response: MakeCredentialResponse) {}

    /**
     * Take any extension-specific information from the Authenticator [GetAssertionResponse].
     *
     * Stores the relevant information inside the [Extension] instance as a side effect.
     *
     * @param response The response object from the Authenticator
     */
    fun getAssertionResponse(response: GetAssertionResponse) {}

    /**
     * Check whether the Authenticator supports this extension.
     *
     * By default, an extension is supported if its canonical name is present in [GetInfoResponse.extensions].
     *
     * @param info The Authenticator's response to a [GetInfoCommand]
     * @return true if the extension is supported; false if it is not
     */
    fun checkSupport(info: GetInfoResponse): Boolean {
        val name = getName()
        return info.extensions?.contains(name) == true
    }
}

/**
 * Represents the set of enabled [Extension]s for a particular CTAP request and its response.
 *
 * @property appliedExtensions [Extension] instances to be used
 */
class ExtensionSetup(private val appliedExtensions: List<Extension>) {
    constructor(appliedExtension: Extension) : this(listOf(appliedExtension))

    companion object {
        private val registeredCreationExtensions = hashMapOf<ExtensionName, DeserializationStrategy<ExtensionParameters>>()
        private val registeredAssertionExtensions = hashMapOf<ExtensionName, DeserializationStrategy<ExtensionParameters>>()
        private val keyAgreementRequiredExtensions = hashMapOf<ExtensionName, Boolean>()

        /**
         * Allows an [Extension] to be understood by the [CTAPCBORDecoder].
         *
         * This method must be called before requests are sent or responses received for the extension,
         * or the deserializer won't know how to parse the CTAP response object.
         *
         * The given deserializers will be given as input the map value associated with the extension's canonical
         * name from [AuthenticatorData.extensions].
         *
         * @param name The [Extension]'s canonical name
         * @param creationParameterDeserializer If the [Extension] retrieves data from a [MakeCredentialResponse], this
         *                                      is the deserializer to use for that
         * @param creationParameterDeserializer If the [Extension] retrieves data from a [GetAssertionResponse], this
         *                                      is the deserializer to use for that
         * @param requiresKeyAgreement If true, the [Extension] needs access to a [KeyAgreementPlatformKey] in order to
         *                             parse response objects - for decrypting response data.
         */
        fun register(
            name: ExtensionName,
            creationParameterDeserializer: DeserializationStrategy<ExtensionParameters>? = null,
            assertionParameterDeserializer: DeserializationStrategy<ExtensionParameters>? = null,
            requiresKeyAgreement: Boolean = false,
        ) {
            if (creationParameterDeserializer != null) {
                registeredCreationExtensions[name] = creationParameterDeserializer
            }
            if (assertionParameterDeserializer != null) {
                registeredAssertionExtensions[name] = assertionParameterDeserializer
            }
            if (requiresKeyAgreement) {
                keyAgreementRequiredExtensions[name] = true
            }
        }

        /**
         * Get whether a particular [Extension] requires a [KeyAgreementPlatformKey].
         *
         * This returns the value with which the extension [register]ed itself.
         *
         * @param name The canonical extension name in question
         * @return true if the extension requires key agreement; false otherwise
         */
        fun isKeyAgreementRequired(name: ExtensionName): Boolean {
            return keyAgreementRequiredExtensions[name] == true
        }

        /**
         * Get the serialization strategy for a particular [Extension] in the context of a [MakeCredentialResponse].
         *
         * This returns the value with which the extension [register]ed itself.
         *
         * @param name The canonical extension name in question
         * @return Deserialization strategy to use
         */
        fun getCreationRegistration(name: ExtensionName): DeserializationStrategy<ExtensionParameters>? {
            return registeredCreationExtensions[name]
        }

        /**
         * Get the serialization strategy for a particular [Extension] in the context of a [GetAssertionResponse].
         *
         * This returns the value with which the extension [register]ed itself.
         *
         * @param name The canonical extension name in question
         * @return Deserialization strategy to use
         */
        fun getAssertionRegistration(name: ExtensionName): DeserializationStrategy<ExtensionParameters>? {
            return registeredAssertionExtensions[name]
        }
    }

    /**
     * Get whether any applied [Extension] requires a [KeyAgreementPlatformKey].
     *
     * @return true if required; false if not
     */
    fun isKeyAgreementRequired(): Boolean {
        for (extension in appliedExtensions) {
            if (isKeyAgreementRequired(extension.getName())) {
                return true
            }
        }
        return false
    }

    /**
     * Invoke every registered [Extension] to collect [parameters][ExtensionParameters] to attach to a
     * [MakeCredentialCommand].
     *
     * @param keyAgreement The Authenticator-Platform key agreement in use, if required
     * @param pinUVProtocol The PIN/UV protocol in use, if required
     * @return A map keyed by each [Extension.getName], and values set to the result of [Extension.makeCredential].
     */
    fun makeCredential(
        keyAgreement: KeyAgreementPlatformKey?,
        pinUVProtocol: PinUVProtocol?,
    ): Map<ExtensionName, ExtensionParameters> {
        val ret = hashMapOf<ExtensionName, ExtensionParameters>()
        for (extension in appliedExtensions) {
            val params = extension.makeCredential(keyAgreement, pinUVProtocol)
            if (params != null) {
                ret[extension.getName()] = params
            }
        }
        return ret
    }

    /**
     * Invoke every registered [Extension] to collect [parameters][ExtensionParameters] to attach to a
     * [GetAssertionCommand].
     *
     * @param keyAgreement The Authenticator-Platform key agreement in use, if required
     * @param pinUVProtocol The PIN/UV protocol in use, if required
     * @return A map keyed by each [Extension.getName], and values set to the result of [Extension.getAssertion].
     */
    fun getAssertion(
        keyAgreement: KeyAgreementPlatformKey?,
        pinUVProtocol: PinUVProtocol?,
    ): Map<ExtensionName, ExtensionParameters> {
        val ret = hashMapOf<ExtensionName, ExtensionParameters>()
        for (extension in appliedExtensions) {
            val params = extension.getAssertion(keyAgreement, pinUVProtocol)
            if (params != null) {
                ret[extension.getName()] = params
            }
        }
        return ret
    }

    /**
     * Invoke every registered [Extension] to extract data from a [MakeCredentialResponse].
     *
     * @param response The Authenticator's response object
     */
    fun makeCredentialResponse(response: MakeCredentialResponse) {
        for (extension in appliedExtensions) {
            extension.makeCredentialResponse(response)
        }
    }

    /**
     * Invoke every registered [Extension] to extract data from a [GetAssertionResponse].
     *
     * @param response The Authenticator's response object
     */
    fun getAssertionResponse(response: GetAssertionResponse) {
        for (extension in appliedExtensions) {
            extension.getAssertionResponse(response)
        }
    }

    /**
     * Check whether all requested [Extension]s are supported.
     *
     * @param info The result of a [GetInfoCommand] to the Authenticator
     * @return true if all extensions are supported; false if any are not
     */
    fun checkSupport(info: GetInfoResponse): Boolean {
        for (extension in appliedExtensions) {
            if (!extension.checkSupport(info)) {
                return false
            }
        }
        return true
    }
}
