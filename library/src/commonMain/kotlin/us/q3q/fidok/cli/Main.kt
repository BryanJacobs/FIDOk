package us.q3q.fidok.cli

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import us.q3q.fidok.ctap.AuthenticatorListing
import us.q3q.fidok.ctap.FIDOkLibrary

class Main(
    private val providerMap: Map<String, AuthenticatorListing>,
    private val libraryBuilder: (authenticatorAccessors: List<AuthenticatorListing>) -> FIDOkLibrary,
) : CliktCommand(name = "fidok") {

    init {
        subcommands(Info(), Create(), Get(), Pin(), Reset())
    }

    private val logLevel by option("--log-level")
        .choice(*Severity.entries.map { it.name.lowercase() }.toTypedArray())
        .default(Severity.Error.name.lowercase())
        .help("Minimum log level to display")

    private val providers: List<String> by option("--provider")
        .help("Provider of Authenticator devices")
        .choice(*providerMap.keys.toTypedArray())
        .multiple(default = providerMap.keys.toList())

    override fun run() {
        Logger.setMinSeverity(Severity.valueOf(logLevel.replaceFirstChar { it.uppercase() }))

        val providerClasses = providers.mapNotNull { providerMap[it] }.distinct().toList()

        currentContext.obj = libraryBuilder(providerClasses)
    }
}
