package us.q3q.fidok

import us.q3q.fidok.ctap.DeviceListing

actual fun platformDeviceProviders(): List<DeviceListing> {
    return listOf(PCSCDevice, LibHIDDevice)
}
