package us.q3q.fidok.ctap.commands

import co.touchlab.kermit.Logger
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule

val customSerializers = SerializersModule {
    include(extensionSerializers)
}

@OptIn(ExperimentalSerializationApi::class)
open class CTAPCBOREncoder : AbstractEncoder() {
    protected val accumulatedBytes = arrayListOf<Byte>()

    fun getBytes() = accumulatedBytes.toByteArray()

    private val cbor = Cbor { serializersModule = customSerializers }

    override val serializersModule: SerializersModule
        get() = customSerializers

    override fun encodeInline(descriptor: SerialDescriptor): Encoder {
        return this
    }

    override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
        return true
    }

    override fun <T> encodeSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        serializer: SerializationStrategy<T>,
        value: T,
    ) {
        Logger.v { "Encoding SerializableElement $index ${descriptor.getElementDescriptor(index)} = $value" }
        val element = descriptor.getElementDescriptor(index)
        if (element.kind == StructureKind.LIST && element.getElementDescriptor(0).kind == PrimitiveKind.BYTE) {
            val ba = ByteArrayEncoder(this)
            ba.encodeValue(value as Any)
            ba.endStructure(descriptor.getElementDescriptor(index))
            return
        }
        super.encodeSerializableElement(descriptor, index, serializer, value)
    }

    override fun beginCollection(descriptor: SerialDescriptor, collectionSize: Int): CompositeEncoder {
        if (descriptor.kind == StructureKind.LIST &&
            descriptor.getElementDescriptor(0).kind == PrimitiveKind.BYTE
        ) {
            // Lists of bytes are byte strings
            return ByteArrayEncoder(this)
        }
        if (descriptor.kind == StructureKind.MAP) {
            return MapEncoder(this, collectionSize)
        }
        if (descriptor.kind == StructureKind.LIST) {
            return ListEncoder(this, collectionSize)
        }
        return super.beginCollection(descriptor, collectionSize)
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        return when (descriptor.kind) {
            PolymorphicKind.SEALED ->
                SealedEncoder(this)
            StructureKind.CLASS ->
                ClassEncoder(this)
            else ->
                this
        }
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        // Do nothing
    }

    override fun encodeInt(value: Int) {
        val gotten = Cbor { serializersModule = customSerializers }.encodeToByteArray(Int.serializer(), value)
        accumulatedBytes.addAll(gotten.toList())
    }

    override fun encodeByte(value: Byte) {
        accumulatedBytes.add(value)
    }

    override fun encodeBoolean(value: Boolean) {
        accumulatedBytes.addAll(cbor.encodeToByteArray(value).toList())
    }

    override fun encodeString(value: String) {
        accumulatedBytes.addAll(cbor.encodeToByteArray(value).toList())
    }

    override fun encodeValue(value: Any) {
        accumulatedBytes.addAll(cbor.encodeToByteArray(value).toList())
    }

    override fun encodeNull() {
        // Do nothing
    }
}

@OptIn(ExperimentalStdlibApi::class)
open class ParentFeedingEncoder(val parentEncoder: CTAPCBOREncoder) : CTAPCBOREncoder() {

    fun feedParent() {
        for (x in accumulatedBytes) {
            parentEncoder.encodeByte(x)
        }
        accumulatedBytes.clear()
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        feedParent()
    }
}

@OptIn(ExperimentalSerializationApi::class)
class SealedEncoder(parentEncoder: CTAPCBOREncoder) : ParentFeedingEncoder(parentEncoder) {
    override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
        // class name is at index 0 - skip
        return index != 0
    }

    override fun <T> encodeSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        serializer: SerializationStrategy<T>,
        value: T,
    ) {
        super.encodeSerializableElement(descriptor.getElementDescriptor(index), index, serializer, value)
    }
}

@OptIn(ExperimentalSerializationApi::class)
class ClassEncoder(parentEncoder: CTAPCBOREncoder) : ParentFeedingEncoder(parentEncoder) {

    private var descriptorIndex = 0
    private var classDescriptor: SerialDescriptor? = null

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        if (descriptor.kind == StructureKind.LIST) {
            // Sub-lists should be handled as children of this class
            val pe = ParentFeedingEncoder(this)
            pe.beginStructure(descriptor)
            return pe
        }
        classDescriptor = descriptor
        return this
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        super.endStructure(descriptor)
    }

    override fun encodeString(value: String) {
        super.encodeString(value)
    }
}

class ByteArrayEncoder(parentEncoder: CTAPCBOREncoder) : ParentFeedingEncoder(parentEncoder) {
    override fun encodeValue(value: Any) {
        // It's a byte array.
        val ba = value as ByteArray

        accumulatedBytes.addAll(ba.toList())
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        val count = accumulatedBytes.size

        val prefixBytes = arrayListOf<Byte>()

        if (count <= 23) {
            prefixBytes.add((count + 0x40).toByte())
        } else if (count <= 255) {
            prefixBytes.add((0x58).toByte())
            prefixBytes.add(count.toByte())
        } else if (count <= Short.MAX_VALUE) {
            prefixBytes.add((0x59).toByte())
            prefixBytes.add(((count and 0xFF00) shr 8).toByte())
            prefixBytes.add((count and 0x00FF).toByte())
        } else {
            throw SerializationException("Overlong byte array")
        }

        prefixBytes.addAll(accumulatedBytes)
        accumulatedBytes.clear()
        accumulatedBytes.addAll(prefixBytes)

        super.endStructure(descriptor)
    }
}

class ListEncoder(parentEncoder: CTAPCBOREncoder, private val numElements: Int) : ParentFeedingEncoder(parentEncoder) {
    override fun endStructure(descriptor: SerialDescriptor) {
        if (numElements <= 23) {
            parentEncoder.encodeByte((numElements + 0x80).toByte())
        } else if (numElements <= 255) {
            parentEncoder.encodeByte((0x98).toByte())
            parentEncoder.encodeByte(numElements.toByte())
        } else if (numElements <= Short.MAX_VALUE) {
            parentEncoder.encodeByte((0x99).toByte())
            parentEncoder.encodeByte(((numElements and 0xFF00) shr 8).toByte())
            parentEncoder.encodeByte((numElements and 0x00FF).toByte())
        } else {
            throw SerializationException("Overlong list element")
        }
        super.endStructure(descriptor)
    }
}

class MapEncoder(parentEncoder: CTAPCBOREncoder, private val numElements: Int) : ParentFeedingEncoder(parentEncoder) {

    private val bufferedValues: ArrayList<List<Byte>> = arrayListOf()

    private fun stashResult(f: () -> Any) {
        val heldBytes = accumulatedBytes.toList()
        accumulatedBytes.clear()
        f()
        // _bytes now contains the result of the function call
        if (!accumulatedBytes.isEmpty()) {
            bufferedValues.add(accumulatedBytes.toList())
        }
        accumulatedBytes.clear()
        accumulatedBytes.addAll(heldBytes)
    }

    override fun encodeString(value: String) {
        stashResult {
            super.encodeString(value)
        }
    }

    override fun encodeInt(value: Int) {
        stashResult {
            super.encodeInt(value)
        }
    }

    override fun encodeValue(value: Any) {
        stashResult {
            super.encodeValue(value)
        }
    }

    override fun <T> encodeSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        serializer: SerializationStrategy<T>,
        value: T,
    ) {
        // Hold this for later - we'll need to sort the values
        stashResult {
            super.encodeSerializableElement(descriptor, index, serializer, value)
        }
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        // Emit map header
        if (numElements <= 23) {
            parentEncoder.encodeByte((numElements + 0xA0).toByte())
        } else if (numElements <= 255) {
            parentEncoder.encodeByte((0xA8).toByte())
            parentEncoder.encodeByte(numElements.toByte())
        } else if (numElements <= Short.MAX_VALUE) {
            parentEncoder.encodeByte((0xA9).toByte())
            parentEncoder.encodeByte(((numElements and 0xFF00) shr 8).toByte())
            parentEncoder.encodeByte((numElements and 0x00FF).toByte())
        } else {
            throw SerializationException("Overlong map")
        }

        // Sort any values we have in the buffer - it contains key, value, key, value...
        if (bufferedValues.size % 2 != 0) {
            throw SerializationException("Map contains an odd number of entries: ${bufferedValues.size}")
        }
        val emissionOrder = (0..<bufferedValues.size step 2).sortedWith {
                a, b ->
            if (bufferedValues[a].size == bufferedValues[b].size) {
                // equal key lengths: compare key values
                for (i in 0..bufferedValues[a].size) {
                    if (bufferedValues[a][i] < bufferedValues[b][i]) {
                        return@sortedWith -1
                    } else if (bufferedValues[a][i] > bufferedValues[b][i]) {
                        return@sortedWith 1
                    }
                }
                0
            } else {
                bufferedValues[a].size - bufferedValues[b].size
            }
        }

        emissionOrder.forEach { i ->
            // emit key, then value
            for (b in bufferedValues[i]) {
                parentEncoder.encodeByte(b)
            }
            for (b in bufferedValues[i + 1]) {
                parentEncoder.encodeByte(b)
            }
        }

        super.endStructure(descriptor)
    }
}
