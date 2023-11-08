package us.q3q.fidok.ctap

/**
 * The ways that the FIDO standards describe a connection between an Authenticator and a Platform.
 *
 * @property value The CTAP canonical description of a connection mechanism
 */
enum class AuthenticatorTransport(val value: String) {
    /**
     * Universal Serial Bus. Probably uses the Human Interface Device CTAP encoding.
     */
    USB("usb"),

    /**
     * Near Field Communications; a wireless standard. Probably uses APDUs to communicate.
     */
    NFC("nfc"),

    /**
     * Bluetooth Low Energy. Uses its own protocol - GATT.
     */
    BLE("ble"),

    /**
     * A trusted element in some kind of housing. Annoyingly, smart cards can be contactless (NFC) or contacted.
     *
     * Probably uses APDUs to communicate either way, possibly over PC/SC or CCID, or maybe the smart card is connected
     * over USB? This is quite a confusing transport generally.
     */
    SMART_CARD("smart-card"),

    /**
     * Rather than being two transports smooshed together, this actually means a transport being "helped out" by some
     * server somewhere on the Internet. The Hybrid transport mainly describes CaBLE (Cloud Assisted BLE).
     */
    HYBRID("hybrid"),

    /**
     * The Authenticator is somehow part of the Platform itself.
     */
    INTERNAL("internal"),
}

/**
 * Represents an Authenticator with which the Platform can communicate
 */
interface AuthenticatorDevice {

    /**
     * Send bytes to the Authenticator, and receive bytes back
     *
     * @param bytes Request to the Authenticator. Should be CTAP CBOR
     * @return Authenticator response bytes received corresponding to this request. Should be CTAP CBOR
     */
    @Throws(DeviceCommunicationException::class)
    fun sendBytes(bytes: ByteArray): ByteArray

    /**
     * Get the transport(s) over which the Authenticator connects to the Platform
     *
     * @return A list of [AuthenticatorTransport] values
     */
    fun getTransports(): List<AuthenticatorTransport>
}
