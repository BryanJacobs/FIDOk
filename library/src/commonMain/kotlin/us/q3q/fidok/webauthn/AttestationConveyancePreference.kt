package us.q3q.fidok.webauthn

enum class AttestationConveyancePreference(val value: String) {
    NONE("none"),
    INDIRECT("indirect"),
    DIRECT("direct"),
    ENTERPRISE("enterprise"),
}
