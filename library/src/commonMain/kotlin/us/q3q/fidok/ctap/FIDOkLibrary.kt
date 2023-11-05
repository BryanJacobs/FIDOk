package us.q3q.fidok.ctap

import co.touchlab.kermit.Logger
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import us.q3q.fidok.FIDOkCallbacks
import us.q3q.fidok.cable.CaBLESupport
import us.q3q.fidok.crypto.CryptoProvider
import us.q3q.fidok.webauthn.WebauthnClient
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class FIDOkLibrary private constructor(
    val cryptoProvider: CryptoProvider,
    private var authenticatorAccessors: List<AuthenticatorListing>,
    private val callbacks: FIDOkCallbacks? = null,
) {

    companion object {
        @JvmStatic
        @JvmOverloads
        fun init(
            cryptoProvider: CryptoProvider,
            authenticatorAccessors: List<AuthenticatorListing> = listOf(),
            callbacks: FIDOkCallbacks? = null,
        ): FIDOkLibrary {
            Logger.d { "Initializing FIDOk library using ${authenticatorAccessors.size} types of Authenticator accessor" }
            return FIDOkLibrary(
                cryptoProvider,
                authenticatorAccessors,
                callbacks = callbacks,
            )
        }
    }

    fun listDevices(allowedTransports: List<AuthenticatorTransport>? = null): Array<AuthenticatorDevice> {
        val devices = arrayListOf<AuthenticatorDevice>()
        for (accessor in authenticatorAccessors) {
            devices.addAll(
                accessor.listDevices().filter {
                    if (allowedTransports == null) {
                        true
                    } else {
                        var match = false
                        for (transport in it.getTransports()) {
                            if (allowedTransports.contains(transport)) {
                                match = true
                                break
                            }
                        }
                        match
                    }
                },
            )
        }
        return devices.toTypedArray()
    }

    @Throws(AuthenticatorNotFoundException::class, CancellationException::class)
    suspend fun waitForUsableAuthenticator(
        preFilter: suspend (client: CTAPClient) -> Boolean = { true },
        activeSelection: suspend (client: CTAPClient) -> Boolean = { true },
        timeout: Duration = 10.seconds,
    ): CTAPClient {
        val startTime = TimeSource.Monotonic.markNow()

        val potentialCTAPClients: ArrayList<CTAPClient> = arrayListOf()
        var authenticatorNeededCallbackTriggered = false

        while (startTime.elapsedNow() < timeout) {
            val devices = this.listDevices()

            Logger.d { "${devices.size} device(s) available" }

            for (device in devices) {
                val client = this.ctapClient(device)

                if (!preFilter(client)) {
                    callbacks?.authenticatorUnsuitable(client)
                    continue
                }

                if (callbacks?.authenticatorFound(client) != false) {
                    potentialCTAPClients.add(client)
                }
            }

            if (!potentialCTAPClients.isEmpty()) {
                break
            }

            if (!authenticatorNeededCallbackTriggered) {
                callbacks?.authenticatorNeeded()
                authenticatorNeededCallbackTriggered = true
            }
        }

        if (potentialCTAPClients.isEmpty()) {
            throw AuthenticatorNotFoundException()
        }

        var selectedClient: CTAPClient = potentialCTAPClients[0]

        if (potentialCTAPClients.size > 1) {
            // TODO: prefer authenticators that better match the requested parameters
            coroutineScope {
                var ok = false

                var remainingTime = timeout - startTime.elapsedNow()
                if (remainingTime < 2.seconds) {
                    remainingTime = 2.seconds // call it a grace period
                }

                try {
                    withTimeout(remainingTime) {
                        val receiver = Channel<CTAPClient?>(capacity = potentialCTAPClients.size)
                        val jobs = potentialCTAPClients.map {
                            launch {
                                callbacks?.auhtenticatorWaitingForSelection(it)
                                val working = activeSelection(it)

                                if (working) {
                                    receiver.send(it)
                                } else {
                                    receiver.send(null)
                                }
                            }
                        }

                        for (i in 1..potentialCTAPClients.size) {
                            val clientOrNull = receiver.receive()
                            if (clientOrNull != null) {
                                // cancel anything left
                                for (job in jobs) {
                                    if (job.isActive) {
                                        job.cancel()
                                    }
                                }
                                selectedClient = clientOrNull
                                ok = true
                                break
                            }
                        }
                    }
                } catch (_: TimeoutCancellationException) {
                    Logger.w { "Timed out waiting for authenticator(s) to answer selection call" }
                }

                if (!ok) {
                    throw AuthenticatorNotFoundException("No CTAP client answered selection call")
                }
            }
        }

        return selectedClient
    }

    fun disableTransport(transport: AuthenticatorTransport) {
        authenticatorAccessors = authenticatorAccessors.filter {
            !it.providedTransports().contains(transport)
        }
    }

    fun caBLESupport(): CaBLESupport {
        return CaBLESupport(this)
    }

    fun ctapClient(device: AuthenticatorDevice, collectPinFromUser: (suspend (client: CTAPClient?) -> String?)? = null): CTAPClient {
        val callback = collectPinFromUser ?: (if (callbacks != null) callbacks::collectPin else { { null } })
        return CTAPClient(this, device, callback)
    }

    fun webauthn(): WebauthnClient {
        return WebauthnClient(this)
    }
}
