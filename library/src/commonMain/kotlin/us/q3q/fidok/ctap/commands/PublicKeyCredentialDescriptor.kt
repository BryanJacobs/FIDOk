package us.q3q.fidok.ctap.commands

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ArraySerializer
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

enum class AuthenticatorTransports(val value: String) {
    USB("usb"),
    NFC("nfc"),
    BLE("ble"),
    SMART_CARD("smart-card"),
    HYBRID("hybrid"),
    INTERNAL("internal"),
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable(with = PublicKeyCredentialDescriptorSerializer::class)
data class PublicKeyCredentialDescriptor(val type: String, @ByteString val id: ByteArray, val transports: List<String>? = null) {
    constructor(id: ByteArray) : this(PublicKeyCredentialType.PUBLIC_KEY.value, id)

    constructor(type: PublicKeyCredentialType, id: ByteArray, transports: List<AuthenticatorTransports>? = null) :
        this(type.value, id, transports?.map { it.value })

    init {
        require(id.isNotEmpty())
        require(type.isNotEmpty())
    }

    fun getKnownTransports(): List<AuthenticatorTransports>? {
        return transports?.mapNotNull { stringTransport ->
            AuthenticatorTransports.entries.find {
                it.value == stringTransport
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as PublicKeyCredentialDescriptor

        if (type != other.type) return false
        if (!id.contentEquals(other.id)) return false
        if (transports != other.transports) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + id.contentHashCode()
        result = 31 * result + (transports?.hashCode() ?: 0)
        return result
    }
}

@Serializable
data class PublicKeyCredentialListParameter(override val v: List<PublicKeyCredentialDescriptor>) : ParameterValue()

@OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
class PublicKeyCredentialDescriptorSerializer : KSerializer<PublicKeyCredentialDescriptor> {
    override val descriptor: SerialDescriptor
        get() = buildSerialDescriptor("PublicKeyCredentialDescriptor", StructureKind.MAP) {
            element("className", String.serializer().descriptor)
            element("id_key", String.serializer().descriptor)
            element("id", ByteArraySerializer().descriptor)
            element("type_key", String.serializer().descriptor)
            element("type", String.serializer().descriptor)
            element("transports_key", String.serializer().descriptor, isOptional = true)
            element("transports", ArraySerializer(String.serializer()).descriptor, isOptional = true)
        }

    override fun deserialize(decoder: Decoder): PublicKeyCredentialDescriptor {
        val composite = decoder.beginStructure(descriptor)
        val numItems = composite.decodeCollectionSize(descriptor)
        if (numItems < 2) {
            throw SerializationException("Decoded a PublicKeyCredentialDescriptor with too few elements")
        }

        var id: ByteArray? = null
        var type: String? = null
        var transports: Array<String>? = null

        for (i in 1..numItems) {
            val key = decoder.decodeString()
            when (key) {
                "id" -> {
                    id = decoder.decodeSerializableValue(ByteArraySerializer())
                }
                "type" -> {
                    type = decoder.decodeString()
                }
                "transports" -> {
                    transports = decoder.decodeSerializableValue(ArraySerializer(String.serializer()))
                }
                else -> {
                    // Unknown property
                    // TODO: handle other value types
                    decoder.decodeString()
                }
            }
        }

        composite.endStructure(descriptor)

        if (id == null) {
            throw SerializationException("Deserialized a PublicKeyCredentialDescriptor with no ID")
        }
        if (type == null) {
            throw SerializationException("Deserialized a PublicKeyCredentialDescriptor with no type")
        }

        return PublicKeyCredentialDescriptor(
            type = type,
            id = id,
            transports = transports?.toList(),
        )
    }

    override fun serialize(encoder: Encoder, value: PublicKeyCredentialDescriptor) {
        var size = 2
        if (value.transports != null) {
            size++
        }
        val subEncoder = encoder.beginCollection(descriptor, size)
        subEncoder.encodeStringElement(descriptor, 1, "id")
        subEncoder.encodeSerializableElement(descriptor, 2, ByteArraySerializer(), value.id)
        subEncoder.encodeStringElement(descriptor, 3, "type")
        subEncoder.encodeStringElement(descriptor, 4, value.type)
        if (value.transports != null) {
            subEncoder.encodeStringElement(descriptor, 5, "transports")
            subEncoder.encodeSerializableElement(
                descriptor,
                6,
                ListSerializer(String.serializer()),
                value.transports,
            )
        }
        subEncoder.endStructure(descriptor)
    }
}

@Serializable
data class PublicKeyCredentialDescriptorParameter(override val v: PublicKeyCredentialDescriptor) : ParameterValue()
