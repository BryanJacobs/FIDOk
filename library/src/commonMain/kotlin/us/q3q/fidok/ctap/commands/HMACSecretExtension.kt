package us.q3q.fidok.ctap.commands

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import us.q3q.fidok.crypto.KeyAgreementPlatformKey
import us.q3q.fidok.crypto.PinUVProtocol
import kotlin.random.Random

/**
 * Implements the CTAP2.1 HMAC-Secret extension
 *
 * This relatively complex extension uses an Authenticator-held secret key associated with a
 * particular credential to HMAC-hash one or two given byte arrays, returning a deterministic
 * result. This can be used for turning a credential into a static encryption key!
 *
 * This extension object should be used to create a credential, and then used again (with salts)
 * on a [getAssertion call][us.q3q.fidok.ctap.CTAPClient.getAssertions] to retrieve the HMAC
 * value(s).
 *
 * Call [wasCreated] to check the extension was accepted by the Authenticator on creation; call
 * [getResult] to get the HMAC bytes.
 *
 * @param salt1 First salt to be HMAC-ed: must be 32 bytes long
 * @param salt2 Second salt to be HMAC-ed: optional, but if provided must be 32 bytes long
 *
 * @sample hmacSecretExtensionCreation
 * @sample hmacSecretExtensionUse
 */
class HMACSecretExtension(
    private val salt1: ByteArray? = null,
    private val salt2: ByteArray? = null,
) : Extension {
    companion object {
        private const val NAME = "hmac-secret"
    }

    init {
        require(salt1 == null || salt1.size == 32)
        require(salt2 == null || salt2.size == 32)
        require(salt2 == null || salt1 != null)

        ExtensionSetup.register(
            NAME,
            creationParameterDeserializer = BooleanExtensionParameter.serializer(),
            assertionParameterDeserializer = ByteArrayExtensionParameter.serializer(),
            requiresKeyAgreement = true,
        )
    }

    private var keyAgreement: KeyAgreementPlatformKey? = null
    private var pinUVProtocol: PinUVProtocol? = null
    private var created: Boolean = false
    private var results: ArrayDeque<Pair<ByteArray?, ByteArray?>> = ArrayDeque()

    fun getResult(): Pair<ByteArray?, ByteArray?> {
        return results.removeFirst()
    }

    fun wasCreated(): Boolean {
        return created
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

    override fun getAssertion(
        keyAgreement: KeyAgreementPlatformKey?,
        pinUVProtocol: PinUVProtocol?,
    ): ExtensionParameters {
        if (keyAgreement == null || pinUVProtocol == null) {
            throw IllegalStateException("hmac-secret requires key agreement")
        }
        if (salt1 == null) {
            throw IllegalArgumentException("Can't get an HMAC-secret assertion with no salt")
        }
        this.keyAgreement = keyAgreement
        this.pinUVProtocol = pinUVProtocol

        val salt =
            if (salt2 == null) {
                salt1
            } else {
                (salt1.toList() + salt2.toList()).toByteArray()
            }

        val saltEnc = pinUVProtocol.encrypt(keyAgreement, salt)
        val saltAuth = pinUVProtocol.authenticate(keyAgreement, saltEnc)

        return HMACSecretExtensionParameter(
            keyAgreement.getCOSE(),
            saltEnc = saltEnc,
            saltAuth = saltAuth,
            pinUvAuthProtocol = pinUVProtocol.getVersion(),
        )
    }

    override fun makeCredentialResponse(response: MakeCredentialResponse) {
        val gotten =
            response.authData.extensions?.get(getName())
                ?: return
        created = (gotten as BooleanExtensionParameter).v
    }

    override fun getAssertionResponse(response: GetAssertionResponse) {
        val gotten = response.authData.extensions?.get(getName())
        if (gotten == null) {
            results.addLast(null to null)
            return
        }
        val pp = pinUVProtocol
        val ka = keyAgreement
        if (pp == null || ka == null) {
            throw IllegalStateException("hmac-secret response parsing requires key agreement")
        }

        val result = pp.decrypt(ka, (gotten as ByteArrayExtensionParameter).v)

        if (result.size != 32 && result.size != 64) {
            throw IllegalStateException("hmac-secret response was a strange length: ${result.size}")
        }

        val res1 = result.copyOfRange(0, 32)
        var res2: ByteArray? = null
        if (result.size == 64) {
            res2 = result.copyOfRange(32, 64)
        }
        results.addLast(res1 to res2)
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable(with = HMACSecretInputSerializer::class)
data class HMACSecretExtensionParameter(
    val keyAgreement: COSEKey,
    @ByteString val saltEnc: ByteArray,
    @ByteString val saltAuth: ByteArray,
    val pinUvAuthProtocol: UByte?,
) : ExtensionParameters() {
    init {
        require(saltEnc.size == 32 || saltEnc.size == 48 || saltEnc.size == 64 || saltEnc.size == 80)
        require(saltAuth.size == 16 || saltAuth.size == 32)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as HMACSecretExtensionParameter

        if (keyAgreement != other.keyAgreement) return false
        if (!saltEnc.contentEquals(other.saltEnc)) return false
        if (!saltAuth.contentEquals(other.saltAuth)) return false
        if (pinUvAuthProtocol != other.pinUvAuthProtocol) return false

        return true
    }

    override fun hashCode(): Int {
        var result = keyAgreement.hashCode()
        result = 31 * result + saltEnc.contentHashCode()
        result = 31 * result + saltAuth.contentHashCode()
        result = 31 * result + (pinUvAuthProtocol?.hashCode() ?: 0)
        return result
    }
}

class HMACSecretInputSerializer : KSerializer<HMACSecretExtensionParameter> {
    override val descriptor: SerialDescriptor
        get() =
            buildClassSerialDescriptor("HMACSecretInput") {
                element("keyAgreement", COSEKey.serializer().descriptor)
                element("saltEnc", ByteArraySerializer().descriptor)
                element("saltAuth", ByteArraySerializer().descriptor)
                element("pinUvAuthProtocol", UInt.serializer().descriptor, isOptional = true)
            }

    override fun deserialize(decoder: Decoder): HMACSecretExtensionParameter {
        throw NotImplementedError("Cannot deserialize an HMAC secret INput")
    }

    override fun serialize(
        encoder: Encoder,
        value: HMACSecretExtensionParameter,
    ) {
        var numElements = 3
        val puv = value.pinUvAuthProtocol
        if (puv != null) {
            numElements++
        }
        val composite = encoder.beginCollection(descriptor, numElements)

        composite.encodeIntElement(descriptor, 0, 0x01)
        composite.encodeSerializableElement(descriptor, 0, COSEKey.serializer(), value.keyAgreement)
        composite.encodeIntElement(descriptor, 1, 0x02)
        composite.encodeSerializableElement(descriptor, 1, ByteArraySerializer(), value.saltEnc)
        composite.encodeIntElement(descriptor, 2, 0x03)
        composite.encodeSerializableElement(descriptor, 2, ByteArraySerializer(), value.saltAuth)
        if (puv != null) {
            composite.encodeIntElement(descriptor, 3, 0x04)
            composite.encodeIntElement(descriptor, 3, puv.toInt())
        }

        composite.endStructure(descriptor)
    }
}

@Suppress("UNUSED_VARIABLE")
internal fun hmacSecretExtensionCreation() {
    val client = Examples.getCTAPClient()

    val hmacSecretExtension = HMACSecretExtension()
    val credential =
        client.makeCredential(
            rpId = "some.cool.example",
            extensions = ExtensionSetup(listOf(hmacSecretExtension)),
        )

    if (hmacSecretExtension.wasCreated()) {
        println("Rejoice!")
    }
}

@Suppress("UNUSED_VARIABLE")
internal fun hmacSecretExtensionUse() {
    val client = Examples.getCTAPClient()

    val hmacSecretExtension =
        HMACSecretExtension(
            salt1 = Random.nextBytes(32),
            salt2 = Random.nextBytes(32),
        )
    val assertions =
        client.getAssertions(
            rpId = "some.awesome.example",
            extensions = ExtensionSetup(listOf(hmacSecretExtension)),
        )

    for (assertion in assertions) {
        val result = hmacSecretExtension.getResult()
        val firstHMAC = result.first
        val secondHMAC = result.second
    }
}
