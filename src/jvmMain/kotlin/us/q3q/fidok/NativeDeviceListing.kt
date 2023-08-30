package us.q3q.fidok

class NativeDeviceListing(libraryPath: String) : NativeLibraryUser(libraryPath) {
    fun list(): Int {
        return native.fidok_count_devices()
    }
}
