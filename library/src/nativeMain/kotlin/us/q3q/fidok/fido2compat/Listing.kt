@file:OptIn(ExperimentalNativeApi::class, ExperimentalForeignApi::class)
@file:Suppress("unused", "FunctionName", "LocalVariableName")

package us.q3q.fidok.fido2compat

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
import kotlin.experimental.ExperimentalNativeApi
import kotlin.math.min

class FidoDevInfoHandle(
    val slots: Int,
    var devices: List<AuthenticatorDevice> = listOf(),
)

typealias fido_dev_info = COpaquePointer

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

    val devices = get_fidocompat_lib().listDevices()

    val numToReturn = min(min(devices.size, ilen.toInt()), resultListingObj.slots)

    Logger.d { "libfido-compat returning $numToReturn of ${devices.size} devices (cap ${resultListingObj.slots})..." }

    if (numToReturn > 0) {
        resultListingObj.devices = devices.take(numToReturn)
    } else {
        resultListingObj.devices = listOf()
    }

    olen.pointed.value = resultListingObj.devices.size.convert()
}
