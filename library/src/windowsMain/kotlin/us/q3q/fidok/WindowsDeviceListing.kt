package us.q3q.fidok

import us.q3q.fidok.ctap.Device

actual fun platformListDevices(): List<Device> {
    return PCSCDevice.list() + LibHIDDevice.list()
}
