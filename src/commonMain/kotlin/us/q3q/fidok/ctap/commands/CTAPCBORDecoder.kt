package us.q3q.fidok.ctap.commands

import co.touchlab.kermit.Logger
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.modules.SerializersModule

@OptIn(ExperimentalSerializationApi::class, ExperimentalStdlibApi::class)
open class CTAPCBORDecoder(protected var input: ByteArray) : AbstractDecoder() {

    protected var offset = 0
    private val cbor = Cbor { customSerializers }

    override val serializersModule: SerializersModule
        get() = SerializersModule {
            customSerializers
        }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        if (descriptor.kind == StructureKind.LIST &&
            descriptor.getElementDescriptor(0).kind == PrimitiveKind.BYTE
        ) {
            val p = peekCollectionSize(descriptor)
            val ret = CTAPCBORArrayDecoder(input.copyOfRange(offset, p.first + p.second))
            offset = p.first + p.second
            return ret
        }
        return super.beginStructure(descriptor)
    }

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (offset == input.size) {
            return -1
        }
        val ret = input[offset++].toInt()
        Logger.v("DEI ($ret) $descriptor")
        return ret
    }

    override fun <T> decodeSerializableValue(deserializer: DeserializationStrategy<T>): T {
        Logger.v("DSV $deserializer")
        return deserializer.deserialize(this)
    }

    override fun decodeNotNullMark(): Boolean {
        return offset < input.size
    }

    override fun decodeBoolean(): Boolean {
        return cbor.decodeFromByteArray(Boolean.serializer(), byteArrayOf(input[offset++]))
    }

    override fun decodeInt(): Int {
        val ret = cbor.decodeFromByteArray(
            Int.serializer(),
            input.toList().subList(offset, input.size).toByteArray(),
        )
        offset++
        if (ret > 23 || ret < -24) {
            offset++
        }
        if (ret > 255 || ret < -256) {
            offset++
        }
        return ret
    }

    override fun decodeSequentially(): Boolean {
        return true
    }

    override fun decodeByte(): Byte {
        return input[offset++]
    }

    override fun decodeString(): String {
        val ret = cbor.decodeFromByteArray(
            String.serializer(),
            input.toList().subList(offset, input.size).toByteArray(),
        )

        offset += ret.length + 1
        if (ret.length > 23) {
            offset++
        }
        if (ret.length > 255) {
            offset++
        }

        return ret
    }

    private fun peekCollectionSize(descriptor: SerialDescriptor): Pair<Int, Int> {
        var soffset = offset
        val ret = when (descriptor.kind) {
            StructureKind.MAP, StructureKind.CLASS -> {
                val byte = input[soffset++]
                if (byte == (0xB8).toByte()) {
                    val num = input[soffset++]
                    num.toInt()
                } else if (byte >= (0xA0).toByte() && byte <= (0xB7).toByte()) {
                    byte - (0xA0).toByte()
                } else {
                    throw SerializationException("Overlong or incorrect map: $byte")
                }
            }
            StructureKind.LIST -> {
                when (val byte = input[soffset++].toUByte().toInt()) {
                    0x98, 0x58 -> {
                        input[soffset++].toUByte().toInt()
                    }
                    0x99, 0x59 -> {
                        (input[soffset++].toUByte().toInt() shl 8) + input[soffset++].toUByte().toInt()
                    }
                    in 0x80..0x97 -> {
                        byte - 0x80
                    }
                    in 0x40..0x57 -> {
                        // for bogus reasons, a byte array is a "byte list" here
                        byte - 0x40
                    }
                    else -> {
                        throw SerializationException("Overlong or incorrect list: ${byte.toHexString()}")
                    }
                }
            }
            else ->
                throw NotImplementedError()
        }
        return ret to soffset
    }

    override fun decodeCollectionSize(descriptor: SerialDescriptor): Int {
        val p = peekCollectionSize(descriptor)
        offset = p.second
        return p.first
    }
}

class CTAPCBORArrayDecoder(bytes: ByteArray) : CTAPCBORDecoder(bytes) {
    override fun endStructure(descriptor: SerialDescriptor) {
        if (offset != input.size) {
            throw SerializationException("Byte array sub-decoder didn't consume entire array!")
        }
    }
}
