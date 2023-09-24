package us.q3q.fidok.ctap.commands

import us.q3q.fidok.crypto.KeyAgreementPlatformKey
import us.q3q.fidok.crypto.PinUVProtocol

class LargeBlobKeyExtension : Extension {

    private val NAME = "largeBlobKey"

    init {
        ExtensionSetup.register(NAME)
    }

    private var largeBlobKey: ByteArray? = null

    fun getKey(): ByteArray? = largeBlobKey

    override fun getName(): ExtensionName {
        return NAME
    }

    override fun makeCredential(keyAgreement: KeyAgreementPlatformKey?, pinUVProtocol: PinUVProtocol?): ExtensionParameters? {
        return BooleanExtensionParameter(true)
    }

    override fun makeCredentialResponse(response: MakeCredentialResponse) {
        // Note: doesn't come from extension results per se
        largeBlobKey = response.largeBlobKey
    }

    override fun getAssertion(keyAgreement: KeyAgreementPlatformKey?, pinUVProtocol: PinUVProtocol?): ExtensionParameters {
        return BooleanExtensionParameter(true)
    }

    override fun getAssertionResponse(response: GetAssertionResponse) {
        largeBlobKey = response.largeBlobKey
    }

    override fun checkSupport(info: GetInfoResponse): Boolean {
        if (!super.checkSupport(info)) {
            return false
        }
        if (info.options?.contains("largeBlobs") != true) {
            // largeBlobKey requires largeBlobs option
            return false
        }
        return true
    }
}
