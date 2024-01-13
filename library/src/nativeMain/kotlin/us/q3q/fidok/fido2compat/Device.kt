@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
@file:Suppress("FunctionName", "unused")

package us.q3q.fidok.fido2compat

import co.touchlab.kermit.Logger
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVarOf
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.pointed
import kotlinx.cinterop.value
import us.q3q.fidok.ctap.AuthenticatorDevice
import us.q3q.fidok.ctap.CTAPOption
import us.q3q.fidok.ctap.DeviceCommunicationException
import kotlin.experimental.ExperimentalNativeApi

typealias fido_dev_t = COpaquePointer

class FidoDevHandle(var authenticatorDevice: AuthenticatorDevice? = null)

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
    val client = get_fidocompat_lib().ctapClient(authenticator)
    return try {
        client.getInfoIfUnset().options?.get(
            CTAPOption.CLIENT_PIN.value,
        ) == true
    } catch (e: DeviceCommunicationException) {
        false
    }
}
