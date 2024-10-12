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

/**
 * Primary object for interacting with FIDOk.
 */
class FIDOkLibrary private constructor(
    val cryptoProvider: CryptoProvider,
    private var authenticatorAccessors: List<AuthenticatorListing>,
    private val callbacks: FIDOkCallbacks? = null,
) {
    companion object {
        /**
         * Create a library instance.
         *
         * @param cryptoProvider Support for necessary Cryptographic functionality
         * @param authenticatorAccessors Ways to get [Authenticator][AuthenticatorDevice] instances
         * @param callbacks Hooks to handle different library events
         * @return Ready-for-use [FIDOkLibrary] instance
         */
        @JvmStatic
        @JvmOverloads
        fun init(
            cryptoProvider: CryptoProvider,
            authenticatorAccessors: List<AuthenticatorListing> = listOf(),
            callbacks: FIDOkCallbacks? = null,
        ): FIDOkLibrary {
            Logger.setTag("FIDOk")
            // Logger.setMinSeverity(Severity.Info)
            Logger.d { "Initializing FIDOk library using ${authenticatorAccessors.size} types of Authenticator accessor" }
            return FIDOkLibrary(
                cryptoProvider,
                authenticatorAccessors,
                callbacks = callbacks,
            )
        }
    }

    /**
     * Get [devices][AuthenticatorDevice] available for use.
     *
     * @param allowedTransports If provided, only return Authenticators that support one of the given transports
     * @return A list of available Authenticators
     */
    fun listDevices(allowedTransports: List<AuthenticatorTransport>? = null): Array<AuthenticatorDevice> {
        val devices = arrayListOf<AuthenticatorDevice>()
        for (accessor in authenticatorAccessors) {
            val provided = accessor.providedTransports()
            if (provided.isNotEmpty() && allowedTransports != null &&
                provided.intersect(allowedTransports).isEmpty()
            ) {
                continue
            }

            devices.addAll(
                accessor.listDevices(this).filter {
                    if (allowedTransports == null) {
                        true
                    } else {
                        it.getTransports().intersect(allowedTransports).isNotEmpty()
                    }
                },
            )
        }
        return devices.toTypedArray()
    }

    /**
     * Wait until an [Authenticator][AuthenticatorDevice] is available which matches the given criteria.
     *
     * @param preFilter Only Authenticators for which the given function returns `true` will be used
     * @param activeSelection This function will be called only on Authenticators which pass the [preFilter]. If it
     * returns `true`, the Authenticator may be returned. This function will not be called if only a single
     * Authenticator is available passing the [preFilter]
     * @param timeout How long to wait for an Authenticator that matches the filters
     */
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
                        val jobs =
                            potentialCTAPClients.map {
                                launch {
                                    callbacks?.authenticatorWaitingForSelection(it)
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

    /**
     * Remove all sources of Authenticators that provide the given transport.
     *
     * @param transport Transport to exclude
     */
    fun disableTransport(transport: AuthenticatorTransport) {
        authenticatorAccessors =
            authenticatorAccessors.filter {
                !it.providedTransports().contains(transport)
            }
    }

    /**
     * Change the library's sources of Authenticators.
     *
     * @param accessors New ways to get [AuthenticatorDevice] instances
     */
    fun setAuthenticatorAccessors(accessors: List<AuthenticatorListing>) {
        authenticatorAccessors = accessors
    }

    /**
     * Get a client for Cloud Assisted Bluetooth LE (CaBLE) functionality.
     *
     * @return Client for interacting with CaBLE features
     */
    fun caBLESupport(): CaBLESupport {
        return CaBLESupport(this)
    }

    /**
     * Get a client for communicating with Authenticators via the lower-level CTAP protocol.
     *
     * @param device Authenticator object for which to produce a client
     * @param collectPin Function for supplying a PIN to the device when necessary. If not provided, will use
     * the library's [callbacks]
     * @return Object for CTAP interaction with the given [device]
     */
    fun ctapClient(
        device: AuthenticatorDevice,
        collectPin: (suspend (client: CTAPClient?) -> String?)? = null,
    ): CTAPClient {
        val callback =
            collectPin ?: (
                if (callbacks != null) {
                    callbacks::collectPin
                } else {
                    { null }
                }
            )
        return CTAPClient(this, device, callback)
    }

    /**
     * Get a client for communicating with Authenticators via the higher-level Webauthn protocol.
     *
     * WebauthN operations select their own appropriate Authenticator to handle each request.
     *
     * @return Object for interacting with this library over WebauthN
     */
    fun webauthn(): WebauthnClient {
        return WebauthnClient(this)
    }
}
