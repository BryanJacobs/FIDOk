package us.q3q.fidok.ez

import us.q3q.fidok.crypto.AES256Key
import us.q3q.fidok.ctap.CTAPClient
import us.q3q.fidok.ctap.CTAPPinPermission
import us.q3q.fidok.ctap.FIDOkLibrary
import us.q3q.fidok.ctap.commands.ExtensionSetup
import us.q3q.fidok.ctap.commands.HMACSecretExtension
import us.q3q.fidok.ctap.commands.PublicKeyCredentialDescriptor
import kotlin.coroutines.cancellation.CancellationException

private const val EZ_HMAC_SECRET_USER = "FIDOk EZ hmac-secret"

class EZHmac(
    private val library: FIDOkLibrary,
    private val rpId: String = "fidok.nodomain",
) {
    companion object {

        val EZ_HMAC_DEFAULT_SALT = "EZHMACSaltDoNotThinkThisIsSecret".encodeToByteArray()

        private fun supportsHMACSecret(client: CTAPClient): Boolean {
            return client.getInfoIfUnset().extensions?.contains("hmac-secret") == true
        }
    }

    class InvalidKeyException : IllegalArgumentException("Key does not match given data")

    suspend fun setup(): ByteArray {
        val client = library.waitForUsableAuthenticator(preFilter = ::supportsHMACSecret)

        val extension = HMACSecretExtension()

        val credential = client.makeCredential(
            rpId = rpId,
            userName = EZ_HMAC_SECRET_USER,
            extensions = ExtensionSetup(extension),
            validateAttestation = false,
        )

        if (!extension.wasCreated()) {
            throw IllegalStateException("Failed to create hmac-secret")
        }

        return credential.getCredentialID()
    }

    suspend fun getKeys(setup: ByteArray, salt1: ByteArray = EZ_HMAC_DEFAULT_SALT, salt2: ByteArray? = null): Pair<ByteArray, ByteArray?> {
        val client = library.waitForUsableAuthenticator(preFilter = ::supportsHMACSecret)

        val uvToken = client.getPinUvTokenUsingAppropriateMethod(
            CTAPPinPermission.GET_ASSERTION.value,
            desiredRpId = rpId,
        )

        val extension = HMACSecretExtension(salt1, salt2)
        val assertions = client.getAssertions(
            rpId = rpId,
            pinUvToken = uvToken,
            extensions = ExtensionSetup(extension),
            allowList = listOf(
                PublicKeyCredentialDescriptor(
                    id = setup,
                ),
            ),
        )

        if (assertions.size != 1) {
            throw IllegalStateException("Did not get exactly one assertion in EZ HMAC key fetch")
        }

        val hmacs = extension.getResult()
        val first = hmacs.first
            ?: throw IllegalStateException("Failed to retrieve hmac-secret value from Authenticator")

        return Pair(first, hmacs.second)
    }

    private fun encryptUsingKey(key: ByteArray, data: ByteArray): ByteArray {
        if (data.size % 16 != 0) {
            throw IllegalArgumentException("Input data is not a multiple of 16 bytes long")
        }

        val iv = library.cryptoProvider.secureRandom(16)

        val encrypted = library.cryptoProvider.aes256CBCEncrypt(
            data,
            AES256Key(
                key = key,
                iv = iv,
            ),
        )

        val validation = library.cryptoProvider.hmacSHA256(
            (iv.toList() + encrypted.toList()).toByteArray(),
            AES256Key(key),
        ).hash

        return (
            iv.toList() + encrypted.toList() + validation.toList()
            ).toByteArray()
    }

    @Throws(InvalidKeyException::class)
    private fun checkCorrectKeyForDecryption(key: ByteArray, data: ByteArray) {
        val dataBeingValidated = data.copyOfRange(0, data.size - 32)
        val validation = data.copyOfRange(data.size - 32, data.size)

        val comparedValidation = library.cryptoProvider.hmacSHA256(
            dataBeingValidated,
            AES256Key(
                key,
            ),
        ).hash

        if (!validation.contentEquals(comparedValidation)) {
            throw InvalidKeyException()
        }
    }

    suspend fun encrypt(setup: ByteArray, data: ByteArray, salt: ByteArray = EZ_HMAC_DEFAULT_SALT): ByteArray {
        val keys = getKeys(setup, salt)

        return encryptUsingKey(keys.first, data)
    }

    suspend fun encryptAndRotate(setup: ByteArray, data: ByteArray, salt1: ByteArray, salt2: ByteArray): Pair<ByteArray, ByteArray> {
        val keys = getKeys(setup, salt1, salt2)
        val secondKey = keys.second
            ?: throw IllegalStateException("Somehow didn't get two HMAC salts back from key fetching!")

        return Pair(
            encryptUsingKey(keys.first, data),
            encryptUsingKey(secondKey, data),
        )
    }

    @Throws(InvalidKeyException::class, CancellationException::class)
    suspend fun decrypt(setup: ByteArray, data: ByteArray, salt: ByteArray = EZ_HMAC_DEFAULT_SALT): ByteArray {
        require(data.size >= 48)

        val keys = getKeys(setup, salt)

        checkCorrectKeyForDecryption(keys.first, data)

        val iv = data.copyOfRange(0, 16)
        val toDecrypt = data.copyOfRange(16, data.size - 32)

        return library.cryptoProvider.aes256CBCDecrypt(
            toDecrypt,
            AES256Key(
                key = keys.first,
                iv = iv,
            ),
        )
    }
}
