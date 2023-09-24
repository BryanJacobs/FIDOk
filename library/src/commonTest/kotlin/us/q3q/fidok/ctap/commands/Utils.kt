package us.q3q.fidok.ctap.commands

import kotlinx.serialization.KSerializer

class Utils {
    companion object {
        fun <T> roundTripSerialize(entity: T, serializer: KSerializer<T>): T {
            val encoder = CTAPCBOREncoder()
            encoder.encodeSerializableValue(serializer, entity)
            val bytes = encoder.getBytes()

            return CTAPCBORDecoder(bytes).decodeSerializableValue(serializer)
        }
    }
}
