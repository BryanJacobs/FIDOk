package us.q3q.fidok

import us.q3q.fidok.ctap.AuthenticatorListing

actual fun platformDeviceProviders(): List<AuthenticatorListing> {
    // return listOf(PCSCDevice, LibHIDDevice)
    return listOf(LibHIDDevice)
}
