package us.q3q.fidok.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.validate
import kotlinx.coroutines.runBlocking
import us.q3q.fidok.ez.EZHmac
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class, ExperimentalStdlibApi::class)
class Encrypt : CliktCommand(help = "Symmetrically encrypt data using the hmac-secret extension") {
    private val ezhmac by requireObject<EZHmac>()

    val setup by option("--setup")
        .required()
        .help("Exact output of an EZHMAC setup")
        .validate {
            if (it.isEmpty()) {
                fail("Must not be empty")
            }
        }

    val data by option("--data")
        .required()
        .help("Base64-encoded data to encrypt")
        .validate {
            if (it.isEmpty()) {
                fail("Must not be empty")
            }
        }

    val salt by option("--salt")
        .help("Hex-encoded salt to use in encryption")
        .validate {
            checkHex(it)
            if (it.length != 64) {
                fail("Salt must be exactly 32 bytes (64 hexadecimal characters) long")
            }
        }

    override fun run() {
        runBlocking {
            val gottenSalt = salt
            val ret =
                if (gottenSalt == null) {
                    ezhmac.encrypt(Base64.UrlSafe.decode(setup), Base64.decode(data))
                } else {
                    ezhmac.encrypt(Base64.UrlSafe.decode(setup), Base64.decode(data), gottenSalt.hexToByteArray())
                }
            echo(Base64.encode(ret))
        }
    }
}
