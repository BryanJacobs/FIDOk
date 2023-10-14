package us.q3q.fidok.webauthn

enum class UserVerificationRequirement(val value: String) {
    REQUIRED("required"),
    PREFERRED("preferred"),
    DISCOURAGED("discouraged"),
}
