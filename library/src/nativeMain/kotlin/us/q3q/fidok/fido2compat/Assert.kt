@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
@file:Suppress("FunctionName", "unused", "LocalVariableName")

package us.q3q.fidok.fido2compat

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVarOf
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.pin
import kotlinx.cinterop.pointed
import kotlinx.cinterop.value
import kotlinx.coroutines.runBlocking
import platform.posix.size_t
import us.q3q.fidok.ctap.CTAPOption
import us.q3q.fidok.ctap.CTAPPermission
import us.q3q.fidok.ctap.commands.Extension
import us.q3q.fidok.ctap.commands.ExtensionSetup
import us.q3q.fidok.ctap.commands.GetAssertionResponse
import us.q3q.fidok.ctap.commands.HMACSecretExtension
import us.q3q.fidok.ctap.commands.PublicKeyCredentialDescriptor
import kotlin.experimental.ExperimentalNativeApi

typealias fido_assert_t = COpaquePointer

class FidoAssertHandle(
    var clientDataHash: ByteArray? = null,
    var extensions: Int = 0x00,
    var rpId: String? = null,
    var allowList: MutableList<ByteArray> = mutableListOf(),
    var hmacSalt: ByteArray? = null,
    var assertions: List<GetAssertionResponse> = listOf(),
    var hmacSecrets: List<ByteArray?> = listOf(),
)

@OptIn(ExperimentalForeignApi::class)
@CName("fido_assert_new")
fun fido_assert_new(): COpaquePointer {
    val assertHandle = FidoAssertHandle()
    return StableRef.create(assertHandle).asCPointer()
}

@OptIn(ExperimentalForeignApi::class)
@CName("fido_assert_free")
fun fido_assert_free(assert_p: CPointer<CPointerVarOf<fido_assert_t>>?) {
    val stableRef = assert_p?.pointed?.value ?: return
    val target = stableRef.asStableRef<FidoCredHandle>()
    target.dispose()
    assert_p.pointed.value = null
}

@OptIn(ExperimentalForeignApi::class)
@CName("fido_assert_set_rp")
fun fido_assert_set_rp(
    cred: fido_assert_t,
    id: String,
): Int {
    val assertHandle = cred.asStableRef<FidoAssertHandle>().get()

    assertHandle.rpId = id

    return FIDO_OK
}

@OptIn(ExperimentalForeignApi::class)
@CName("fido_dev_get_assert")
fun fido_dev_get_assert(
    dev: fido_dev_t,
    assert: fido_assert_t,
    pin: String?,
): Int {
    val devHandle = dev.asStableRef<FidoDevHandle>().get()
    val assertHandle = assert.asStableRef<FidoAssertHandle>().get()
    val authenticator = devHandle.authenticatorDevice ?: return FIDO_ERR_NOTFOUND

    val rpId = assertHandle.rpId ?: return FIDO_ERR_INVALID_PARAM

    val client =
        get_fidocompat_lib().ctapClient(
            authenticator,
            collectPin = { pin },
        )

    val extensions = arrayListOf<Extension>()
    val hmacSalt = assertHandle.hmacSalt
    var hmacSecretExtension: HMACSecretExtension? = null
    if (assertHandle.extensions.and(FIDO_EXT_HMAC_SECRET) != 0 && hmacSalt != null) {
        hmacSecretExtension =
            HMACSecretExtension(
                salt1 = hmacSalt.copyOfRange(0, 32),
                salt2 = if (hmacSalt.size > 32) hmacSalt.copyOfRange(32, 64) else null,
            )
        extensions.add(hmacSecretExtension)
    }

    var assertResponse: List<GetAssertionResponse>? = null
    val result =
        fido_do_with_error_handling {
            val pinUVToken =
                if (pin != null && client.getInfoIfUnset().options?.get(CTAPOption.CLIENT_PIN.value) == true) {
                    runBlocking {
                        client.getPinUvTokenUsingAppropriateMethod(
                            desiredPermissions = CTAPPermission.MAKE_CREDENTIAL.value,
                            desiredRpId = rpId,
                        )
                    }
                } else {
                    null
                }

            assertResponse =
                client.getAssertions(
                    clientDataHash = assertHandle.clientDataHash,
                    rpId = rpId,
                    pinUvToken = pinUVToken,
                    extensions = ExtensionSetup(extensions),
                    allowList =
                        assertHandle.allowList.map {
                            PublicKeyCredentialDescriptor(it)
                        },
                )
        }

    if (result == FIDO_OK) {
        assertHandle.assertions = assertResponse!!
        if (hmacSecretExtension != null) {
            assertHandle.hmacSecrets =
                (1..assertResponse!!.size).map {
                    val firstAndSecondSecret = hmacSecretExtension.getResult()
                    val firstSecret = firstAndSecondSecret.first
                    if (firstSecret == null) {
                        null
                    } else {
                        (firstSecret.toList() + (firstAndSecondSecret.second?.toList() ?: listOf())).toByteArray()
                    }
                }
        } else {
            assertHandle.hmacSecrets = listOf()
        }
    } else {
        assertHandle.assertions = listOf()
    }

    return result
}

@OptIn(ExperimentalForeignApi::class)
@CName("fido_assert_set_clientdata")
fun fido_assert_set_clientdata(
    assert: fido_assert_t,
    ptr: CPointer<ByteVar>?,
    len: size_t,
): Int {
    val assertHandle = assert.asStableRef<FidoAssertHandle>().get()

    if (ptr == null) {
        assertHandle.clientDataHash = null
        return FIDO_OK
    }

    val clientData =
        ByteArray(len.toInt()) {
            ptr[it]
        }
    val clientDataHash = get_fidocompat_lib().cryptoProvider.sha256(clientData).hash
    assertHandle.clientDataHash = clientDataHash

    return FIDO_OK
}

@OptIn(ExperimentalForeignApi::class)
@CName("fido_assert_set_clientdata_hash")
fun fido_assert_set_clientdata_hash(
    assert: fido_assert_t,
    ptr: CPointer<ByteVar>?,
    len: size_t,
): Int {
    val assertHandle = assert.asStableRef<FidoAssertHandle>().get()

    if (ptr == null) {
        assertHandle.clientDataHash = null
        return FIDO_OK
    }

    val clientDataHash =
        ByteArray(len.toInt()) {
            ptr[it]
        }
    assertHandle.clientDataHash = clientDataHash

    return FIDO_OK
}

@OptIn(ExperimentalForeignApi::class)
@CName("fido_assert_set_extensions")
fun fido_assert_set_extensions(
    assert: fido_assert_t,
    flags: Int,
): Int {
    val assertHandle = assert.asStableRef<FidoAssertHandle>().get()

    assertHandle.extensions = flags

    return FIDO_OK
}

@OptIn(ExperimentalForeignApi::class)
@CName("fido_assert_count")
fun fido_assert_count(assert: fido_assert_t): size_t {
    val assertHandle = assert.asStableRef<FidoAssertHandle>().get()

    return assertHandle.assertions.size.convert()
}

@OptIn(ExperimentalForeignApi::class)
@CName("fido_assert_empty_allow_list")
fun fido_assert_empty_allow_list(assert: fido_assert_t): Int {
    val assertHandle = assert.asStableRef<FidoAssertHandle>().get()

    assertHandle.allowList = mutableListOf()

    return FIDO_OK
}

@OptIn(ExperimentalForeignApi::class)
@CName("fido_assert_allow_cred")
fun fido_assert_allow_cred(
    assert: fido_assert_t,
    ptr: CPointer<ByteVar>,
    len: size_t,
): Int {
    val assertHandle = assert.asStableRef<FidoAssertHandle>().get()

    val credIdBytes =
        ByteArray(len.toInt()) {
            ptr[it]
        }
    assertHandle.allowList.add(credIdBytes)

    return FIDO_OK
}

@OptIn(ExperimentalForeignApi::class)
@CName("fido_assert_set_hmac_salt")
fun fido_assert_set_hmac_salt(
    assert: fido_assert_t,
    ptr: CPointer<ByteVar>?,
    len: size_t,
): Int {
    val assertHandle = assert.asStableRef<FidoAssertHandle>().get()

    val hmacSalt =
        if (ptr != null) {
            ByteArray(len.toInt()) {
                ptr[it]
            }
        } else {
            null
        }
    assertHandle.hmacSalt = hmacSalt

    return FIDO_OK
}

@OptIn(ExperimentalForeignApi::class)
@CName("fido_assert_hmac_secret_ptr")
fun fido_assert_hmac_secret_ptr(
    assert: fido_assert_t,
    idx: size_t,
): CPointer<ByteVar>? {
    val assertHandle = assert.asStableRef<FidoAssertHandle>().get()

    val idxInt = idx.toInt()
    if (idxInt < 0 || idxInt >= assertHandle.hmacSecrets.size) {
        return null
    }

    return assertHandle.hmacSecrets[idxInt]?.pin()?.addressOf(0)
}

@OptIn(ExperimentalForeignApi::class)
@CName("fido_assert_hmac_secret_len")
fun fido_assert_hmac_secret_len(
    assert: fido_assert_t,
    idx: size_t,
): size_t {
    val assertHandle = assert.asStableRef<FidoAssertHandle>().get()

    val idxInt = idx.toInt()
    if (idxInt < 0 || idxInt >= assertHandle.hmacSecrets.size) {
        return 0.convert()
    }

    return assertHandle.hmacSecrets[idxInt]?.size?.convert() ?: 0.convert()
}

@OptIn(ExperimentalForeignApi::class)
@CName("fido_assert_id_ptr")
fun fido_assert_id_ptr(
    assert: fido_assert_t,
    idx: size_t,
): CPointer<ByteVar>? {
    val assertHandle = assert.asStableRef<FidoAssertHandle>().get()

    val idxInt = idx.toInt()
    if (idxInt < 0 || idxInt >= assertHandle.assertions.size) {
        return null
    }

    return assertHandle.assertions[idxInt].authData.attestedCredentialData?.credentialId?.pin()?.addressOf(0)
}

@OptIn(ExperimentalForeignApi::class)
@CName("fido_assert_id_len")
fun fido_assert_id_len(
    assert: fido_assert_t,
    idx: size_t,
): size_t {
    val assertHandle = assert.asStableRef<FidoAssertHandle>().get()

    val idxInt = idx.toInt()
    if (idxInt < 0 || idxInt >= assertHandle.assertions.size) {
        return 0.convert()
    }

    return assertHandle.assertions[idxInt].authData.attestedCredentialData?.credentialId?.size?.convert() ?: 0.convert()
}

@OptIn(ExperimentalForeignApi::class)
@CName("fido_assert_user_id_ptr")
fun fido_assert_user_id_ptr(
    assert: fido_assert_t,
    idx: size_t,
): CPointer<ByteVar>? {
    val assertHandle = assert.asStableRef<FidoAssertHandle>().get()

    val idxInt = idx.toInt()
    if (idxInt < 0 || idxInt >= assertHandle.assertions.size) {
        return null
    }

    return assertHandle.assertions[idxInt].user?.id?.pin()?.addressOf(0)
}

@OptIn(ExperimentalForeignApi::class)
@CName("fido_assert_user_id_len")
fun fido_assert_user_id_len(
    assert: fido_assert_t,
    idx: size_t,
): size_t {
    val assertHandle = assert.asStableRef<FidoAssertHandle>().get()

    val idxInt = idx.toInt()
    if (idxInt < 0 || idxInt >= assertHandle.assertions.size) {
        return 0.convert()
    }

    return assertHandle.assertions[idxInt].user?.id?.size?.convert() ?: 0.convert()
}

@OptIn(ExperimentalForeignApi::class)
@CName("fido_assert_rp_id")
fun fido_assert_user_id_len(assert: fido_assert_t): String? {
    val assertHandle = assert.asStableRef<FidoAssertHandle>().get()

    return assertHandle.rpId
}

@OptIn(ExperimentalForeignApi::class)
@CName("fido_assert_user_name")
fun fido_assert_user_name(
    assert: fido_assert_t,
    idx: size_t,
): String? {
    val assertHandle = assert.asStableRef<FidoAssertHandle>().get()

    val idxInt = idx.toInt()
    if (idxInt < 0 || idxInt >= assertHandle.assertions.size) {
        return null
    }

    return assertHandle.assertions[idxInt].user?.name
}

@OptIn(ExperimentalForeignApi::class)
@CName("fido_assert_user_display_name")
fun fido_assert_user_display_name(
    assert: fido_assert_t,
    idx: size_t,
): String? {
    val assertHandle = assert.asStableRef<FidoAssertHandle>().get()

    val idxInt = idx.toInt()
    if (idxInt < 0 || idxInt >= assertHandle.assertions.size) {
        return null
    }

    return assertHandle.assertions[idxInt].user?.displayName
}

@OptIn(ExperimentalForeignApi::class)
@CName("fido_assert_sig_len")
fun fido_assert_sig_len(
    assert: fido_assert_t,
    idx: size_t,
): size_t {
    val assertHandle = assert.asStableRef<FidoAssertHandle>().get()

    val idxInt = idx.toInt()
    if (idxInt < 0 || idxInt >= assertHandle.assertions.size) {
        return 0.convert()
    }

    return assertHandle.assertions[idxInt].signature.size.convert()
}

@OptIn(ExperimentalForeignApi::class)
@CName("fido_assert_sig_ptr")
fun fido_assert_sig_ptr(
    assert: fido_assert_t,
    idx: size_t,
): CPointer<ByteVar>? {
    val assertHandle = assert.asStableRef<FidoAssertHandle>().get()

    val idxInt = idx.toInt()
    if (idxInt < 0 || idxInt >= assertHandle.assertions.size) {
        return null
    }

    return assertHandle.assertions[idxInt].signature.pin().addressOf(0)
}
