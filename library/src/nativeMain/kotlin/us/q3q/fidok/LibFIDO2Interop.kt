@file:OptIn(ExperimentalNativeApi::class, ExperimentalForeignApi::class)
@file:Suppress("unused", "FunctionName", "LocalVariableName")

package us.q3q.fidok

import co.touchlab.kermit.Logger
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVarOf
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.convert
import kotlinx.cinterop.pointed
import kotlinx.cinterop.value
import platform.posix.size_t
import platform.posix.size_tVar
import us.q3q.fidok.ctap.AuthenticatorDevice
import us.q3q.fidok.ctap.CTAPOption
import us.q3q.fidok.ctap.FIDOkLibrary
import kotlin.experimental.ExperimentalNativeApi
import kotlin.math.min

internal var library: FIDOkLibrary? = null

@CName("fido_init")
fun fido_init(flags: Int) {
    if (library == null) {
        library =
            FIDOkLibrary.init(
                cryptoProvider = BotanCryptoProvider(),
                authenticatorAccessors = platformDeviceProviders(),
            )
    }
}

class FidoDevHandle(var authenticatorDevice: AuthenticatorDevice? = null)

class FidoDevInfoHandle(
    val slots: Int,
    var devices: List<AuthenticatorDevice> = listOf(),
)

typealias fido_dev_info = COpaquePointer

typealias fido_dev_t = COpaquePointer

@OptIn(ExperimentalForeignApi::class)
@CName("fido_dev_info_new")
fun fido_dev_info_new(n: size_t): COpaquePointer {
    val devInfoHandle = FidoDevInfoHandle(n.toInt())
    return StableRef.create(devInfoHandle).asCPointer()
}

@OptIn(ExperimentalForeignApi::class)
@CName("fido_dev_info_free")
fun fido_dev_info_free(
    dev_p: CPointer<CPointerVarOf<fido_dev_info>>?,
    n: size_t,
) {
    val stableRef = dev_p?.pointed?.value ?: return
    val target = stableRef.asStableRef<FidoDevInfoHandle>()
    target.dispose()
    dev_p.pointed.value = null
}

@OptIn(ExperimentalForeignApi::class)
@CName("fido_dev_info_manifest")
fun fido_dev_info_manifest(
    dev_p: fido_dev_info?,
    ilen: size_t,
    olen: CPointer<size_tVar>,
) {
    val resultListingObj = dev_p?.asStableRef<FidoDevInfoHandle>()?.get() ?: return

    val devices = library?.listDevices()
    if (devices == null) {
        olen.pointed.value = 0.convert()
        return
    }

    val numToReturn = min(min(devices.size, ilen.toInt()), resultListingObj.slots)

    Logger.d { "libfido-compat returning $numToReturn of ${devices.size} devices (cap ${resultListingObj.slots})..." }

    if (numToReturn > 0) {
        resultListingObj.devices = devices.take(numToReturn)
    } else {
        resultListingObj.devices = listOf()
    }

    olen.pointed.value = resultListingObj.devices.size.convert()
}

@OptIn(ExperimentalForeignApi::class)
@CName("fido_dev_new")
fun fido_dev_new(): COpaquePointer {
    val devHandle = FidoDevHandle()
    return StableRef.create(devHandle).asCPointer()
}

@OptIn(ExperimentalForeignApi::class)
@CName("fido_dev_new_with_info")
fun fido_dev_new_with_info(info: fido_dev_info?): COpaquePointer? {
    val resultListingObj = info?.asStableRef<FidoDevInfoHandle>()?.get() ?: return null

    val devHandle = FidoDevHandle(resultListingObj.devices.firstOrNull())

    Logger.i { "libfido-compat device created with ${devHandle.authenticatorDevice}" }

    return StableRef.create(devHandle).asCPointer()
}

@OptIn(ExperimentalForeignApi::class)
@CName("fido_dev_free")
fun fido_dev_free(dev_p: CPointer<CPointerVarOf<fido_dev_t>>?) {
    val stableRef = dev_p?.pointed?.value ?: return
    val target = stableRef.asStableRef<FidoDevHandle>()
    target.dispose()
    dev_p.pointed.value = null
}

@OptIn(ExperimentalForeignApi::class)
@CName("fido_dev_open")
fun fido_dev_open(
    dev: fido_dev_t?,
    path: String,
) {
}

@OptIn(ExperimentalForeignApi::class)
@CName("fido_dev_open_with_info")
fun fido_dev_open_with_info(dev: fido_dev_t?) {
    val target = dev?.asStableRef<FidoDevHandle>() ?: return
    Logger.d { "libfido-compat opening ${target.get().authenticatorDevice}" }
}

@OptIn(ExperimentalForeignApi::class)
@CName("fido_dev_close")
fun fido_dev_close(dev: fido_dev_t?) {
    val target = dev?.asStableRef<FidoDevHandle>() ?: return
    target.get().authenticatorDevice = null
}

@OptIn(ExperimentalForeignApi::class)
@CName("fido_dev_has_pin")
fun fido_dev_has_pin(dev: fido_dev_t?): Boolean {
    val authenticator = dev?.asStableRef<FidoDevHandle>()?.get()?.authenticatorDevice ?: return false
    return library?.ctapClient(authenticator)?.getInfo()?.options?.get(
        CTAPOption.CLIENT_PIN.value,
    ) == true
}
