package us.q3q.fidok.ctap

enum class AuthenticatorTransport(val value: String) {
    USB("usb"),
    NFC("nfc"),
    BLE("ble"),
    SMART_CARD("smart-card"),
    HYBRID("hybrid"),
    INTERNAL("internal"),
}

interface AuthenticatorDevice {
    @Throws(DeviceCommunicationException::class)
    fun sendBytes(bytes: ByteArray): ByteArray

    fun getTransports(): List<AuthenticatorTransport>
}
