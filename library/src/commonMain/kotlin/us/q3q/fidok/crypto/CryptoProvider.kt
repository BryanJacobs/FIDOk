package us.q3q.fidok.crypto

/**
 * Provides support for cryptographic operations to the FIDOk library, possibly in a platform-dependent way.
 */
interface CryptoProvider {
    /**
     * Perform the first step of an Elliptic Curve Diffie-Hellman key agreement on the NIST P-256 curve,
     * with a SHA-256 hash algorithm.
     *
     * This is called once before potentially many calls to [ecdhKeyAgreementKDF]. It needs to generate
     * a local random key suitable for ECDH.
     *
     * Each call to this function will eventually be accompanied by a call to [ecdhKeyAgreementDestroy],
     * so it's acceptable for this function to allocate memory/state externally.
     *
     * @param otherPublicKeyPoint The public key point of the "other side" of the key agreement.
     * @return A state-tracking object to be used for [ecdhKeyAgreementKDF], and includes the generated
     *         public key points chosen (not the points from `otherPublicKeyPoint`). Any private key info
     *         should be stored in the `opaqueState` member of the return object
     */
    fun ecdhKeyAgreementInit(otherPublicKeyPoint: P256Point): KeyAgreementState

    /**
     * Derive a shared secret by combining a local private key with a remote public key.
     *
     * The genereration of a local private key should have happened in [ecdhKeyAgreementInit] prior
     * to calling this function, so the KDF here must be deterministic.
     *
     * @param state The result of a prior call to [ecdhKeyAgreementInit] on this object
     * @param otherPublicKeyPoint The public key of the remote side of the key agreement
     * @param useHKDF If true, perform HKDF (both extract and expand) to get the shared secret. If false,
     *                use KDF1 (also known as just SHA-256 hashing the KDF result)
     * @param salt If using HKDF, use the given value as "salt"
     * @param info If using HKDF, use the given value as "info"
     * @return The derived shared secret: 32 bytes long
     */
    fun ecdhKeyAgreementKDF(
        state: KeyAgreementState,
        otherPublicKeyPoint: P256Point,
        useHKDF: Boolean,
        salt: ByteArray,
        info: ByteArray,
    ): KeyAgreementResult

    /**
     * Destroy the result of a call to [ecdhKeyAgreementInit], freeing any externally allocated resources.
     *
     * @param state The result of a call to [ecdhKeyAgreementInit] on this object
     */
    fun ecdhKeyAgreementDestroy(state: KeyAgreementState)

    /**
     * Gets the SHA-256 hash of some data
     *
     * @param data Incoming bytes, arbitrary length
     * @return The result of applying the SHA-256 hash algorithm to `data`
     */
    fun sha256(data: ByteArray): SHA256Result

    /**
     * Securely generate random bytes, via whatever means
     *
     * @param numBytes The number of bytes of random data to generate
     * @return A byte array with length `numBytes` containing the generated randomness
     */
    fun secureRandom(numBytes: Int): ByteArray

    /**
     * Perform AES-256 encryption using the Cipher Block Chaining mode
     *
     * @param bytes Bytes to encrypt. Will have a length that is a multiple of the AES-256 block size (16 bytes).
     * @param key AES-256 key to use for encryption, including any applicable IV
     * @return Encrypted data
     */
    fun aes256CBCEncrypt(bytes: ByteArray, key: AES256Key): ByteArray

    /**
     * See [aes256CBCEncrypt]; this is the same, but decrypting data instead of encrypting it
     */
    fun aes256CBCDecrypt(bytes: ByteArray, key: AES256Key): ByteArray

    /**
     * Generate a Hashed Message Authentication Code using AES256 and SHA-256 for the input data
     *
     * @param bytes Incoming data to hash
     * @param key Key to use for the HMAC; any IV will be ignored
     * @return The resulting authenticated hash
     */
    fun hmacSHA256(bytes: ByteArray, key: AES256Key): SHA256Result

    /**
     * Validate an existing ECDSA signature on NIST P-256
     *
     * @param signedBytes The payload whose signature is being verified
     * @param key The P-256 curve point of the public key associated with the signature
     * @param sig The bytes of the signature to validate, using BER encoding
     * @return true if the signature successfully validates; false otherwise
     */
    fun es256SignatureValidate(signedBytes: ByteArray, key: P256Point, sig: ByteArray): Boolean

    /**
     * Parse useful information from an X.509 certificate containing an ECDSA key on NIST P-256
     *
     * @param x509Bytes DER-encoded X.509 certificate
     * @return Public key information and potentially bound AAGUID for the given certificate
     */
    fun parseES256X509(x509Bytes: ByteArray): X509Info
}
