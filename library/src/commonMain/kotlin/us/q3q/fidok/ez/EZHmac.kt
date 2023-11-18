package us.q3q.fidok.ez

import us.q3q.fidok.crypto.AES256Key
import us.q3q.fidok.ctap.CTAPClient
import us.q3q.fidok.ctap.CTAPPermission
import us.q3q.fidok.ctap.FIDOkLibrary
import us.q3q.fidok.ctap.IncorrectDataException
import us.q3q.fidok.ctap.commands.ExtensionSetup
import us.q3q.fidok.ctap.commands.HMACSecretExtension
import us.q3q.fidok.ctap.commands.PublicKeyCredentialDescriptor
import kotlin.coroutines.cancellation.CancellationException

private const val EZ_HMAC_SECRET_USER = "FIDOk EZ hmac-secret"

/**
 * Provides an "easy" interface to encrypting/decrypting data using the
 * [hmac-secret extension][us.q3q.fidok.ctap.commands.HMACSecretExtension].
 *
 * @property library A configured FIDOk library instance
 * @property rpId The Relying Party ID to use for generated Credentials/Assertions. Must match between [setup] and
 * [use][encryptUsingKey]!
 */
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

    /**
     * Prepare to encrypt/decrypt data.
     *
     * Each time this is called, it will return a new handle usable for encryption or decryption. Data encrypted
     * using a given handle may only be decrypted using both that handle and the Authenticator that created it.
     *
     * Under the hood, this is making a new FIDO Credential.
     *
     * @return Handle usable for [encrypt] or [decrypt]
     * @throws IllegalStateException If the Authenticator refused to enable the hmac-secret extension
     */
    @Throws(IllegalStateException::class)
    suspend fun setup(): ByteArray {
        val client = library.waitForUsableAuthenticator(preFilter = ::supportsHMACSecret)

        val extension = HMACSecretExtension()

        val credential =
            client.makeCredential(
                rpId = rpId,
                userName = EZ_HMAC_SECRET_USER,
                extensions = ExtensionSetup(extension),
                validateAttestation = false,
            )

        if (!extension.wasCreated()) {
            throw IllegalStateException("Failed to create hmac-secret")
        }

        return (listOf(0x01.toByte()) + credential.getCredentialID().toList()).toByteArray()
    }

    private fun getCredentialFromHandle(setup: ByteArray): ByteArray {
        if (setup[0] != 0x01.toByte()) {
            throw IncorrectDataException("Unknown setup passed to EZHMAC method")
        }
        return setup.copyOfRange(1, setup.size)
    }

    /**
     * Get HMAC secret(s) in their raw form.
     *
     * While this is an "easy" class, this particular method is more advanced. It has the following properties:
     *
     * - When given the same input, it will return the same output.
     * - A given [setup] is only usable with the Authenticator that created it
     * - A change in the input alters the output in unpredictable ways
     *
     * @param setup The result of calling [EZHmac.setup]
     * @param salt1 First "salt" to get a key
     * @param salt2 Second "salt" to get a key
     * @return Pair of the result of HMACing [salt1] and [salt2]
     */
    suspend fun getKeys(
        setup: ByteArray,
        salt1: ByteArray = EZ_HMAC_DEFAULT_SALT,
        salt2: ByteArray? = null,
    ): Pair<ByteArray, ByteArray?> {
        require(salt1.size == 32)
        require(salt2 == null || salt2.size == 32)

        val client = library.waitForUsableAuthenticator(preFilter = ::supportsHMACSecret)

        val uvToken =
            client.getPinUvTokenUsingAppropriateMethod(
                CTAPPermission.GET_ASSERTION.value,
                desiredRpId = rpId,
            )

        val extension = HMACSecretExtension(salt1, salt2)
        val assertions =
            client.getAssertions(
                rpId = rpId,
                pinUvToken = uvToken,
                extensions = ExtensionSetup(extension),
                allowList =
                    listOf(
                        PublicKeyCredentialDescriptor(
                            id = getCredentialFromHandle(setup),
                        ),
                    ),
            )

        if (assertions.size != 1) {
            throw IllegalStateException("Did not get exactly one assertion in EZ HMAC key fetch")
        }

        val hmacs = extension.getResult()
        val first =
            hmacs.first
                ?: throw IllegalStateException("Failed to retrieve hmac-secret value from Authenticator")

        return Pair(first, hmacs.second)
    }

    private fun encryptUsingKey(
        key: ByteArray,
        data: ByteArray,
    ): ByteArray {
        val iv = library.cryptoProvider.secureRandom(16)

        // Pad to a multiple of 16 bytes, storing the number of padding bytes in the last byte
        var necessaryPaddingBytes = 16 - (data.size % 16)
        if (necessaryPaddingBytes == 0) {
            necessaryPaddingBytes = 16
        }
        val toEncrypt = data.toList() + List(necessaryPaddingBytes - 1) { 0x00 } + listOf(necessaryPaddingBytes.toByte())

        val encrypted =
            library.cryptoProvider.aes256CBCEncrypt(
                toEncrypt.toByteArray(),
                AES256Key(
                    key = key,
                    iv = iv,
                ),
            )

        val validation =
            library.cryptoProvider.hmacSHA256(
                (iv.toList() + encrypted.toList()).toByteArray(),
                AES256Key(key),
            ).hash

        return (
            iv.toList() + encrypted.toList() + validation.toList()
        ).toByteArray()
    }

    @Throws(InvalidKeyException::class)
    private fun checkCorrectKeyForDecryption(
        key: ByteArray,
        data: ByteArray,
    ) {
        val dataBeingValidated = data.copyOfRange(0, data.size - 32)
        val validation = data.copyOfRange(data.size - 32, data.size)

        val comparedValidation =
            library.cryptoProvider.hmacSHA256(
                dataBeingValidated,
                AES256Key(
                    key,
                ),
            ).hash

        if (!validation.contentEquals(comparedValidation)) {
            throw InvalidKeyException()
        }
    }

    /**
     * Encrypt data.
     *
     * @param setup The result of calling [EZHmac.setup]
     * @param data Bytes to be encrypted
     * @param salt Optionally, a "salt". Without providing the same salt to the decryption function, decryption
     * will fail.
     * @return Encrypted data, which may be decrypted via [decrypt]
     */
    suspend fun encrypt(
        setup: ByteArray,
        data: ByteArray,
        salt: ByteArray = EZ_HMAC_DEFAULT_SALT,
    ): ByteArray {
        val keys = getKeys(setup, salt)

        return encryptUsingKey(keys.first, data)
    }

    /**
     * Decrypt data in one way, and re-encrypt it in another.
     *
     * This is useful to only require using an Authenticator once, but allow "rotating" from one salt to another.
     *
     * @param setup The result of calling [EZHmac.setup]
     * @param previouslyEncryptedData Result of having called [encrypt] with [previousSalt]
     * @param newSalt New salt to use for re-encryption
     * @param previousSalt Salt to use for decryption
     * @param newData New data to re-encrypt (defaults to the result of decrypting the old data)
     * @return A pair, whose first element is [previouslyEncryptedData] decrypted using [previousSalt], and second
     * element is [newData] encrypted using [newSalt]
     */
    suspend fun decryptAndRotate(
        setup: ByteArray,
        previouslyEncryptedData: ByteArray,
        newSalt: ByteArray,
        previousSalt: ByteArray = EZ_HMAC_DEFAULT_SALT,
        newData: ByteArray? = null,
    ): Pair<ByteArray, ByteArray> {
        require(previouslyEncryptedData.size >= 64)

        val keys = getKeys(setup, previousSalt, newSalt)
        val secondKey =
            keys.second
                ?: throw IllegalStateException("Somehow didn't get two HMAC salts back from key fetching!")

        val decrypted = decryptUsingKey(keys.first, previouslyEncryptedData)
        val dataToEncrypt = newData ?: decrypted

        return Pair(
            decrypted,
            encryptUsingKey(secondKey, dataToEncrypt),
        )
    }

    /**
     * Decrypt previously-encrypted data.
     *
     * @param setup Result of calling [EZHmac.setup]
     * @param data Result of calling [encrypt]
     * @param salt Optional salt. Must match the value given to [encrypt]
     * @return Decrypted data
     * @throws InvalidKeyException If the given [setup] and/or [salt] do not match the given [data]
     */
    @Throws(InvalidKeyException::class, CancellationException::class)
    suspend fun decrypt(
        setup: ByteArray,
        data: ByteArray,
        salt: ByteArray = EZ_HMAC_DEFAULT_SALT,
    ): ByteArray {
        require(data.size >= 64)

        val keys = getKeys(setup, salt)

        return decryptUsingKey(keys.first, data)
    }

    private fun decryptUsingKey(
        key: ByteArray,
        data: ByteArray,
    ): ByteArray {
        checkCorrectKeyForDecryption(key, data)

        val iv = data.copyOfRange(0, 16)
        val toDecrypt = data.copyOfRange(16, data.size - 32)

        val decrypted =
            library.cryptoProvider.aes256CBCDecrypt(
                toDecrypt,
                AES256Key(
                    key = key,
                    iv = iv,
                ),
            )

        val numPaddingBytes = decrypted[decrypted.size - 1]

        return decrypted.copyOfRange(0, decrypted.size - numPaddingBytes)
    }
}
