package us.q3q.fidok.ctap.commands

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Represents information about a Relying Party being passed to/from an Authenticator.
 *
 * @property id The Relying Party ID. This will generally be a string like `something.example.com`.
 * @property name The Relying Party "name" for display to a human: this generally isn't used for anything important
 * in the CTAP standards, and might even be truncated from its original value
 * @property icon Relying Parties used to be able to have a display icon, but this is deprecated and should not be used
 */
@Serializable(with = PKCredentialRpEntitySerializer::class)
data class PublicKeyCredentialRpEntity(
    val id: String? = null,
    val name: String? = null,
    @Deprecated("webauthn-3 does not handle icons for RPs") val icon: String? = null,
)

@Serializable
data class PublicKeyCredentialRpEntityParameter(override val v: PublicKeyCredentialRpEntity) : ParameterValue()

/**
 * Serializes and/or deserializes a [PublicKeyCredentialRpEntity] to/from CTAP CBOR.
 */
@OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
class PKCredentialRpEntitySerializer : KSerializer<PublicKeyCredentialRpEntity> {
    override val descriptor: SerialDescriptor
        get() = buildSerialDescriptor("PublicKeyCredentialRpEntity", StructureKind.MAP) {
            element("className", String.serializer().descriptor)
            element("id_key", String.serializer().descriptor)
            element("id", String.serializer().descriptor)
            element("icon_key", String.serializer().descriptor, isOptional = true)
            element("icon", String.serializer().descriptor, isOptional = true)
            element("name_key", String.serializer().descriptor, isOptional = true)
            element("name", String.serializer().descriptor, isOptional = true)
        }

    override fun deserialize(decoder: Decoder): PublicKeyCredentialRpEntity {
        val map = decoder.decodeSerializableValue(
            MapSerializer(
                String.serializer(),
                String.serializer(),
            ),
        )
        val id = map["id"]
        return PublicKeyCredentialRpEntity(
            id = id,
            icon = map["icon"],
            name = map["name"],
        )
    }

    override fun serialize(encoder: Encoder, value: PublicKeyCredentialRpEntity) {
        if (value.id == null) {
            throw IllegalArgumentException("Cannot serialize a PublicKeyCredentialRpEntity with a null ID")
        }
        var size = 1
        if (value.name != null) {
            size++
        }
        @Suppress("DEPRECATION")
        if (value.icon != null) {
            size++
        }
        val subEncoder = encoder.beginCollection(descriptor, size)
        subEncoder.encodeStringElement(descriptor, 1, "id")
        subEncoder.encodeStringElement(descriptor, 2, value.id)
        @Suppress("DEPRECATION")
        if (value.icon != null) {
            subEncoder.encodeStringElement(descriptor, 3, "icon")
            subEncoder.encodeStringElement(descriptor, 4, value.icon)
        }
        if (value.name != null) {
            subEncoder.encodeStringElement(descriptor, 5, "name")
            subEncoder.encodeStringElement(descriptor, 6, value.name)
        }
        subEncoder.endStructure(descriptor)
    }
}
