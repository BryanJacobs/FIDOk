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

class CredentialManagementClient internal constructor(private val client: CTAPClient) {
    fun getCredsMetadata(pinProtocol: UByte? = null, pinUVToken: PinUVToken): CredentialManagementGetMetadataResponse {
        val pp = client.getPinProtocol(pinProtocol)

        val pinUvAuthParam = pp.authenticate(pinUVToken, byteArrayOf(0x01))
        val fullySupported = client.getInfoIfUnset().options?.get(CTAPOptions.CREDENTIALS_MANAGEMENT.value) == true

        val command = CredentialManagementCommand.getCredsMetadata(
            pinUvAuthProtocol = pp.getVersion(),
            pinUvAuthParam = pinUvAuthParam,
            ctap21Implementation = fullySupported,
        )

        return client.xmit(command, CredentialManagementGetMetadataResponse.serializer())
    }

    fun enumerateRPs(pinProtocol: UByte? = null, pinUVToken: PinUVToken): List<RPWithHash> {
        val pp = client.getPinProtocol(pinProtocol)

        val pinUvAuthParam = pp.authenticate(pinUVToken, byteArrayOf(0x02))
        val fullySupported = client.getInfoIfUnset().options?.get(CTAPOptions.CREDENTIALS_MANAGEMENT.value) == true

        val command = CredentialManagementCommand.enumerateRPsBegin(
            pinUvAuthProtocol = pp.getVersion(),
            pinUvAuthParam = pinUvAuthParam,
            ctap21Implementation = fullySupported,
        )

        val initialRes = client.xmit(command, EnumerateRPsResponse.serializer())

        val totalRPs = initialRes.totalRPs?.toInt() ?: 1

        val allRPs = arrayListOf(RPWithHash(initialRes.rpIDHash, initialRes.rp))

        val enumerateCommand = CredentialManagementCommand.enumerateRPsGetNextRP(ctap21Implementation = fullySupported)
        for (i in 1..<totalRPs) {
            val res = client.xmit(enumerateCommand, EnumerateRPsResponse.serializer())
            if (res.totalRPs != null) {
                throw IllegalStateException("Present totalRPs on enumerate-RPs follow-up")
            }
            allRPs.add(RPWithHash(res.rpIDHash, res.rp))
        }

        return allRPs
    }

    fun enumerateCredentials(rpIDHash: ByteArray, pinProtocol: UByte? = null, pinUVToken: PinUVToken): List<StoredCredentialData> {
        val pp = client.getPinProtocol(pinProtocol)
        val fullySupported = client.getInfoIfUnset().options?.get(CTAPOptions.CREDENTIALS_MANAGEMENT.value) == true

        val command = CredentialManagementCommand.enumerateCredentialsBegin(
            pinUvAuthProtocol = pp.getVersion(),
            rpIDHash = rpIDHash,
            ctap21Implementation = fullySupported,
        )

        command.pinUvAuthParam = pp.authenticate(pinUVToken, command.getUvParamData())
        command.params = command.generateParams()

        val initialRes = client.xmit(command, EnumerateCredentialsResponse.serializer())

        val totalCredentials = initialRes.totalCredentials?.toInt() ?: 1

        Logger.i { "Authenticator has $totalCredentials credential(s) when enumerating" }

        val allRPs = arrayListOf(StoredCredentialData(initialRes))

        val enumerateCommand = CredentialManagementCommand.enumerateCredentialsGetNextCredential(ctap21Implementation = fullySupported)
        for (i in 1..<totalCredentials) {
            val res = client.xmit(enumerateCommand, EnumerateCredentialsResponse.serializer())
            if (res.totalCredentials != null) {
                throw IllegalStateException("Present totalCredentials on enumerate-credentials follow-up")
            }
            allRPs.add(StoredCredentialData(res))
        }

        return allRPs
    }

    fun deleteCredential(credentialID: PublicKeyCredentialDescriptor, pinProtocol: UByte? = null, pinUVToken: PinUVToken) {
        val pp = client.getPinProtocol(pinProtocol)

        val fullySupported = client.getInfoIfUnset().options?.get(CTAPOptions.CREDENTIALS_MANAGEMENT.value) == true

        val command = CredentialManagementCommand.deleteCredential(
            pinUvAuthProtocol = pp.getVersion(),
            credentialId = credentialID,
            ctap21Implementation = fullySupported,
        )
        command.pinUvAuthParam = pp.authenticate(pinUVToken, command.getUvParamData())
        command.params = command.generateParams()

        client.xmit(command)
    }

    fun updateUserInformation(
        credentialID: PublicKeyCredentialDescriptor,
        user: PublicKeyCredentialUserEntity,
        pinProtocol: UByte? = null,
        pinUVToken: PinUVToken,
    ) {
        val pp = client.getPinProtocol(pinProtocol)

        val fullySupported = client.getInfoIfUnset().options?.get(CTAPOptions.CREDENTIALS_MANAGEMENT.value) == true

        val command = CredentialManagementCommand.updateUserInformation(
            pinUvAuthProtocol = pp.getVersion(),
            credentialId = credentialID,
            user = user,
            ctap21Implementation = fullySupported,
        )
        command.pinUvAuthParam = pp.authenticate(pinUVToken, command.getUvParamData())
        command.params = command.generateParams()

        client.xmit(command)
    }
}

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

data class StoredCredentialData(
    val user: PublicKeyCredentialUserEntity,
    val credentialID: PublicKeyCredentialDescriptor,
    val publicKey: COSEKey,
    val credProtect: UByte?,
    val largeBlobKey: ByteArray?,
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
