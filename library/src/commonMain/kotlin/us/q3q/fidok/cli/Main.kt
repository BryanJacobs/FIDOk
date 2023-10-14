import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import us.q3q.fidok.cli.Create
import us.q3q.fidok.cli.Get
import us.q3q.fidok.ctap.AuthenticatorListing
import us.q3q.fidok.ctap.FIDOkLibrary

class Main(
    private val providerMap: Map<String, AuthenticatorListing>,
    private val libraryBuilder: (authenticatorAccessors: List<AuthenticatorListing>) -> FIDOkLibrary,
) : CliktCommand() {
    private val providers: List<String> by option("--provider")
        .choice(*providerMap.keys.toTypedArray())
        .multiple(default = providerMap.keys.toList())
        .help("Provider of Authenticator devices")

    override fun run() {
        val providerClasses = providers.mapNotNull { providerMap[it] }.toList()

        currentContext.obj = libraryBuilder(providerClasses)
    }

    fun execute(args: Array<String>) {
        subcommands(Create(), Get()).main(args)
    }
}
