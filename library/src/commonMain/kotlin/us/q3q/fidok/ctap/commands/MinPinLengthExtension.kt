package us.q3q.fidok.ctap.commands

import us.q3q.fidok.crypto.KeyAgreementPlatformKey
import us.q3q.fidok.crypto.PinUVProtocol

/**
 * When an Authenticator supports the [setMinPinLength][us.q3q.fidok.ctap.CTAPOption.SET_MIN_PIN_LENGTH]
 * option, this extension may be used by particular Relying Parties to retrieve the configured minimum
 * PIN length on credential creation.
 *
 * @sample minPinLengthUsage
 */
class MinPinLengthExtension : Extension {

    private val NAME = "minPinLength"

    init {
        ExtensionSetup.register(NAME, creationParameterDeserializer = IntExtensionParameter.serializer())
    }

    private var gottenLength: UInt? = null

    fun getLength(): UInt? {
        return gottenLength
    }

    override fun getName(): ExtensionName {
        return NAME
    }

    override fun makeCredential(keyAgreement: KeyAgreementPlatformKey?, pinUVProtocol: PinUVProtocol?): ExtensionParameters {
        return BooleanExtensionParameter(true)
    }

    override fun makeCredentialResponse(response: MakeCredentialResponse) {
        val gotten = response.authData.extensions?.get(getName())
        gottenLength = (gotten as IntExtensionParameter).v.toUInt()
    }
}

internal fun minPinLengthUsage() {
    val client = Examples.getCTAPClient()

    val minPinLengthExtension = MinPinLengthExtension()
    val credential = client.makeCredential(
        rpId = "some.good.example",
        extensions = ExtensionSetup(listOf(minPinLengthExtension)),
    )

    val gottenMinPinLength = minPinLengthExtension.getLength()
}
