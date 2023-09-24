package us.q3q.fidok.ctap.commands

import us.q3q.fidok.crypto.KeyAgreementPlatformKey
import us.q3q.fidok.crypto.PinUVProtocol

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
