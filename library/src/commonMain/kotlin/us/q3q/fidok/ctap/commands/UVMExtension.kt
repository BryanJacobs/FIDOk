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
import us.q3q.fidok.crypto.PinUVProtocol

/**
 * Implements the Webauthn-2 User Verification Management extension.
 *
 * This allows a Relying Party to obtain, on creating a new credential, details
 * about how the Authenticator protects that credential. It has no effect when
 * getting an assertion.
 *
 * @sample uvmExtensionUsage
 */
class UVMExtension : Extension {
    companion object {
        private const val NAME = "uvm"
    }

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

    override fun makeCredential(
        keyAgreement: KeyAgreementPlatformKey?,
        pinUVProtocol: PinUVProtocol?,
    ): ExtensionParameters {
        return BooleanExtensionParameter(true)
    }

    override fun makeCredentialResponse(response: MakeCredentialResponse) {
        val gotten = response.authData.extensions?.get(getName())
        gottenUVMEntries = (gotten as UVMExtensionResultParameter).v
    }
}

@Serializable(with = UVMEntrySerializer::class)
data class UVMEntry(val userVerificationMethod: Int, val keyProtectionType: Int, val matcherProtectionType: Int) {
    fun decodeKeyProtectionType(): KeyProtectionType? =
        KeyProtectionType.entries.find {
            it.v == keyProtectionType
        }

    fun decodeMatcherProtectionType(): MatcherProtectionType? =
        MatcherProtectionType.entries.find {
            it.v == matcherProtectionType
        }

    fun decodeUserVerificationMethod(): UserVerificationMethod? =
        UserVerificationMethod.entries.find {
            it.v == userVerificationMethod
        }
}

class UVMEntrySerializer : KSerializer<UVMEntry> {
    override val descriptor: SerialDescriptor
        get() =
            buildClassSerialDescriptor("UVMEntry") {
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

    override fun serialize(
        encoder: Encoder,
        value: UVMEntry,
    ) {
        throw NotImplementedError("Cannot serialize a UVMEntry")
    }
}

@Serializable(with = UVMExtensionResultSerializer::class)
class UVMExtensionResultParameter(val v: List<UVMEntry>) : ExtensionParameters()

/**
 * Ways an Authenticator can verify that the "right" human is interacting with it.
 *
 * @property v The FIDO canonical integer representing the user verification method
 */
enum class UserVerificationMethod(val v: Int) {
    /**
     * Verify somebody is present
     */
    USER_VERIFY_PRESENCE(0x00000001),

    /**
     * Check the unique-ish pattern on the end of a human finger
     */
    USER_VERIFY_FINGERPRINT(0x00000002),

    /**
     * Check the user can remember some sequence of characters
     */
    USER_VERIFY_PASSCODE(0x00000004),

    /**
     * Check the user's voice sounds familiar-ish
     */
    USER_VERIFY_VOICEPRINT(0x00000008),

    /**
     * Check an image of the user's frontal head area
     */
    USER_VERIFY_FACEPRINT(0x00000010),

    /**
     * Check a location sensor - this one isn't so much about WHO is interacting with the Authenticator, but rather
     * WHERE the interaction is happening
     */
    USER_VERIFY_LOCATION(0x00000020),

    /**
     * Check an image of one side of a human eyeball
     */
    USER_VERIFY_EYEPRINT(0x00000040),

    /**
     * Check the user can remember a geometric pattern. Conceptually identical to a passcode
     */
    USER_VERIFY_PATTERN(0x00000080),

    /**
     * Check an image of a human hand
     */
    USER_VERIFY_HANDPRINT(0x00000100),

    /**
     * No user verification supported/performed
     */
    USER_VERIFY_NONE(0x00000200),

    /**
     * All possible methods. No, just joking, this represents a combination of many methods.
     */
    USER_VERIFY_ALL(0x00000400),
}

/**
 * How an Authenticator keeps a private key safe.
 *
 * @property v The FIDO canonical integer representing the key protection method
 */
enum class KeyProtectionType(val v: Int) {
    /**
     * The key is protected by a sequence of instructions executing on a general-purpose computer
     */
    KEY_PROTECTION_SOFTWARE(0x0001),

    /**
     * The key is protected by some physical object designed to protect it
     */
    KEY_PROTECTION_HARDWARE(0x0002),

    /**
     * The key is protected by some software that's VERY SPECIAL software
     */
    KEY_PROTECTION_TEE(0x0004),

    /**
     * The key is protected by some hardware that's VERY SPECIAL hardware
     */
    KEY_PROTECTION_SECURE_ELEMENT(0x0008),

    /**
     * The key is protected by virtue of being stored elsewhere, where it's someone else's problem
     */
    KEY_PROTECTION_REMOTE_HANDLE(0x0010),
}

/**
 * How an Authenticator ensures that a user verification `true` result hasn't been tampered with.
 *
 * @property v The FIDO canonical integer representing the matcher protection type
 */
enum class MatcherProtectionType(val v: Int) {
    /**
     * The Authenticator checks the User Verification using a series of instructions to a general-purpose computer
     */
    MATCHER_PROTECTION_SOFTWARE(0x0001),

    /**
     * The Authenticator checks the User Verification using a series of VERY SPECIAL instructions running on a computer
     */
    MATCHER_PROTECTION_TEE(0x0002),

    /**
     * The Authenticator checks the User Verification using a series of instructions running on a VERY SPECIAL computer.
     *
     * Or actually a dedicated chip.
     */
    MATCHER_PROTECTION_ON_CHIP(0x0004),
}

/**
 * Deserializes the [UVM extension response][UVMExtensionResultParameter] from incoming CBOR.
 */
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

    override fun serialize(
        encoder: Encoder,
        value: UVMExtensionResultParameter,
    ) {
        throw NotImplementedError("Cannot serialize a UVM *result*")
    }
}

@Suppress("UNUSED_VARIABLE")
internal fun uvmExtensionUsage(): String {
    val client = Examples.getCTAPClient()

    val uvmExtension = UVMExtension()
    val credential =
        client.makeCredential(
            rpId = "some.groovy.example",
            extensions = ExtensionSetup(listOf(uvmExtension)),
        )

    val entries =
        uvmExtension.getUVMEntries()
            ?: return "credential unprotected"

    for (entry in entries) {
        println("Key protection: ${entry.decodeKeyProtectionType()}")
        println("Matcher protection: ${entry.decodeMatcherProtectionType()}")
        println("User verification: ${entry.decodeUserVerificationMethod()}")
    }

    return "adequately protected"
}
