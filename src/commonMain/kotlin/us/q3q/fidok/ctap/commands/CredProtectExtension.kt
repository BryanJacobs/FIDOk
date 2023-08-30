package us.q3q.fidok.ctap.commands

import us.q3q.fidok.crypto.KeyAgreementPlatformKey
import us.q3q.fidok.crypto.PinProtocol

class CredProtectExtension(private val requestedLevel: UByte) : Extension {

    private val NAME = "credProtect"

    init {
        ExtensionSetup.register(NAME, creationParameterDeserializer = IntExtensionParameter.serializer())
    }

    private var gottenLevel: UByte? = null

    fun getLevel(): UByte? {
        return gottenLevel
    }

    override fun getName(): ExtensionName {
        return NAME
    }

    override fun makeCredential(keyAgreement: KeyAgreementPlatformKey?, pinProtocol: PinProtocol?): ExtensionParameters {
        return IntExtensionParameter(requestedLevel.toInt())
    }

    override fun makeCredentialResponse(response: MakeCredentialResponse) {
        val gotten = response.authData.extensions?.get(getName())
        gottenLevel = (gotten as IntExtensionParameter).v.toUByte()
    }
}
