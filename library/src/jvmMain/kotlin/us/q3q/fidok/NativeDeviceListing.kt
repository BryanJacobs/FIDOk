package us.q3q.fidok

import jnr.ffi.Pointer
import us.q3q.fidok.ctap.AuthenticatorDevice
import us.q3q.fidok.ctap.AuthenticatorListing

class NativeDeviceListing(private val libraryPath: String) : NativeLibraryUser(libraryPath), AuthenticatorListing {
    override fun listDevices(): List<AuthenticatorDevice> {
        val listing = native.fidok_device_list()
        val numDevices = native.fidok_device_count(listing)

        val listingHolder = NativeListingHolder(native, listing, numDevices)

        return (0..<numDevices).map {
            NativeBackedDevice(libraryPath, listingHolder, it)
        }
    }
}

data class NativeListingHolder(private val native: FIDOkNative, val listing: Pointer, private val numDevices: Int) {
    private var devicesRemaining = numDevices

    fun release() {
        if (--devicesRemaining == 0) {
            native.fidok_free_device_list(listing)
        }
    }
}
