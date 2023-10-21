package us.q3q.fidok

import us.q3q.fidok.ctap.AuthenticatorDevice
import us.q3q.fidok.ctap.AuthenticatorListing

class NativeDeviceListing(private val libraryPath: String) : NativeLibraryUser(libraryPath), AuthenticatorListing {
    override fun listDevices(): List<AuthenticatorDevice> {
        val numDevices = native.fidok_count_devices()
        println("$numDevices devices")
        return (0..<numDevices).map {
            NativeBackedDevice(libraryPath, it)
        }
    }
}
