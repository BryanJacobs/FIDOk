package us.q3q.fidok.ctap.commands

import kotlinx.serialization.DeserializationStrategy
import us.q3q.fidok.crypto.KeyAgreementPlatformKey
import us.q3q.fidok.crypto.PinProtocol

interface Extension {
    fun getName(): ExtensionName
    fun makeCredential(keyAgreement: KeyAgreementPlatformKey?, pinProtocol: PinProtocol?): ExtensionParameters? = null
    fun getAssertion(keyAgreement: KeyAgreementPlatformKey?, pinProtocol: PinProtocol?): ExtensionParameters? = null

    fun makeCredentialResponse(response: MakeCredentialResponse) {}

    fun getAssertionResponse(response: GetAssertionResponse) {}

    fun checkSupport(info: GetInfoResponse) {
        val name = getName()
        if (info.extensions?.contains(name) != true) {
            throw IllegalArgumentException("$name extension not supported")
        }
    }
}

class ExtensionSetup(private val appliedExtensions: List<Extension>) {

    companion object {
        private val registeredCreationExtensions = hashMapOf<ExtensionName, DeserializationStrategy<ExtensionParameters>>()
        private val registeredAssertionExtensions = hashMapOf<ExtensionName, DeserializationStrategy<ExtensionParameters>>()
        private var keyAgreementRequiredExtensions = hashMapOf<ExtensionName, Boolean>()

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

        fun isKeyAgreementRequired(name: ExtensionName): Boolean {
            return keyAgreementRequiredExtensions[name] == true
        }

        fun getCreationRegistration(name: ExtensionName): DeserializationStrategy<ExtensionParameters>? {
            return registeredCreationExtensions[name]
        }

        fun getAssertionRegistration(name: ExtensionName): DeserializationStrategy<ExtensionParameters>? {
            return registeredAssertionExtensions[name]
        }
    }

    fun isKeyAgreementRequired(): Boolean {
        for (extension in appliedExtensions) {
            if (isKeyAgreementRequired(extension.getName())) {
                return true
            }
        }
        return false
    }

    fun makeCredential(keyAgreement: KeyAgreementPlatformKey?, pinProtocol: PinProtocol?): Map<ExtensionName, ExtensionParameters> {
        val ret = hashMapOf<ExtensionName, ExtensionParameters>()
        for (extension in appliedExtensions) {
            val params = extension.makeCredential(keyAgreement, pinProtocol)
            if (params != null) {
                ret[extension.getName()] = params
            }
        }
        return ret
    }

    fun getAssertion(keyAgreement: KeyAgreementPlatformKey?, pinProtocol: PinProtocol?): Map<ExtensionName, ExtensionParameters> {
        val ret = hashMapOf<ExtensionName, ExtensionParameters>()
        for (extension in appliedExtensions) {
            val params = extension.getAssertion(keyAgreement, pinProtocol)
            if (params != null) {
                ret[extension.getName()] = params
            }
        }
        return ret
    }

    fun makeCredentialResponse(response: MakeCredentialResponse) {
        for (extension in appliedExtensions) {
            extension.makeCredentialResponse(response)
        }
    }

    fun getAssertionResponse(response: GetAssertionResponse) {
        for (extension in appliedExtensions) {
            extension.getAssertionResponse(response)
        }
    }

    fun checkSupport(info: GetInfoResponse) {
        for (extension in appliedExtensions) {
            extension.checkSupport(info)
        }
    }
}
