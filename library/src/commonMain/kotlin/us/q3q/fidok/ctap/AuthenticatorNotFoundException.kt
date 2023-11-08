package us.q3q.fidok.ctap

/**
 * Thrown when an Authenticator cannot be found at all, or where no found Authenticator is suitable.
 *
 * @param msg More detailed error message describing the situation
 */
class AuthenticatorNotFoundException(msg: String = "No usable Authenticator found") : IllegalStateException(msg)
