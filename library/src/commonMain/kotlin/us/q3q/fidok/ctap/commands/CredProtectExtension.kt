package us.q3q.fidok.ctap.commands

import us.q3q.fidok.crypto.KeyAgreementPlatformKey
import us.q3q.fidok.crypto.PinUVProtocol

/**
 * Attaches a CTAP `credProtect` request to newly created credentials.
 *
 * A "protected" credential is prevented by the Authenticator from begin used or discovered
 * without user verification (either via a PIN or an onboard verification method) depending on its
 * level:
 *
 * - Level 1 credentials are usable normally
 * - Level 2 credentials may be used without PIN/UV when they're passed to the authenticator via
 *   [GetAssertionCommand.allowList]
 * - Level 3 credentials always require a PIN or UV to discover or use
 *
 * This extension is only effective on creating a credential. After the credential is created, call [getLevel]
 * to see what level of protection (if any) was applied: the Authenticator may choose to apply a different
 * level than the one requested - lower, or even higher!
 *
 * @param requestedLevel The requested credential protection level
 */
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

    override fun makeCredential(keyAgreement: KeyAgreementPlatformKey?, pinUVProtocol: PinUVProtocol?): ExtensionParameters {
        return IntExtensionParameter(requestedLevel.toInt())
    }

    override fun makeCredentialResponse(response: MakeCredentialResponse) {
        val gotten = response.authData.extensions?.get(getName())
        gottenLevel = (gotten as IntExtensionParameter).v.toUByte()
    }
}
