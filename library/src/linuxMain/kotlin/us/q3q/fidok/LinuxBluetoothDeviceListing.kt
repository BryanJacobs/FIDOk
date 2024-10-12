package us.q3q.fidok

import binc.Adapter
import binc.BINC_BONDED
import binc.BINC_DISCONNECTED
import binc.BINC_DISCOVERY_STARTED
import binc.BINC_DISCOVERY_STARTING
import binc.BINC_DISCOVERY_STOPPED
import binc.Characteristic
import binc.ConnectionState
import binc.Device
import binc.DiscoveryState
import binc.FALSE
import binc.GByteArray
import binc.GDBusConnection
import binc.GError
import binc.GMainLoop
import binc.GPtrArray
import binc.G_BUS_TYPE_SYSTEM
import binc.TRUE
import binc.WITHOUT_RESPONSE
import binc.WITH_RESPONSE
import binc.binc_adapter_free
import binc.binc_adapter_get_default
import binc.binc_adapter_get_device_by_address
import binc.binc_adapter_get_discovery_state
import binc.binc_adapter_get_discovery_state_name
import binc.binc_adapter_remove_device
import binc.binc_adapter_set_discovery_cb
import binc.binc_adapter_set_discovery_filter
import binc.binc_adapter_set_discovery_state_cb
import binc.binc_adapter_start_discovery
import binc.binc_adapter_stop_discovery
import binc.binc_characteristic_get_service
import binc.binc_characteristic_get_uuid
import binc.binc_characteristic_is_notifying
import binc.binc_device_connect
import binc.binc_device_get_address
import binc.binc_device_get_bonding_state
import binc.binc_device_get_name
import binc.binc_device_get_service
import binc.binc_device_read_char
import binc.binc_device_set_connection_state_change_cb
import binc.binc_device_set_notify_char_cb
import binc.binc_device_set_notify_state_cb
import binc.binc_device_set_read_char_cb
import binc.binc_device_set_services_resolved_cb
import binc.binc_device_set_write_char_cb
import binc.binc_device_start_notify
import binc.binc_device_stop_notify
import binc.binc_device_write_char
import binc.binc_service_get_characteristics
import binc.binc_service_get_uuid
import binc.g_bus_get_sync
import binc.g_byte_array_append
import binc.g_byte_array_free
import binc.g_byte_array_sized_new
import binc.g_free
import binc.g_list_first
import binc.g_main_loop_new
import binc.g_main_loop_quit
import binc.g_main_loop_run
import binc.g_main_loop_unref
import binc.g_object_unref
import binc.g_ptr_array_add
import binc.g_ptr_array_free
import binc.g_ptr_array_new
import binc.g_uuid_string_random
import binc.gcharVar
import co.touchlab.kermit.Logger
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.pointed
import kotlinx.cinterop.set
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toCValues
import kotlinx.cinterop.toKString
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import us.q3q.fidok.ble.FIDO_BLE_SERVICE_UUID
import us.q3q.fidok.ble.reader.ACR_BLE_READER_SERVICE_UUID
import us.q3q.fidok.ctap.AuthenticatorDevice
import us.q3q.fidok.ctap.AuthenticatorListing
import us.q3q.fidok.ctap.AuthenticatorTransport
import us.q3q.fidok.ctap.FIDOkLibrary
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val listingState = LinuxBluetoothDeviceListing()

private val DISCOVERY_TIMEOUT = 5.seconds
private val DISCOVERY_POLL_INTERVAL = 500.milliseconds
private val CONNECT_TIMEOUT = 10.seconds
private val READ_TIMEOUT = 5.seconds
private val WRITE_TIMEOUT = 5.seconds

@OptIn(ExperimentalForeignApi::class)
private fun getMAC(device: CPointer<Device>): String {
    val address =
        binc_device_get_address(device)
            ?: throw RuntimeException("Unable to get MAC for device")
    return address.toKString()
}

@OptIn(ExperimentalForeignApi::class)
private fun onConnectionStateChange(
    device: CPointer<Device>?,
    state: ConnectionState,
    error: CPointer<GError>?,
) {
    if (error != null && device == null) {
        throw RuntimeException(error.pointed.message?.toKString())
    }

    if (device == null) {
        throw RuntimeException("Null device in connection, no error")
    }

    val addr = getMAC(device)

    if (error != null) {
        listingState.handleConnectionError(addr, device, error.pointed.message?.toKString())
    } else {
        listingState.handleConnectionStateChange(addr, device, state)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun onServicesResolved(device: CPointer<Device>?) {
    if (device == null) {
        throw RuntimeException("Null device in services resolved")
    }
    val addr = getMAC(device)
    Logger.v { "Services resolved on $addr" }

    listingState.handleServicesResolved(addr, device)
}

@OptIn(ExperimentalForeignApi::class)
private fun getCharAndServiceUUIDs(
    device: CPointer<Device>,
    characteristic: CPointer<Characteristic>?,
): Triple<String, String, String> {
    val addr = getMAC(device)
    val char =
        binc_characteristic_get_uuid(characteristic)
            ?: throw RuntimeException("Unable to get characteristic UUID for $device write")
    val charStr = char.toKString()
    val service =
        binc_characteristic_get_service(characteristic)
            ?: throw RuntimeException("Unable to get service from characteristic $charStr")
    val serviceStr =
        binc_service_get_uuid(service)?.toKString()
            ?: throw RuntimeException("Could not get UUID of service for $charStr")
    return Triple(addr, serviceStr, charStr)
}

@OptIn(ExperimentalForeignApi::class, ExperimentalStdlibApi::class)
private fun onRead(
    device: CPointer<Device>?,
    characteristic: CPointer<Characteristic>?,
    data: CPointer<GByteArray>?,
    error: CPointer<GError>?,
) {
    if (error != null && (device == null || characteristic == null)) {
        throw RuntimeException(error.pointed.message?.toKString())
    }

    if (device == null) {
        throw RuntimeException("Null device in read, no error")
    }

    if (characteristic == null) {
        throw RuntimeException("Null characteristic in read, no error")
    }

    val (addr, serviceStr, charStr) = getCharAndServiceUUIDs(device, characteristic)

    if (error != null) {
        val message = error.pointed.message?.toKString()
        Logger.v { "Got read error from BLE $addr($serviceStr - $charStr): $message" }
        listingState.handleReadError(addr, serviceStr, charStr, message ?: "No message")
    } else {
        val result = data?.let { toKBytes(it) }
        Logger.v { "Got read result from BLE $addr($serviceStr - $charStr): ${result?.toHexString()}" }
        listingState.handleReadComplete(addr, serviceStr, charStr, result)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun toKBytes(data: CPointer<GByteArray>): UByteArray {
    val len = data.pointed.len.convert<Int>()
    val ret = UByteArray(len)
    for (i in ret.indices) {
        ret[i] = data.pointed.data!![i]
    }
    return ret
}

@OptIn(ExperimentalForeignApi::class, ExperimentalStdlibApi::class)
private fun onWrite(
    device: CPointer<Device>?,
    characteristic: CPointer<Characteristic>?,
    data: CPointer<GByteArray>?,
    error: CPointer<GError>?,
) {
    if (error != null && (device == null || characteristic == null)) {
        throw RuntimeException(error.pointed.message?.toKString())
    }

    if (device == null) {
        throw RuntimeException("Null device in write, no error")
    }

    if (characteristic == null) {
        throw RuntimeException("Null characteristic in write, no error")
    }

    val (addr, serviceStr, charStr) = getCharAndServiceUUIDs(device, characteristic)

    if (error != null) {
        val message = error.pointed.message?.toKString()
        Logger.v { "Got write error from BLE $addr($serviceStr - $charStr): $message" }
        listingState.handleWriteError(addr, serviceStr, charStr, message ?: "No message")
    } else {
        val result = data?.let { toKBytes(it) }
        Logger.v { "Got write result from BLE $addr($serviceStr - $charStr): ${result?.toHexString()}" }
        listingState.handleWriteComplete(addr, serviceStr, charStr, result)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun onNotify(
    device: CPointer<Device>?,
    characteristic: CPointer<Characteristic>?,
    data: CPointer<GByteArray>?,
) {
    if (device == null) {
        throw RuntimeException("Null device in notify")
    }

    if (characteristic == null) {
        throw RuntimeException("Null characteristic in notify")
    }

    if (data == null) {
        throw RuntimeException("Null data in notify")
    }

    val (addr, serviceStr, charStr) = getCharAndServiceUUIDs(device, characteristic)

    listingState.handleNotify(addr, serviceStr, charStr, toKBytes(data))
}

@OptIn(ExperimentalForeignApi::class)
private fun onNotifyState(
    device: CPointer<Device>?,
    characteristic: CPointer<Characteristic>?,
    error: CPointer<GError>?,
) {
    if (error != null && (device == null || characteristic == null)) {
        throw RuntimeException(error.pointed.message?.toKString())
    }

    if (device == null) {
        throw RuntimeException("Null device in notify_state")
    }

    val (addr, serviceStr, charStr) = getCharAndServiceUUIDs(device, characteristic)

    val notifying = binc_characteristic_is_notifying(characteristic) == TRUE

    Logger.v { "Got notify state change on $addr ($serviceStr $charStr), now $notifying" }

    listingState.handleNotifyState(addr, serviceStr, charStr, notifying)
}

@OptIn(ExperimentalForeignApi::class)
private fun onDiscoveryState(
    adapter: CPointer<Adapter>?,
    discoveryState: DiscoveryState,
    error: CPointer<GError>?,
) {
    if (error != null) {
        listingState.handleDiscoveryError(error.pointed.message?.toKString())
    }
    val state = binc_adapter_get_discovery_state_name(adapter)?.toKString()
    Logger.v { "Discovery state: $state" }
}

@OptIn(ExperimentalForeignApi::class)
private fun onDiscovery(
    adapter: CPointer<Adapter>?,
    device: CPointer<Device>?,
) {
    if (device == null) {
        Logger.v { "Discovery of null device!" }
        return
    }

    val name = binc_device_get_name(device)
    val nameStr = name?.toKString() ?: "Unknown device"

    val addressStr = getMAC(device)

    Logger.v { "Discovery made: $nameStr ($addressStr)" }
    binc_adapter_stop_discovery(adapter)

    listingState.registerDevice(addressStr, nameStr, device)
}

private fun onTimeout() {
}

@OptIn(ExperimentalForeignApi::class)
private data class BTDeviceInfo(val logicalDevice: AuthenticatorDevice, val address: String, val rawDevice: CPointer<Device>)

@OptIn(ExperimentalForeignApi::class)
class LinuxBluetoothDeviceListing {
    private var adapter: CPointer<Adapter>? = null
    private val connectionChannelsByAddress: MutableMap<String, Channel<Boolean>> = hashMapOf()
    private val writeChannelsByAddress: MutableMap<String, Channel<UByteArray?>> = hashMapOf()
    private val readChannelsByAddress: MutableMap<String, Channel<UByteArray?>> = hashMapOf()
    private val notifyChannelsByAddressServiceAndChar:
        MutableMap<
            String,
            MutableMap<
                String,
                MutableMap<String, Pair<Channel<UByteArray>, Channel<Boolean>>>,
                >,
            > = hashMapOf()
    private val registrationChannel = Channel<BTDeviceInfo>(Channel.BUFFERED)

    private var refCt = 0
    private var dbusConnection: CPointer<GDBusConnection>? = null
    private var tokenServiceUuid: CPointer<gcharVar>? = null
    private var readerServiceUuid: CPointer<gcharVar>? = null
    private var serviceUuids: CPointer<GPtrArray>? = null
    private var loop: CPointer<GMainLoop>? = null
    private var discoveryError: String? = null
    private var fidokLibrary: FIDOkLibrary? = null

    private fun isToken(device: CPointer<Device>): Boolean {
        if (binc_device_get_service(device, FIDO_BLE_SERVICE_UUID) != null) {
            return true
        }
        return false
    }

    fun registerDevice(
        address: String,
        name: String,
        device: CPointer<Device>,
    ) {
        val obj =
            if (isToken(device)) {
                LinuxBluetoothDevice(address, name, this)
            } else {
                LinuxBluetoothReader(address, name, this)
            }

        runBlocking {
            registrationChannel.send(
                BTDeviceInfo(
                    logicalDevice = obj,
                    address = address,
                    rawDevice = device,
                ),
            )
        }
    }

    fun ref(): Boolean {
        Logger.v { "Adding reference to BLE services" }

        if (refCt++ == 0) {
            val ret = start()
            if (!ret) {
                unref()
            }
            return ret
        }
        return true
    }

    private fun start(): Boolean {
        Logger.v { "Starting BLE services" }

        dbusConnection = g_bus_get_sync(G_BUS_TYPE_SYSTEM, null, null)
        if (dbusConnection == null) {
            return false
        }

        if (adapter == null) {
            adapter = binc_adapter_get_default(dbusConnection)
            if (adapter == null) {
                return false
            }
        }

        tokenServiceUuid = g_uuid_string_random()
        for (i in FIDO_BLE_SERVICE_UUID.indices) {
            tokenServiceUuid!![i] = FIDO_BLE_SERVICE_UUID[i].code.toByte()
        }

        readerServiceUuid = g_uuid_string_random()
        for (i in ACR_BLE_READER_SERVICE_UUID.indices) {
            readerServiceUuid!![i] = ACR_BLE_READER_SERVICE_UUID[i].code.toByte()
        }

        serviceUuids = g_ptr_array_new()
        if (serviceUuids == null) {
            return false
        }
        g_ptr_array_add(serviceUuids, tokenServiceUuid)
        g_ptr_array_add(serviceUuids, readerServiceUuid)

        return true
    }

    fun getLibrary(): FIDOkLibrary {
        return fidokLibrary
            ?: throw IllegalStateException("Tried to get FIDOk library from uninitialized BLE state")
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun startDiscovery(library: FIDOkLibrary): Job? {
        val usedAdapter = adapter
        if (usedAdapter == null || serviceUuids == null) {
            Logger.w { "Discovery failed to start due to missing BLE adapter" }
            return null
        }

        val currentState = binc_adapter_get_discovery_state(usedAdapter)
        if (currentState == BINC_DISCOVERY_STARTING || currentState == BINC_DISCOVERY_STARTED) {
            return null
        }

        fidokLibrary = library
        binc_adapter_set_discovery_filter(usedAdapter, -100, serviceUuids, null)
        binc_adapter_set_discovery_cb(usedAdapter, staticCFunction(::onDiscovery))
        binc_adapter_set_discovery_state_cb(usedAdapter, staticCFunction(::onDiscoveryState))

        var job: Job? = null

        if (loop == null) {
            loop = g_main_loop_new(null, FALSE)

            job =
                GlobalScope.launch {
                    Logger.v { "Starting glib main loop" }
                    g_main_loop_run(loop)
                }
        }

        binc_adapter_start_discovery(usedAdapter)

        return job
    }

    suspend fun connect(address: String): Boolean {
        Logger.i { "Opening connection to BLE device $address" }

        val device =
            binc_adapter_get_device_by_address(adapter, address)
                ?: throw IllegalStateException("Unknown device with address $address")

        if (connectionChannelsByAddress.contains(address)) {
            throw IllegalStateException("Already watching for connection to $address")
        }

        binc_device_set_connection_state_change_cb(device, staticCFunction(::onConnectionStateChange))
        binc_device_set_services_resolved_cb(device, staticCFunction(::onServicesResolved))
        binc_device_set_read_char_cb(device, staticCFunction(::onRead))
        binc_device_set_write_char_cb(device, staticCFunction(::onWrite))
        binc_device_set_notify_char_cb(device, staticCFunction(::onNotify))
        binc_device_set_notify_state_cb(device, staticCFunction(::onNotifyState))

        val channel = Channel<Boolean>(1)
        connectionChannelsByAddress[address] = channel

        binc_device_connect(device)

        var okay = false

        try {
            withTimeout(CONNECT_TIMEOUT) {
                okay = channel.receive()
            }
        } catch (e: TimeoutCancellationException) {
            Logger.w { "Timed out connecting to $address" }
        } finally {
            connectionChannelsByAddress.remove(address)
        }

        Logger.i { "BLE Connected to $address : $okay" }

        return okay
    }

    fun unref() {
        Logger.v { "Removing reference to BLE state" }
        if (--refCt == 0) {
            stop()
        }
    }

    private fun stop() {
        Logger.v { "Stopping BLE services" }

        val currentState = binc_adapter_get_discovery_state(adapter)
        if (currentState == BINC_DISCOVERY_STARTING || currentState == BINC_DISCOVERY_STARTED) {
            binc_adapter_stop_discovery(adapter)
        }

        if (loop != null) {
            g_main_loop_quit(loop)
            g_main_loop_unref(loop)
            loop = null
        }

        if (adapter != null) {
            binc_adapter_free(adapter)
            adapter = null
        }

        if (dbusConnection != null) {
            // g_dbus_connection_close_sync(dbusConnection, null, null)
            g_object_unref(dbusConnection)
            dbusConnection = null
        }

        if (serviceUuids != null) {
            g_ptr_array_free(serviceUuids, TRUE)
            serviceUuids = null
        }

        if (tokenServiceUuid != null) {
            g_free(tokenServiceUuid)
            tokenServiceUuid = null
        }

        if (readerServiceUuid != null) {
            g_free(readerServiceUuid)
            readerServiceUuid = null
        }
    }

    fun handleConnectionStateChange(
        addr: String,
        device: CPointer<Device>,
        state: ConnectionState,
    ) {
        Logger.v { "Connection state change (on $addr): $state" }
        if (state == BINC_DISCONNECTED && binc_device_get_bonding_state(device) != BINC_BONDED) {
            binc_adapter_remove_device(listingState.adapter, device)
        }
    }

    fun handleConnectionError(
        addr: String,
        device: CPointer<Device>,
        error: String?,
    ) {
        Logger.e { "Error BLE connecting (to $addr): $error" }

        val cbChannel = connectionChannelsByAddress.get(addr)
        if (cbChannel != null) {
            runBlocking {
                cbChannel.send(false)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun getDiscoveredDevices(): List<AuthenticatorDevice> {
        val ret = arrayListOf<AuthenticatorDevice>()
        while (!registrationChannel.isEmpty) {
            val device = registrationChannel.receive()
            Logger.i { "Got device $device from registration channel" }
            ret.add(device.logicalDevice)
        }
        return ret
    }

    private fun logCharacteristics(
        device: CPointer<Device>,
        serviceUuid: String,
    ) {
        val service = binc_device_get_service(device, serviceUuid)
        val characteristics =
            binc_service_get_characteristics(service)
                ?: throw IllegalStateException("Failed to get characteristics")

        var char = g_list_first(characteristics)
        while (char != null) {
            @Suppress("UNCHECKED_CAST")
            val charData = char.pointed.data as CPointer<Characteristic>
            val uuid = binc_characteristic_get_uuid(charData)
            Logger.v { "Got chara ${uuid?.toKString()}" }

            char = char.pointed.next
        }
    }

    fun handleServicesResolved(
        addr: String,
        device: CPointer<Device>,
    ) {
        val cbChannel = connectionChannelsByAddress.get(addr)
        if (cbChannel != null) {
            runBlocking {
                cbChannel.send(true)
            }
        }
    }

    private fun <R> withGBytes(
        data: UByteArray,
        block: (arr: CPointer<GByteArray>) -> R,
    ): R {
        val arr =
            g_byte_array_sized_new(data.size.convert())
                ?: throw RuntimeException("Failed to allocate GByteArray")
        try {
            // TODO: check this works
            g_byte_array_append(arr, data.toCValues(), data.size.convert())
            return block(arr)
        } finally {
            g_byte_array_free(arr, TRUE)
        }
    }

    fun setNotify(
        address: String,
        serviceUuid: String,
        characteristicUuid: String,
        responder: Channel<UByteArray>?,
    ): Boolean {
        val device =
            binc_adapter_get_device_by_address(adapter, address)
                ?: throw IllegalStateException("Unknown device with address $address")

        var forAddr = notifyChannelsByAddressServiceAndChar[address]
        if (forAddr == null) {
            forAddr = hashMapOf()
            notifyChannelsByAddressServiceAndChar[address] = forAddr
        }

        var forService = forAddr[serviceUuid]
        if (forService == null) {
            forService = hashMapOf()
            forAddr[serviceUuid] = forService
        }

        if (responder != null) {
            if (forService.contains(characteristicUuid)) {
                throw IllegalStateException("Notifier already registered for $address ($serviceUuid $characteristicUuid)")
            }

            val setupChannel = Channel<Boolean>()
            forService[characteristicUuid] = Pair(responder, setupChannel)

            Logger.v { "Enabling notifies on $address ($serviceUuid-$characteristicUuid)" }
            if (binc_device_start_notify(device, serviceUuid, characteristicUuid) != TRUE) {
                Logger.w { "Starting notifies failed on $address ($serviceUuid $characteristicUuid)" }
                return false
            }

            return runBlocking {
                setupChannel.receive()
            }
        } else {
            Logger.v { "Stopping notifies on $address ($serviceUuid $characteristicUuid)" }

            val pairing = forService[characteristicUuid]
            if (pairing == null) {
                Logger.w { "Notifies already stopped on $address ($serviceUuid $characteristicUuid)" }
                return true
            }

            val setupChannel = pairing.second
            if (binc_device_stop_notify(device, serviceUuid, characteristicUuid) != TRUE) {
                Logger.w { "Stopping notifies failed on $address ($serviceUuid $characteristicUuid)" }
                return false
            }

            return runBlocking {
                !setupChannel.receive()
            }
        }
    }

    suspend fun write(
        address: String,
        serviceUuid: String,
        characteristicUuid: String,
        data: UByteArray,
        expectResponse: Boolean = false,
    ): UByteArray? {
        val device =
            binc_adapter_get_device_by_address(adapter, address)
                ?: throw IllegalStateException("Unknown device with address $address")

        if (writeChannelsByAddress.contains(address)) {
            throw IllegalStateException("Already listening for a write on $address")
        }

        val channel = Channel<UByteArray?>(1)
        writeChannelsByAddress[address] = channel

        try {
            val ok =
                withGBytes(data) {
                    val writeType = if (expectResponse) WITH_RESPONSE else WITHOUT_RESPONSE
                    return@withGBytes binc_device_write_char(
                        device,
                        serviceUuid,
                        characteristicUuid,
                        it,
                        writeType,
                    ) == TRUE
                }

            if (!ok) {
                Logger.e { "Write to $address ($serviceUuid - $characteristicUuid) failed due to binc_write_char result" }
                return null
            }

            return withTimeout(WRITE_TIMEOUT) {
                return@withTimeout channel.receive()
            }
        } finally {
            writeChannelsByAddress.remove(address)
        }
    }

    suspend fun read(
        address: String,
        serviceUuid: String,
        characteristicUuid: String,
    ): UByteArray? {
        val device =
            binc_adapter_get_device_by_address(adapter, address)
                ?: throw IllegalStateException("Unknown device with address $address")

        if (readChannelsByAddress.contains(address)) {
            throw IllegalStateException("Already waiting for a read on $address")
        }

        val channel = Channel<UByteArray?>(1)
        readChannelsByAddress[address] = channel

        try {
            val ok =
                binc_device_read_char(
                    device,
                    serviceUuid,
                    characteristicUuid,
                )

            if (ok != TRUE) {
                Logger.w { "Failed to read from $address ($serviceUuid - $characteristicUuid)" }
                return null
            }

            return withTimeout(READ_TIMEOUT) {
                return@withTimeout channel.receive()
            }
        } finally {
            readChannelsByAddress.remove(address)
        }
    }

    fun handleReadError(
        addr: String,
        service: String,
        characteristic: String,
        error: String,
    ) {
        val channel = readChannelsByAddress.get(addr)
        if (channel != null) {
            runBlocking {
                channel.send(null)
            }
        }
    }

    fun handleReadComplete(
        addr: String,
        service: String,
        characteristic: String,
        data: UByteArray?,
    ) {
        val channel = readChannelsByAddress.get(addr)
        if (channel != null) {
            runBlocking {
                channel.send(data)
            }
        }
    }

    fun handleWriteError(
        addr: String,
        service: String,
        characteristic: String,
        error: String,
    ) {
        val channel = writeChannelsByAddress.get(addr)
        if (channel != null) {
            runBlocking {
                channel.send(null)
            }
        }
    }

    fun handleWriteComplete(
        addr: String,
        service: String,
        characteristic: String,
        data: UByteArray?,
    ) {
        val channel = writeChannelsByAddress.get(addr)
        if (channel != null) {
            runBlocking {
                channel.send(data)
            }
        } else {
            Logger.d { "Ignored write to $addr ($service $characteristic)" }
        }
    }

    fun handleNotify(
        addr: String,
        service: String,
        characteristic: String,
        data: UByteArray,
    ) {
        Logger.v { "Notify received on $addr ($service $characteristic)" }
        val listener = notifyChannelsByAddressServiceAndChar.get(addr)?.get(service)?.get(characteristic)
        if (listener == null) {
            Logger.w { "Ignoring notify on $addr ($service $characteristic) with no listener" }
            return
        }

        runBlocking {
            listener.first.send(data)
        }
    }

    fun handleDiscoveryError(error: String?) {
        Logger.e { "Error discovering BT devices: $error" }
        discoveryError = error
    }

    companion object : AuthenticatorListing {
        private var discoveryJob: Job? = null
        private var ran = false

        override fun providedTransports(): List<AuthenticatorTransport> {
            return listOf(AuthenticatorTransport.BLE)
        }

        fun allowListingAgain() {
            discoveryJob?.cancel()
            discoveryJob = null
        }

        override fun listDevices(library: FIDOkLibrary): List<AuthenticatorDevice> {
            if (discoveryJob != null) {
                Logger.i { "Returning no devices in BLE listing due to previous run" }
                return listOf()
            }

            val okay = listingState.ref()
            if (!okay) {
                Logger.w { "Failed to start up BLE listening" }
                return listOf()
            }

            var reference = true

            try {
                return runBlocking {
                    val gottenJob = listingState.startDiscovery(library)
                    if (gottenJob != null) {
                        discoveryJob = gottenJob
                    }
                    val d = launch { delay(DISCOVERY_TIMEOUT) }

                    while (d.isActive) {
                        val discoOngoing = listingState.isDiscovering()
                        if (!discoOngoing) {
                            d.cancel()
                            break
                        }
                        delay(DISCOVERY_POLL_INTERVAL)
                    }

                    d.join()
                    listingState.unref()
                    reference = false
                    ran = true

                    listingState.getDiscoveredDevices()
                }
            } finally {
                if (reference) {
                    listingState.unref()
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun isDiscovering(): Boolean {
        if (!registrationChannel.isEmpty) {
            return false
        }

        if (discoveryError != null) {
            return false
        }

        val usedAdapter = adapter ?: return false

        val discoState = binc_adapter_get_discovery_state(usedAdapter)
        return discoState == BINC_DISCOVERY_STARTING || discoState == BINC_DISCOVERY_STARTED ||
            discoState == BINC_DISCOVERY_STOPPED
    }

    fun handleNotifyState(
        address: String,
        service: String,
        characteristic: String,
        notifying: Boolean,
    ) {
        val forService = notifyChannelsByAddressServiceAndChar.get(address)?.get(service)
        val cb = forService?.get(characteristic)
        if (cb != null) {
            runBlocking {
                if (notifying) {
                    cb.second.send(true)
                } else {
                    cb.second.send(false)
                    forService.remove(characteristic)
                }
            }
        } else {
            Logger.d { "Ignoring notification state change because no listener on $address ($service $characteristic)" }
        }
    }
}
