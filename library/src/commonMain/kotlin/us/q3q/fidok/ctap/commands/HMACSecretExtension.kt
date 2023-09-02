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
import us.q3q.fidok.crypto.PinProtocol

class HMACSecretExtension(
    private val salt1: ByteArray,
    private val salt2: ByteArray? = null,
) : Extension {

    private val NAME = "hmac-secret"

    init {
        ExtensionSetup.register(
            NAME,
            creationParameterDeserializer = BooleanExtensionParameter.serializer(),
            assertionParameterDeserializer = ByteArrayExtensionParameter.serializer(),
            requiresKeyAgreement = true,
        )
    }

    private var gottenBytes: UByte? = null
    private var keyAgreement: KeyAgreementPlatformKey? = null
    private var pinProtocol: PinProtocol? = null
    private var created: Boolean = false
    private var res1: ByteArray? = null
    private var res2: ByteArray? = null

    fun getFirstResult(): ByteArray? {
        return res1
    }
    fun getSecondResult(): ByteArray? {
        return res2
    }
    fun wasCreated(): Boolean {
        return created
    }

    override fun getName(): ExtensionName {
        return NAME
    }

    override fun makeCredential(
        keyAgreement: KeyAgreementPlatformKey?,
        pinProtocol: PinProtocol?,
    ): ExtensionParameters {
        return BooleanExtensionParameter(true)
    }

    override fun getAssertion(keyAgreement: KeyAgreementPlatformKey?, pinProtocol: PinProtocol?): ExtensionParameters {
        if (keyAgreement == null || pinProtocol == null) {
            throw IllegalStateException("hmac-secret requires key agreement")
        }
        this.keyAgreement = keyAgreement
        this.pinProtocol = pinProtocol

        val salt = if (salt2 == null) {
            salt1
        } else {
            (salt1.toList() + salt2.toList()).toByteArray()
        }

        val saltEnc = pinProtocol.encrypt(keyAgreement, salt)
        val saltAuth = pinProtocol.authenticate(keyAgreement, saltEnc)

        return HMACSecretExtensionParameter(
            keyAgreement.getCOSE(),
            saltEnc = saltEnc,
            saltAuth = saltAuth,
            pinUvAuthProtocol = pinProtocol.getVersion(),
        )
    }

    override fun makeCredentialResponse(response: MakeCredentialResponse) {
        val gotten = response.authData.extensions?.get(getName())
            ?: return
        created = (gotten as BooleanExtensionParameter).v
    }

    override fun getAssertionResponse(response: GetAssertionResponse) {
        val gotten = response.authData.extensions?.get(getName())
            ?: return
        val pp = pinProtocol
        val ka = keyAgreement
        if (pp == null || ka == null) {
            throw IllegalStateException("hmac-secret response parsing requires key agreement")
        }

        val result = pp.decrypt(ka, (gotten as ByteArrayExtensionParameter).v)

        if (result.size != 32 && result.size != 64) {
            throw IllegalStateException("hmac-secret response was a strange length: ${result.size}")
        }

        res1 = result.copyOfRange(0, 32)
        if (result.size == 64) {
            res2 = result.copyOfRange(32, 64)
        }
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
        require(saltEnc.size == 32 || saltEnc.size == 48 || saltEnc.size == 64 || saltEnc.size == 70)
        require(saltAuth.size == 16 || saltAuth.size == 32)
    }
}

class HMACSecretInputSerializer : KSerializer<HMACSecretExtensionParameter> {
    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("HMACSecretInput") {
            element("keyAgreement", COSEKey.serializer().descriptor)
            element("saltEnc", ByteArraySerializer().descriptor)
            element("saltAuth", ByteArraySerializer().descriptor)
            element("pinUvAuthProtocol", UInt.serializer().descriptor, isOptional = true)
        }

    override fun deserialize(decoder: Decoder): HMACSecretExtensionParameter {
        throw NotImplementedError("Cannot deserialize an HMAC secret INput")
    }

    override fun serialize(encoder: Encoder, value: HMACSecretExtensionParameter) {
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
