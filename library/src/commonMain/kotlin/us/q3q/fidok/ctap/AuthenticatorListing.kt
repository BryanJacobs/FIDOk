package us.q3q.fidok.ctap

interface AuthenticatorListing {
    fun listDevices(): List<AuthenticatorDevice>
}
