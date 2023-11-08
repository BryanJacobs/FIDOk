package us.q3q.fidok.ctap

import co.touchlab.kermit.Logger
import us.q3q.fidok.ctap.commands.COSEKey
import us.q3q.fidok.ctap.commands.CredentialManagementCommand
import us.q3q.fidok.ctap.commands.CredentialManagementGetMetadataResponse
import us.q3q.fidok.ctap.commands.EnumerateCredentialsResponse
import us.q3q.fidok.ctap.commands.EnumerateRPsResponse
import us.q3q.fidok.ctap.commands.PublicKeyCredentialDescriptor
import us.q3q.fidok.ctap.commands.PublicKeyCredentialRpEntity
import us.q3q.fidok.ctap.commands.PublicKeyCredentialUserEntity

/**
 * Provides methods for managing an Authenticator's stored Discoverable Credentials.
 */
class CredentialManagementClient internal constructor(private val client: CTAPClient) {

    /**
     * Gets information about how many Discoverable Credentials are stored, the Authenticator capacity, etc.
     *
     * @param pinProtocol The PIN/UV protocol version in use
     * @param pinUVToken A PIN/UV token obtained from the Authenticator
     * @return Meta-info about the Authenticator and its creds
     */
    suspend fun getCredsMetadata(pinProtocol: UByte? = null, pinUVToken: PinUVToken? = null): CredentialManagementGetMetadataResponse {
        val pp = client.getPinProtocol(pinProtocol)
        val effectiveUVToken = pinUVToken ?: client.getPinUvTokenUsingAppropriateMethod(
            desiredPermissions = CTAPPinPermission.CREDENTIAL_MANAGEMENT.value,
        )

        val pinUvAuthParam = pp.authenticate(effectiveUVToken, byteArrayOf(0x01))
        val fullySupported = client.getInfoIfUnset().options?.get(CTAPOption.CREDENTIALS_MANAGEMENT.value) == true

        val command = CredentialManagementCommand.getCredsMetadata(
            pinUvAuthProtocol = pp.getVersion(),
            pinUvAuthParam = pinUvAuthParam,
            ctap21Implementation = fullySupported,
        )

        return client.xmit(command, CredentialManagementGetMetadataResponse.serializer())
    }

    /**
     * Gets a list of the Relying Parties for which Discoverable Credentials are stored on the Authenticator.
     *
     * @param pinProtocol The PIN/UV protocol version in use
     * @param pinUVToken A PIN/UV token obtained from the Authenticator
     * @return A list of Relying Party info, and the SHA-256 of each Relying Party's ID
     */
    suspend fun enumerateRPs(pinProtocol: UByte? = null, pinUVToken: PinUVToken? = null): List<RPWithHash> {
        val pp = client.getPinProtocol(pinProtocol)
        val effectiveUVToken = pinUVToken ?: client.getPinUvTokenUsingAppropriateMethod(
            desiredPermissions = CTAPPinPermission.CREDENTIAL_MANAGEMENT.value,
        )

        val pinUvAuthParam = pp.authenticate(effectiveUVToken, byteArrayOf(0x02))
        val fullySupported = client.getInfoIfUnset().options?.get(CTAPOption.CREDENTIALS_MANAGEMENT.value) == true

        val command = CredentialManagementCommand.enumerateRPsBegin(
            pinUvAuthProtocol = pp.getVersion(),
            pinUvAuthParam = pinUvAuthParam,
            ctap21Implementation = fullySupported,
        )

        val initialRes = client.xmit(command, EnumerateRPsResponse.serializer())

        val totalRPs = initialRes.totalRPs?.toInt() ?: 1

        val allRPs = arrayListOf<RPWithHash>()

        if (initialRes.rp != null && initialRes.rpIDHash != null) {
            allRPs.add(RPWithHash(initialRes.rpIDHash, initialRes.rp))
        }

        val enumerateCommand = CredentialManagementCommand.enumerateRPsGetNextRP(ctap21Implementation = fullySupported)
        for (i in 1..<totalRPs) {
            val res = client.xmit(enumerateCommand, EnumerateRPsResponse.serializer())
            if (res.totalRPs != null) {
                throw IncorrectDataException("Present totalRPs on enumerate-RPs follow-up")
            }
            if (res.rp == null || res.rpIDHash == null) {
                throw IncorrectDataException("Null RP fields found in RP enumeration")
            }
            allRPs.add(RPWithHash(res.rpIDHash, res.rp))
        }

        return allRPs
    }

    /**
     * Gets a list of the Discoverable Credentials the Authenticator is storing for a particular Relying Party
     *
     * @param rpIDHash The SHA-256 hash of the Relying Party ID being requested
     * @param pinProtocol The PIN/UV protocol version in use
     * @param pinUVToken A PIN/UV token obtained from the Authenticator
     * @return A list of the stored Credentials
     */
    suspend fun enumerateCredentials(rpIDHash: ByteArray, pinProtocol: UByte? = null, pinUVToken: PinUVToken? = null): List<StoredCredentialData> {
        val pp = client.getPinProtocol(pinProtocol)
        val fullySupported = client.getInfoIfUnset().options?.get(CTAPOption.CREDENTIALS_MANAGEMENT.value) == true
        val effectiveUVToken = pinUVToken ?: client.getPinUvTokenUsingAppropriateMethod(
            desiredPermissions = CTAPPinPermission.CREDENTIAL_MANAGEMENT.value,
        )

        val command = CredentialManagementCommand.enumerateCredentialsBegin(
            pinUvAuthProtocol = pp.getVersion(),
            rpIDHash = rpIDHash,
            ctap21Implementation = fullySupported,
        )

        command.pinUvAuthParam = pp.authenticate(effectiveUVToken, command.getUvParamData())
        command.params = command.generateParams()

        val initialRes = client.xmit(command, EnumerateCredentialsResponse.serializer())

        val totalCredentials = initialRes.totalCredentials?.toInt() ?: 1

        Logger.i { "Authenticator has $totalCredentials credential(s) when enumerating" }

        val allRPs = arrayListOf(StoredCredentialData(initialRes))

        val enumerateCommand = CredentialManagementCommand.enumerateCredentialsGetNextCredential(ctap21Implementation = fullySupported)
        for (i in 1..<totalCredentials) {
            val res = client.xmit(enumerateCommand, EnumerateCredentialsResponse.serializer())
            if (res.totalCredentials != null) {
                throw IncorrectDataException("Present totalCredentials on enumerate-credentials follow-up")
            }
            allRPs.add(StoredCredentialData(res))
        }

        return allRPs
    }

    /**
     * Delete a stored Discoverable Credential from the Authenticator.
     *
     * @param credentialID The Credential to delete
     * @param pinProtocol The PIN/UV protocol version in use
     * @param pinUVToken A PIN/UV token obtained from the Authenticator
     */
    suspend fun deleteCredential(
        credentialID: PublicKeyCredentialDescriptor,
        pinProtocol: UByte? = null,
        pinUVToken: PinUVToken? = null,
    ) {
        val effectiveUVToken = pinUVToken ?: client.getPinUvTokenUsingAppropriateMethod(
            desiredPermissions = CTAPPinPermission.CREDENTIAL_MANAGEMENT.value,
        )

        val pp = client.getPinProtocol(pinProtocol)

        val fullySupported = client.getInfoIfUnset().options?.get(CTAPOption.CREDENTIALS_MANAGEMENT.value) == true

        val command = CredentialManagementCommand.deleteCredential(
            pinUvAuthProtocol = pp.getVersion(),
            credentialId = credentialID,
            ctap21Implementation = fullySupported,
        )
        command.pinUvAuthParam = pp.authenticate(effectiveUVToken, command.getUvParamData())
        command.params = command.generateParams()

        client.xmit(command)
    }

    /**
     * Update the user information stored in conjunction with a particular Discoverable Credential.
     *
     * This allows "changing the username" associated with a Credential or just updating the user display name.
     *
     * @param credentialID The Credential for which user information should be updated
     * @param user The new user information to write
     * @param pinProtocol The PIN/UV protocol version in use
     * @param pinUVToken A PIN/UV token obtained from the Authenticator
     */
    suspend fun updateUserInformation(
        credentialID: PublicKeyCredentialDescriptor,
        user: PublicKeyCredentialUserEntity,
        pinProtocol: UByte? = null,
        pinUVToken: PinUVToken? = null,
    ) {
        val pp = client.getPinProtocol(pinProtocol)
        val effectiveUVToken = pinUVToken ?: client.getPinUvTokenUsingAppropriateMethod(
            desiredPermissions = CTAPPinPermission.CREDENTIAL_MANAGEMENT.value,
        )

        val fullySupported = client.getInfoIfUnset().options?.get(CTAPOption.CREDENTIALS_MANAGEMENT.value) == true

        val command = CredentialManagementCommand.updateUserInformation(
            pinUvAuthProtocol = pp.getVersion(),
            credentialId = credentialID,
            user = user,
            ctap21Implementation = fullySupported,
        )
        command.pinUvAuthParam = pp.authenticate(effectiveUVToken, command.getUvParamData())
        command.params = command.generateParams()

        client.xmit(command)
    }
}

/**
 * A combination of Relying Party details and the handle with which Credentials can be enumerated.
 *
 * @property rpIDHash The value to pass to [CredentialManagementClient.enumerateCredentials] to list this
 * Relying Party's Credentials
 * @property rp Information about this Relying Party
 */
data class RPWithHash(val rpIDHash: ByteArray, val rp: PublicKeyCredentialRpEntity) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as RPWithHash

        if (!rpIDHash.contentEquals(other.rpIDHash)) return false
        if (rp != other.rp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = rpIDHash.contentHashCode()
        result = 31 * result + rp.hashCode()
        return result
    }
}

/**
 * Information about a stored Discoverable Credential.
 *
 * @property user The user associated with the Discoverable Credential
 * @property credentialID The Credential itself
 * @property publicKey The public key associated with the Credential - necessary for validating Assertions
 * @property credProtect The [protection level][us.q3q.fidok.ctap.commands.CredProtectExtension] of the Credential,
 * if supported and set
 * @property largeBlobKey The [large blob key][us.q3q.fidok.ctap.commands.LargeBlobKeyExtension] for this Credential,
 * if supported
 */
data class StoredCredentialData(
    val user: PublicKeyCredentialUserEntity,
    val credentialID: PublicKeyCredentialDescriptor,
    val publicKey: COSEKey,
    val credProtect: UByte? = null,
    val largeBlobKey: ByteArray? = null,
) {
    constructor(res: EnumerateCredentialsResponse) : this(
        user = res.user,
        credentialID = res.credentialID,
        publicKey = res.publicKey,
        credProtect = res.credProtect,
        largeBlobKey = res.largeBlobKey,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as StoredCredentialData

        if (user != other.user) return false
        if (credentialID != other.credentialID) return false
        if (publicKey != other.publicKey) return false
        if (credProtect != other.credProtect) return false
        if (largeBlobKey != null) {
            if (other.largeBlobKey == null) return false
            if (!largeBlobKey.contentEquals(other.largeBlobKey)) return false
        } else if (other.largeBlobKey != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = user.hashCode()
        result = 31 * result + credentialID.hashCode()
        result = 31 * result + publicKey.hashCode()
        result = 31 * result + (credProtect?.hashCode() ?: 0)
        result = 31 * result + (largeBlobKey?.contentHashCode() ?: 0)
        return result
    }
}
