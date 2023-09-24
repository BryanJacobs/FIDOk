package us.q3q.fidok.ctap.commands

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@OptIn(ExperimentalSerializationApi::class)
@Serializable(with = PublicKeyCredentialUserEntitySerializer::class)
data class PublicKeyCredentialUserEntity(
    @ByteString val id: ByteArray,
    val displayName: String? = null,
    val icon: String? = null,
) {
    init {
        require(id.isNotEmpty())
        require(id.size <= 64)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as PublicKeyCredentialUserEntity

        if (!id.contentEquals(other.id)) return false
        if (displayName != other.displayName) return false
        if (icon != other.icon) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.contentHashCode()
        result = 31 * result + (displayName?.hashCode() ?: 0)
        result = 31 * result + (icon?.hashCode() ?: 0)
        return result
    }
}

@Serializable
data class PublicKeyCredentialUserEntityParameter(override val v: PublicKeyCredentialUserEntity) : ParameterValue()

@OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
class PublicKeyCredentialUserEntitySerializer : KSerializer<PublicKeyCredentialUserEntity> {
    override val descriptor: SerialDescriptor
        get() = buildSerialDescriptor("PublicKeyCredentialsRpEntity", StructureKind.MAP) {
            element("className", String.serializer().descriptor)
            element("id_key", String.serializer().descriptor)
            element("id", ByteArraySerializer().descriptor)
            element("icon_key", String.serializer().descriptor, isOptional = true)
            element("icon", String.serializer().descriptor, isOptional = true)
            element("displayName_key", String.serializer().descriptor, isOptional = true)
            element("displayName", String.serializer().descriptor, isOptional = true)
        }

    override fun deserialize(decoder: Decoder): PublicKeyCredentialUserEntity {
        val composite = decoder.beginStructure(descriptor)
        val numElements = composite.decodeCollectionSize(descriptor)

        var id: ByteArray? = null
        var displayName: String? = null
        var icon: String? = null

        for (i in 1..numElements) {
            when (val key = composite.decodeStringElement(descriptor, i)) {
                "id" ->
                    id = composite.decodeSerializableElement(descriptor, 2, ByteArraySerializer())
                "icon" ->
                    icon = composite.decodeStringElement(descriptor, 4)
                "displayName" ->
                    displayName = composite.decodeStringElement(descriptor, 6)
                else ->
                    throw SerializationException("Unknown key in PublicKeyCredentialsUserEntity: $key")
            }
        }

        if (id == null) {
            throw SerializationException("PublicKeyCredentialsUserEntity missing id")
        }

        return PublicKeyCredentialUserEntity(id = id, displayName = displayName, icon = icon)
    }

    override fun serialize(encoder: Encoder, value: PublicKeyCredentialUserEntity) {
        if (value.displayName == null) {
            throw SerializationException("Cannot serialize a PublicKeyCredentialsUserEntity without a displayName")
        }
        var size = 2
        if (value.icon != null) {
            size++
        }
        val subEncoder = encoder.beginCollection(descriptor, size)
        subEncoder.encodeStringElement(descriptor, 1, "id")
        subEncoder.encodeSerializableElement(descriptor, 2, ByteArraySerializer(), value.id)
        if (value.icon != null) {
            subEncoder.encodeStringElement(descriptor, 3, "icon")
            subEncoder.encodeStringElement(descriptor, 4, value.icon)
        }
        subEncoder.encodeStringElement(descriptor, 5, "displayName")
        subEncoder.encodeStringElement(descriptor, 6, value.displayName)
        subEncoder.endStructure(descriptor)
    }
}
