package us.q3q.fidok.ctap

class AuthenticatorNotFoundException(msg: String = "No usable Authenticator found") : IllegalStateException(msg)
