@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
@file:Suppress("FunctionName", "unused", "LocalVariableName")

package us.q3q.fidok.fido2compat

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CArrayPointer
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.CPointerVarOf
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.convert
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.getBytes
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.pointed
import kotlinx.cinterop.set
import kotlinx.cinterop.value
import platform.posix.size_t
import us.q3q.fidok.ctap.commands.GetInfoResponse
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.createCleaner

typealias fido_info_t = COpaquePointer

class FidoInfoHandle(
    var info: GetInfoResponse? = null,
    var nativeInfoPtr: CArrayPointer<CPointerVar<ByteVar>>? = null,
)

@OptIn(ExperimentalForeignApi::class)
@CName("fido_cbor_info_new")
fun fido_cbor_info_new(): COpaquePointer {
    val infoHandle = FidoInfoHandle()
    return StableRef.create(infoHandle).asCPointer()
}

@OptIn(ExperimentalForeignApi::class)
@CName("fido_cbor_info_free")
fun fido_cbor_info_free(ci_p: CPointer<CPointerVarOf<fido_info_t>>?) {
    val stableRef = ci_p?.pointed?.value ?: return
    val target = stableRef.asStableRef<FidoInfoHandle>()
    target.dispose()
    ci_p.pointed.value = null
}

@OptIn(ExperimentalForeignApi::class)
@CName("fido_dev_get_cbor_info")
fun fido_dev_get_cbor_info(
    dev: fido_dev_t,
    ci: fido_info_t,
): Int {
    val devHandle = dev.asStableRef<FidoDevHandle>().get()
    val infoHandle = ci.asStableRef<FidoInfoHandle>().get()

    val authenticator = devHandle.authenticatorDevice ?: return FIDO_ERR_TX

    val client =
        get_fidocompat_lib().ctapClient(
            authenticator,
        )

    return fido_do_with_error_handling {
        infoHandle.info = client.getInfo()
    }
}

internal fun cleanUpNativeInfoAlloc(handle: FidoInfoHandle) {
    val extensionsInPlace = handle.info?.extensions
    val nativePtr = handle.nativeInfoPtr
    handle.nativeInfoPtr = null
    if (extensionsInPlace != null && nativePtr != null) {
        for (i in extensionsInPlace.indices) {
            val ptr = nativePtr[i]
            if (ptr != null) {
                nativeHeap.free(ptr.rawValue)
            }
        }
        nativeHeap.free(nativePtr.rawValue)
    }
}

@OptIn(ExperimentalForeignApi::class)
@CName("fido_cbor_info_extensions_ptr")
fun fido_cbor_info_extensions_ptr(ci: fido_info_t): CPointerVarOf<CPointer<ByteVar>>? {
    val infoHandle = ci.asStableRef<FidoInfoHandle>().get()

    val extensions = infoHandle.info?.extensions ?: return null

    val extensionsPtr = nativeHeap.allocArray<CPointerVar<ByteVar>>(extensions.size)

    for (i in extensions.indices) {
        val cstr = extensions[i].cstr
        val heapAlloc = nativeHeap.allocArray<ByteVar>(cstr.size)
        for (j in 0..<cstr.size) {
            heapAlloc[j] = cstr.getBytes()[j]
        }
        extensionsPtr[i] = heapAlloc
    }

    if (infoHandle.nativeInfoPtr == null) {
        createCleaner(infoHandle) {
            cleanUpNativeInfoAlloc(it)
        }
    } else {
        cleanUpNativeInfoAlloc(infoHandle)
    }
    infoHandle.nativeInfoPtr = extensionsPtr

    return extensionsPtr.pointed
}

@OptIn(ExperimentalForeignApi::class)
@CName("fido_cbor_info_extensions_len")
fun fido_cbor_info_extensions_len(ci: fido_info_t): size_t {
    val infoHandle = ci.asStableRef<FidoInfoHandle>().get()

    return infoHandle.info?.extensions?.size?.convert() ?: 0.convert()
}
