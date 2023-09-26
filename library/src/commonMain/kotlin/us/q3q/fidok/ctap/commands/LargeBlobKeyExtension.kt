package us.q3q.fidok.ctap.commands

import us.q3q.fidok.crypto.KeyAgreementPlatformKey
import us.q3q.fidok.crypto.PinUVProtocol

/**
 * Allows for getting a key to store/retrieve authenticator "large blobs"
 *
 * Requires no parameters; on each [makeCredential][us.q3q.fidok.ctap.CTAPClient.makeCredential]
 * or [getAssertion][us.q3q.fidok.ctap.CTAPClient.getAssertions] operation, will retrieve the
 * corresponding `largeBlobKey` if available.
 *
 * Note that each call to [getKey] removes one accumulated entry, and entries are added by
 * (and shared between) both `makeCredential` and `getAssertion`.
 *
 * @sample largeBlobKeyExample
 */
class LargeBlobKeyExtension : Extension {

    private val NAME = "largeBlobKey"

    init {
        ExtensionSetup.register(NAME)
    }

    private var largeBlobKeys: ArrayDeque<ByteArray?> = ArrayDeque()

    fun getKey(): ByteArray? = largeBlobKeys.removeFirst()

    override fun getName(): ExtensionName {
        return NAME
    }

    override fun makeCredential(keyAgreement: KeyAgreementPlatformKey?, pinUVProtocol: PinUVProtocol?): ExtensionParameters? {
        return BooleanExtensionParameter(true)
    }

    override fun makeCredentialResponse(response: MakeCredentialResponse) {
        // Note: doesn't come from extension results per se
        largeBlobKeys.addLast(response.largeBlobKey)
    }

    override fun getAssertion(keyAgreement: KeyAgreementPlatformKey?, pinUVProtocol: PinUVProtocol?): ExtensionParameters {
        return BooleanExtensionParameter(true)
    }

    override fun getAssertionResponse(response: GetAssertionResponse) {
        largeBlobKeys.addLast(response.largeBlobKey)
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

internal fun largeBlobKeyExample() {
    val client = Examples.getCTAPClient()

    val largeBlobKeyExtension = LargeBlobKeyExtension()
    val credential = client.makeCredential(
        rpId = "some.neat.example",
        extensions = ExtensionSetup(listOf(largeBlobKeyExtension)),
    )

    val largeBlobKey = largeBlobKeyExtension.getKey()
}
