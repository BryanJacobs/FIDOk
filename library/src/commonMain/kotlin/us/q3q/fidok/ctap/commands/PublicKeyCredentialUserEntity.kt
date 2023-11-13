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

/**
 * Represents information about a user for a particular Relying Party, to be passed to/from an Authenticator.
 *
 * Note that this data structure isn't about the human using the Authenticator. It's more like a user account for a
 * web site.
 *
 * @property id The unique user ID, a byte string determined by the Relying Party
 * @property name The user account's name. Important to humans, not to the FIDO standard itself: this may be truncated
 * @property displayName The user's even more display-y name. While [name] might be something like "bob@example.com",
 * [displayName] might be something like "Bob Exampleson"
 * @property icon User objects used to be able to have an icon for showing humans; this is deprecated and should not
 * be used
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable(with = PublicKeyCredentialUserEntitySerializer::class)
data class PublicKeyCredentialUserEntity(
    @ByteString val id: ByteArray,
    val name: String? = null,
    val displayName: String? = null,
    @Deprecated("webauthn-3 does not handle icons for users") val icon: String? = null,
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
        if (name != other.name) return false
        if (displayName != other.displayName) return false
        @Suppress("DEPRECATION")
        if (icon != other.icon) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.contentHashCode()
        result = 31 * result + (name?.hashCode() ?: 0)
        result = 31 * result + (displayName?.hashCode() ?: 0)
        @Suppress("DEPRECATION")
        result = 31 * result + (icon?.hashCode() ?: 0)
        return result
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun toString(): String {
        @Suppress("DEPRECATION")
        return "PublicKeyCredentialUserEntity(name=$name, " +
            "id=${id.toHexString()}, " +
            "displayName=$displayName, " +
            "icon=$icon)"
    }
}

@Serializable
data class PublicKeyCredentialUserEntityParameter(override val v: PublicKeyCredentialUserEntity) : ParameterValue()

/**
 * Serializes and deserializes a [PublicKeyCredentialUserEntity] to/from CTAP CBOR.
 */
@OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
class PublicKeyCredentialUserEntitySerializer : KSerializer<PublicKeyCredentialUserEntity> {
    override val descriptor: SerialDescriptor
        get() =
            buildSerialDescriptor("PublicKeyCredentialsRpEntity", StructureKind.MAP) {
                element("className", String.serializer().descriptor)
                element("id_key", String.serializer().descriptor)
                element("id", ByteArraySerializer().descriptor)
                element("icon_key", String.serializer().descriptor, isOptional = true)
                element("icon", String.serializer().descriptor, isOptional = true)
                element("name_key", String.serializer().descriptor, isOptional = true)
                element("name", String.serializer().descriptor, isOptional = true)
                element("displayName_key", String.serializer().descriptor, isOptional = true)
                element("displayName", String.serializer().descriptor, isOptional = true)
            }

    override fun deserialize(decoder: Decoder): PublicKeyCredentialUserEntity {
        val composite = decoder.beginStructure(descriptor)
        val numElements = composite.decodeCollectionSize(descriptor)

        var id: ByteArray? = null
        var displayName: String? = null
        var icon: String? = null
        var name: String? = null

        for (i in 1..numElements) {
            when (val key = composite.decodeStringElement(descriptor, i)) {
                "id" ->
                    id = composite.decodeSerializableElement(descriptor, 2, ByteArraySerializer())
                "icon" ->
                    icon = composite.decodeStringElement(descriptor, 4)
                "name" ->
                    name = composite.decodeStringElement(descriptor, 6)
                "displayName" ->
                    displayName = composite.decodeStringElement(descriptor, 8)
                else ->
                    throw SerializationException("Unknown key in PublicKeyCredentialsUserEntity: $key")
            }
        }

        if (id == null) {
            throw SerializationException("PublicKeyCredentialsUserEntity missing id")
        }

        return PublicKeyCredentialUserEntity(id = id, displayName = displayName, icon = icon, name = name)
    }

    override fun serialize(
        encoder: Encoder,
        value: PublicKeyCredentialUserEntity,
    ) {
        var size = 1
        if (value.displayName != null) {
            size++
        }
        if (value.name != null) {
            size++
        }
        @Suppress("DEPRECATION")
        if (value.icon != null) {
            size++
        }
        val subEncoder = encoder.beginCollection(descriptor, size)
        subEncoder.encodeStringElement(descriptor, 1, "id")
        subEncoder.encodeSerializableElement(descriptor, 2, ByteArraySerializer(), value.id)
        @Suppress("DEPRECATION")
        if (value.icon != null) {
            subEncoder.encodeStringElement(descriptor, 3, "icon")
            subEncoder.encodeStringElement(descriptor, 4, value.icon)
        }
        if (value.name != null) {
            subEncoder.encodeStringElement(descriptor, 5, "name")
            subEncoder.encodeStringElement(descriptor, 6, value.name)
        }
        if (value.displayName != null) {
            subEncoder.encodeStringElement(descriptor, 7, "displayName")
            subEncoder.encodeStringElement(descriptor, 8, value.displayName)
        }
        subEncoder.endStructure(descriptor)
    }
}
