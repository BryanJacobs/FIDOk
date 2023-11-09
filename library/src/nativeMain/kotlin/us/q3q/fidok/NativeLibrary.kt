@file:Suppress("unused", "FunctionName")
@file:OptIn(ExperimentalNativeApi::class, ExperimentalForeignApi::class)

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValues
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.cstr
import kotlinx.cinterop.invoke
import kotlinx.cinterop.toKString
import us.q3q.fidok.BotanCryptoProvider
import us.q3q.fidok.FIDOkCallbacks
import us.q3q.fidok.ctap.CTAPClient
import us.q3q.fidok.ctap.FIDOkLibrary
import us.q3q.fidok.platformDeviceProviders
import kotlin.experimental.ExperimentalNativeApi

typealias PasswordCollectorFunction = CPointer<CFunction<(CValues<ByteVar>) -> CPointer<ByteVar>?>>?

@OptIn(ExperimentalForeignApi::class)
@CName("fidok_init")
fun fidok_init(passwordCollection: PasswordCollectorFunction = null): COpaquePointer {
    val library = FIDOkLibrary.init(
        cryptoProvider = BotanCryptoProvider(),
        authenticatorAccessors = platformDeviceProviders(),
        callbacks = object : FIDOkCallbacks {
            override suspend fun collectPin(client: CTAPClient?): String? {
                if (passwordCollection == null) {
                    return null
                }
                return passwordCollection(client.toString().cstr)?.toKString()
            }
        },
    )
    return StableRef.create(library).asCPointer()
}

@OptIn(ExperimentalForeignApi::class)
@CName("fidok_destroy")
fun fidok_destroy(library: COpaquePointer) {
    library.asStableRef<FIDOkLibrary>().dispose()
}
