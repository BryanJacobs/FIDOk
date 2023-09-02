package us.q3q.fidok.ctap

interface Device {
    fun sendBytes(bytes: ByteArray): ByteArray
}
