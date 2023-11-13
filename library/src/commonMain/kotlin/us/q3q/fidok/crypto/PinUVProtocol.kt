package us.q3q.fidok.crypto

import us.q3q.fidok.ctap.PinUVToken

/**
 * Represents the way in which user-provided verification parameters influence CTAP requests/responses.
 *
 * The `PinUVProtocol` can be used to authenticate, encrypt, or decrypt data. The operations in this class
 * are abstract, and not bound to particular cryptography schemes.
 *
 * @see CryptoProvider
 */
interface PinUVProtocol {
    /**
     * Encipher data using the negotiated authenticator-platform shared secret.
     *
     * @param key The result of a key agreement operation
     * @param data The data to be enciphered
     * @return The enciphered representation of `data`, in a way specific to this protocol
     */
    fun encrypt(
        key: KeyAgreementPlatformKey,
        data: ByteArray,
    ): ByteArray

    /**
     * Decipher data using the negotiated authenticator-platform shared secret.
     *
     * @param key The result of a key agreement operation
     * @param data The result of a call to [encrypt] for this protocol, possibly by
     *             the Authenticator rather than the Platform
     * @return The original data passed to [encrypt]
     */
    fun decrypt(
        key: KeyAgreementPlatformKey,
        data: ByteArray,
    ): ByteArray

    /**
     * Authenticate data using the authenticator-platform shared secret.
     *
     * @param key The result of a key agreement operation
     * @param data Arbitrary data to be authenticated
     * @return Bytes that may be used to verify that the caller of this function had
     *         possession of the `key`
     */
    fun authenticate(
        key: KeyAgreementPlatformKey,
        data: ByteArray,
    ): ByteArray

    /**
     * Authenticate data using a [PinUVToken] directly, producing a signature.
     *
     * @param pinUVToken A token obtained from the Authenticator (hopefully still valid) in
     *                   response to a PIN/UV operation
     * @param data Arbitrary data to be authenticated
     * @return Bytes that may be used to verify that the caller of this function had
     *         possession of the PIN/UV token
     */
    fun authenticate(
        pinUVToken: PinUVToken,
        data: ByteArray,
    ): ByteArray

    /**
     * Verify that the given data were signed using the shared authenticator-platform secret.
     *
     * @param key The result of a key agreement operation
     * @param data Data signed by the given `signature`
     * @param signature The result of an [authenticate] call with this protocol, possibly by
     *                  the Authenticator
     * @return true if the `signature` is valid; false otherwise
     */
    fun verify(
        key: KeyAgreementPlatformKey,
        data: ByteArray,
        signature: ByteArray,
    ): Boolean {
        val result = authenticate(key, data)
        return result.contentEquals(signature)
    }

    /**
     * Gets the CBOR-encoded representation of this PIN/UV protocol's version number
     */
    fun getVersion(): UByte
}
