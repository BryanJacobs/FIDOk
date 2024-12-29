package us.q3q.fidok.webauthn

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import us.q3q.fidok.ctap.commands.AttestationStatement

@Serializable(with = NoneAttestationStatementSerializer::class)
class NoneAttestationStatement : AttestationStatement()

@OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
class NoneAttestationStatementSerializer : KSerializer<NoneAttestationStatement> {
    override val descriptor =
        buildSerialDescriptor("attestationObject", StructureKind.MAP) {
            element("fmt_key", String.serializer().descriptor)
            element("fmt_val", String.serializer().descriptor)
            element("attStmt_key", String.serializer().descriptor)
            element("attStmt_val", MapSerializer(String.serializer(), String.serializer()).descriptor)
            element("authData_key", String.serializer().descriptor)
            element("authData_val", ByteArraySerializer().descriptor)
        }

    override fun deserialize(decoder: Decoder): NoneAttestationStatement {
        TODO("Not yet implemented")
    }

    override fun serialize(
        encoder: Encoder,
        value: NoneAttestationStatement,
    ) {
        val mapEncoder = encoder.beginCollection(descriptor, 3)
        mapEncoder.encodeStringElement(descriptor, 0, "fmt")
        mapEncoder.encodeStringElement(descriptor, 1, "none")
        mapEncoder.encodeStringElement(descriptor, 2, "attStmt")
        mapEncoder.encodeSerializableElement(descriptor, 3, EmptyMapSerializer(), EmptyMap())
        mapEncoder.encodeStringElement(descriptor, 4, "authData")
        mapEncoder.encodeSerializableElement(descriptor, 5, ByteArraySerializer(), byteArrayOf())
        mapEncoder.endStructure(descriptor)
    }
}

@Serializable(with = EmptyMapSerializer::class)
class EmptyMap

class EmptyMapSerializer : KSerializer<EmptyMap> {
    override val descriptor: SerialDescriptor
        get() = MapSerializer(String.serializer(), String.serializer()).descriptor

    override fun deserialize(decoder: Decoder): EmptyMap {
        TODO("Not yet implemented")
    }

    override fun serialize(
        encoder: Encoder,
        value: EmptyMap,
    ) {
        val mapEncoder = encoder.beginCollection(descriptor, 0)
        mapEncoder.endStructure(descriptor)
    }
}
