package us.q3q.fidok.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import us.q3q.fidok.ctap.FIDOkLibrary
import us.q3q.fidok.ez.EZHmac

class HMAC : CliktCommand("Easily encrypt/decrypt data using FIDO Authenticators via hmac-secret") {

    init {
        subcommands(Setup(), Encrypt(), Decrypt())
    }

    private val rpId by option("--rp")
        .help("Relying Party ID to use")

    val library by requireObject<FIDOkLibrary>()

    override fun run() {
        val gottenRpId = rpId
        val hmac = if (gottenRpId != null) EZHmac(library, gottenRpId) else EZHmac(library)
        currentContext.obj = hmac
    }
}
