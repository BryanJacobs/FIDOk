package us.q3q.fidok.ctap.data

enum class FLAGS(val value: UByte) {
    USER_PRESENCE(0x01u),
    USER_VERIFICATION(0x04u),
    BACKUP_ELIGIBLE(0x08u),
    BACKUP_STATE(0x10u),
    ATTESTED(0x40u),
    EXTENSION_DATA(0x80u),
}
