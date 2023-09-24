package us.q3q.fidok.ctap.data

enum class FLAGS(val value: UByte) {
    UP(0x01u),
    UV(0x04u),
    BE(0x08u),
    BS(0x10u),
    AT(0x40u),
    ED(0x80u),
}
