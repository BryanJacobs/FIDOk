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
import us.q3q.fidok.ctap.commands.COSEAlgorithmIdentifier
import us.q3q.fidok.ctap.commands.CredProtectExtension
import us.q3q.fidok.ctap.commands.Extension
import us.q3q.fidok.ctap.commands.ExtensionSetup
import us.q3q.fidok.ctap.commands.HMACSecretExtension
import us.q3q.fidok.ctap.commands.MakeCredentialResponse
import us.q3q.fidok.ctap.commands.PublicKeyCredentialParameters
import kotlin.experimental.ExperimentalNativeApi

typealias fido_cred_t = COpaquePointer

const val FIDO_OK = 0
const val FIDO_ERR_TX = -1
const val FIDO_ERR_RX = -2
const val FIDO_ERR_RX_NOT_CBOR = -3
const val FIDO_ERR_RX_INVALID_CBOR = -4
const val FIDO_ERR_INVALID_PARAM = -5
const val FIDO_ERR_INVALID_SIG = -6
const val FIDO_ERR_INVALID_ARGUMENT = -7
const val FIDO_ERR_USER_PRESENCE_REQUIRED = -8
const val FIDO_ERR_INTERNAL = -9
const val FIDO_ERR_NOTFOUND = -10
const val FIDO_ERR_COMPRESS = -11

const val FIDO_OPT_OMIT = 0
const val FIDO_OPT_FALSE = 1
const val FIDO_OPT_TRUE = 2

const val FIDO_EXT_HMAC_SECRET = 0x01
const val FIDO_EXT_CRED_PROTECT = 0x02
const val FIDO_EXT_LARGEBLOB_KEY = 0x04
const val FIDO_EXT_CRED_BLOB = 0x08
const val FIDO_EXT_MINPINLEN = 0x10

const val FIDO_CRED_PROT_UV_OPTIONAL = 0x01
const val FIDO_CRED_PROT_UV_OPTIONAL_WITH_ID = 0x02
const val FIDO_CRED_PROT_UV_REQUIRED = 0x03

class FidoCredHandle(
    var clientDataHash: ByteArray? = null,
    var rpId: String? = null,
    var rpName: String? = null,
    var userId: ByteArray? = null,
    var userName: String? = null,
    var userDisplayName: String? = null,
    var type: COSEAlgorithmIdentifier = COSEAlgorithmIdentifier.ES256,
    var cred: MakeCredentialResponse? = null,
    var prot: UByte? = null,
    var rk: Boolean? = null,
    var extensions: Int = 0x00,
)

@OptIn(ExperimentalForeignApi::class)
@CName("fido_cred_new")
fun fido_cred_new(): COpaquePointer {
    val credHandle = FidoCredHandle()
    return StableRef.create(credHandle).asCPointer()
}

@OptIn(ExperimentalForeignApi::class)
@CName("fido_cred_free")
fun fido_cred_free(cred_p: CPointer<CPointerVarOf<fido_cred_t>>?) {
    val stableRef = cred_p?.pointed?.value ?: return
    val target = stableRef.asStableRef<FidoCredHandle>()
    target.dispose()
    cred_p.pointed.value = null
}

@OptIn(ExperimentalForeignApi::class)
@CName("fido_cred_set_rp")
fun fido_cred_set_rp(
    cred: fido_cred_t,
    id: String,
    name: String?,
): Int {
    val credHandle = cred.asStableRef<FidoCredHandle>().get()

    credHandle.rpId = id
    credHandle.rpName = name

    return FIDO_OK
}

@OptIn(ExperimentalForeignApi::class)
@CName("fido_dev_make_cred")
fun fido_dev_make_cred(
    dev: fido_dev_t,
    cred: fido_cred_t,
    pin: String?,
): Int {
    val devHandle = dev.asStableRef<FidoDevHandle>().get()
    val credHandle = cred.asStableRef<FidoCredHandle>().get()
    val authenticator = devHandle.authenticatorDevice ?: return FIDO_ERR_NOTFOUND

    val rpId = credHandle.rpId ?: return FIDO_ERR_INVALID_PARAM

    val client =
        get_fidocompat_lib().ctapClient(
            authenticator,
            collectPin = { pin },
        )

    val extensions = arrayListOf<Extension>()
    var credProtect: CredProtectExtension? = null
    if (credHandle.extensions.and(FIDO_EXT_HMAC_SECRET) != 0) {
        extensions.add(HMACSecretExtension())
    }
    if (credHandle.extensions.and(FIDO_EXT_CRED_PROTECT) != 0) {
        credProtect = CredProtectExtension(credHandle.prot ?: 1u)
        extensions.add(credProtect)
    }

    var credResponse: MakeCredentialResponse? = null
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

            credResponse =
                client.makeCredential(
                    clientDataHash = credHandle.clientDataHash,
                    rpId = rpId,
                    rpName = credHandle.rpName,
                    userId = credHandle.userId,
                    userName = credHandle.userName,
                    userDisplayName = credHandle.userDisplayName,
                    pubKeyCredParams =
                        listOf(
                            PublicKeyCredentialParameters(alg = credHandle.type),
                        ),
                    pinUvToken = pinUVToken,
                    extensions = ExtensionSetup(extensions),
                    discoverableCredential = credHandle.rk ?: false,
                )
        }

    if (result == FIDO_OK) {
        credHandle.cred = credResponse
        if (credProtect != null) {
            credHandle.prot = credProtect.getLevel()
        } else {
            credHandle.prot = null
        }
    } else {
        credHandle.cred = null
    }

    return result
}

@OptIn(ExperimentalForeignApi::class)
@CName("fido_cred_fmt")
fun fido_cred_fmt(cred: fido_cred_t): String? {
    val credHandle = cred.asStableRef<FidoCredHandle>().get()

    return credHandle.cred?.fmt
}

@OptIn(ExperimentalForeignApi::class)
@CName("fido_cred_id_len")
fun fido_cred_id_len(cred: fido_cred_t): size_t {
    val credHandle = cred.asStableRef<FidoCredHandle>().get()

    return credHandle.cred?.authData?.attestedCredentialData?.credentialId?.size?.convert() ?: (-1).convert<size_t>()
}

@OptIn(ExperimentalForeignApi::class)
@CName("fido_cred_id_ptr")
fun fido_cred_id_ptr(cred: fido_cred_t): CPointer<ByteVar>? {
    val credHandle = cred.asStableRef<FidoCredHandle>().get()

    return credHandle.cred?.authData?.attestedCredentialData?.credentialId?.pin()?.addressOf(0)
}

@OptIn(ExperimentalForeignApi::class)
@CName("fido_cred_aaguid_len")
fun fido_cred_aaguid_len(cred: fido_cred_t): size_t {
    val credHandle = cred.asStableRef<FidoCredHandle>().get()

    return credHandle.cred?.authData?.attestedCredentialData?.aaguid?.size?.convert() ?: (-1).convert<size_t>()
}

@OptIn(ExperimentalForeignApi::class)
@CName("fido_cred_aaguid_ptr")
fun fido_cred_aaguid_ptr(cred: fido_cred_t): CPointer<ByteVar>? {
    val credHandle = cred.asStableRef<FidoCredHandle>().get()

    return credHandle.cred?.authData?.attestedCredentialData?.aaguid?.pin()?.addressOf(0)
}

@OptIn(ExperimentalForeignApi::class)
@CName("fido_cred_set_clientdata")
fun fido_cred_set_clientdata(
    cred: fido_cred_t,
    ptr: CPointer<ByteVar>?,
    len: size_t,
): Int {
    val credHandle = cred.asStableRef<FidoCredHandle>().get()

    if (ptr == null) {
        credHandle.clientDataHash = null
        return FIDO_OK
    }

    val clientData =
        ByteArray(len.toInt()) {
            ptr[it]
        }
    val clientDataHash = get_fidocompat_lib().cryptoProvider.sha256(clientData).hash
    credHandle.clientDataHash = clientDataHash

    return FIDO_OK
}

@OptIn(ExperimentalForeignApi::class)
@CName("fido_cred_set_clientdata_hash")
fun fido_cred_set_clientdata_hash(
    cred: fido_cred_t,
    ptr: CPointer<ByteVar>?,
    len: size_t,
): Int {
    val credHandle = cred.asStableRef<FidoCredHandle>().get()

    if (ptr == null) {
        credHandle.clientDataHash = null
        return FIDO_OK
    }

    val clientDataHash =
        ByteArray(len.toInt()) {
            ptr[it]
        }
    credHandle.clientDataHash = clientDataHash

    return FIDO_OK
}

@OptIn(ExperimentalForeignApi::class)
@CName("fido_cred_set_extensions")
fun fido_cred_set_extensions(
    cred: fido_cred_t,
    flags: Int,
): Int {
    val credHandle = cred.asStableRef<FidoCredHandle>().get()

    credHandle.extensions = flags

    return FIDO_OK
}

@OptIn(ExperimentalForeignApi::class)
@CName("fido_cred_set_prot")
fun fido_cred_set_prot(
    cred: fido_cred_t,
    prot: Int,
): Int {
    val credHandle = cred.asStableRef<FidoCredHandle>().get()

    if (prot != 0) {
        if (prot < 0 || prot > 3) {
            return FIDO_ERR_INVALID_PARAM
        }
        credHandle.extensions = credHandle.extensions.or(FIDO_EXT_CRED_PROTECT)
        credHandle.prot = prot.toUByte()
    } else {
        credHandle.extensions = credHandle.extensions.and(FIDO_EXT_CRED_PROTECT.inv())
        credHandle.prot = 0u
    }

    return FIDO_OK
}

@OptIn(ExperimentalForeignApi::class)
@CName("fido_cred_prot")
fun fido_cred_prot(cred: fido_cred_t): Int {
    val credHandle = cred.asStableRef<FidoCredHandle>().get()

    if (credHandle.extensions.and(FIDO_EXT_CRED_PROTECT) == 0) {
        return 0
    }
    return credHandle.prot?.toInt() ?: 0
}

@OptIn(ExperimentalForeignApi::class)
@CName("fido_cred_set_rk")
fun fido_cred_set_rk(
    cred: fido_cred_t,
    rk: Int,
): Int {
    val credHandle = cred.asStableRef<FidoCredHandle>().get()

    if (rk == FIDO_OPT_TRUE) {
        credHandle.rk = true
    } else if (rk == FIDO_OPT_FALSE || rk == FIDO_OPT_OMIT) {
        credHandle.rk = false
    } else {
        return FIDO_ERR_INVALID_ARGUMENT
    }

    return FIDO_OK
}
