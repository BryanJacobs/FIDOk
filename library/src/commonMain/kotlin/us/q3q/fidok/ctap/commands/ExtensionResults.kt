package us.q3q.fidok.ctap.commands

import co.touchlab.kermit.Logger
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

data class ExtensionResults(val v: Map<ExtensionName, ExtensionParameters>)

@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
abstract class ExtensionResultsSerializer : KSerializer<ExtensionResults> {
    abstract fun getApplicableSerializer(extensionName: ExtensionName): DeserializationStrategy<ExtensionParameters>

    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("ExtensionResults") {
            element("key", String.serializer().descriptor)
            element("value", ExtensionParameters.serializer().descriptor)
        }

    override fun deserialize(decoder: Decoder): ExtensionResults {
        val composite = decoder.beginStructure(descriptor)
        val numItems = composite.decodeCollectionSize(descriptor)

        Logger.v { "There were $numItems extension result items" }

        val ret = hashMapOf<ExtensionName, ExtensionParameters>()
        for (i in 0..<numItems) {
            val extensionName = composite.decodeSerializableElement(descriptor, 0, ExtensionName.serializer())
            val deserializer = getApplicableSerializer(extensionName)
            Logger.v { "Result for $extensionName using $deserializer" }
            val extensionResult = composite.decodeSerializableElement(descriptor, 1, deserializer)
            Logger.v { "$extensionName value was $extensionResult" }
            ret[extensionName] = extensionResult
        }

        composite.endStructure(descriptor)
        return ExtensionResults(ret)
    }

    override fun serialize(encoder: Encoder, value: ExtensionResults) {
        throw NotImplementedError("Cannot serialize extension RESULTS")
    }
}

class CreationExtensionResultsSerializer : ExtensionResultsSerializer() {
    override fun getApplicableSerializer(extensionName: ExtensionName): DeserializationStrategy<ExtensionParameters> {
        return ExtensionSetup.getCreationRegistration(extensionName)
            ?: throw SerializationException("Unknown/unrequested creation extension result for $extensionName")
    }
}

class AssertionExtensionResultsSerializer : ExtensionResultsSerializer() {
    override fun getApplicableSerializer(extensionName: ExtensionName): DeserializationStrategy<ExtensionParameters> {
        return ExtensionSetup.getAssertionRegistration(extensionName)
            ?: throw SerializationException("Unknown/unrequested assertion extension result for $extensionName")
    }
}
