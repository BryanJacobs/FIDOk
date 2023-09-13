package us.q3q.fidok.ctap.commands

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.listSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import us.q3q.fidok.crypto.KeyAgreementPlatformKey
import us.q3q.fidok.crypto.PinProtocol

class UVMExtension : Extension {

    private val NAME = "uvm"

    init {
        ExtensionSetup.register(NAME, creationParameterDeserializer = UVMExtensionResultParameter.serializer())
    }

    private var gottenUVMEntries: List<UVMEntry>? = null

    fun getUVMEntries(): List<UVMEntry>? {
        return gottenUVMEntries
    }

    override fun getName(): ExtensionName {
        return NAME
    }

    override fun makeCredential(keyAgreement: KeyAgreementPlatformKey?, pinProtocol: PinProtocol?): ExtensionParameters {
        return BooleanExtensionParameter(true)
    }

    override fun makeCredentialResponse(response: MakeCredentialResponse) {
        val gotten = response.authData.extensions?.get(getName())
        gottenUVMEntries = (gotten as UVMExtensionResultParameter).v
    }
}

@Serializable(with = UVMEntrySerializer::class)
data class UVMEntry(val userVerificationMethod: Int, val keyProtectionType: Int, val matcherProtectionType: Int)

class UVMEntrySerializer : KSerializer<UVMEntry> {
    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("UVMEntry") {
            element("userVerificationMethod", Int.serializer().descriptor)
            element("keyProtectionType", Int.serializer().descriptor)
            element("matcherProtectionType", Int.serializer().descriptor)
        }

    override fun deserialize(decoder: Decoder): UVMEntry {
        val elements = decoder.decodeSerializableValue(ListSerializer(Int.serializer()))
        if (elements.size != 3) {
            throw SerializationException("UVMEntry doesn't contain three elements")
        }
        return UVMEntry(elements[0], elements[1], elements[2])
    }

    override fun serialize(encoder: Encoder, value: UVMEntry) {
        throw NotImplementedError("Cannot serialize a UVMEntry")
    }
}

@Serializable(with = UVMExtensionResultSerializer::class)
class UVMExtensionResultParameter(val v: List<UVMEntry>) : ExtensionParameters()

enum class UserVerificationMethods(val v: Int) {
    USER_VERIFY_PRESENCE(0x00000001),
    USER_VERIFY_FINGERPRINT(0x00000002),
    USER_VERIFY_PASSCODE(0x00000004),
    USER_VERIFY_VOICEPRINT(0x00000008),
    USER_VERIFY_FACEPRINT(0x00000010),
    USER_VERIFY_LOCATION(0x00000020),
    USER_VERIFY_EYEPRINT(0x00000040),
    USER_VERIFY_PATTERN(0x00000080),
    USER_VERIFY_HANDPRINT(0x00000100),
    USER_VERIFY_NONE(0x00000200),
    USER_VERIFY_ALL(0x00000400),
}

enum class KeyProtectionTypes(val v: Int) {
    KEY_PROTECTION_SOFTWARE(0x0001),
    KEY_PROTECTION_HARDWARE(0x0002),
    KEY_PROTECTION_TEE(0x0004),
    KEY_PROTECTION_SECURE_ELEMENT(0x0008),
    KEY_PROTECTION_REMOTE_HANDLE(0x0010),
}

enum class MatcherProtectionTypes(val v: Int) {
    MATCHER_PROTECTION_SOFTWARE(0x0001),
    MATCHER_PROTECTION_TEE(0x0002),
    MATCHER_PROTECTION_ON_CHIP(0x0004),
}

@OptIn(ExperimentalSerializationApi::class)
class UVMExtensionResultSerializer : KSerializer<UVMExtensionResultParameter> {
    override val descriptor: SerialDescriptor
        get() = listSerialDescriptor(UVMEntry.serializer().descriptor)

    override fun deserialize(decoder: Decoder): UVMExtensionResultParameter {
        val v = decoder.decodeSerializableValue(ListSerializer(UVMEntry.serializer()))
        if (v.size > 3) {
            throw SerializationException("UVM result contains >3 entries!")
        }
        return UVMExtensionResultParameter(v)
    }

    override fun serialize(encoder: Encoder, value: UVMExtensionResultParameter) {
        throw NotImplementedError("Cannot serialize a UVM *result*")
    }
}
