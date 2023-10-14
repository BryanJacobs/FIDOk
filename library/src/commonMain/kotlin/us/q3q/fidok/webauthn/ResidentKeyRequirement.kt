package us.q3q.fidok.webauthn

enum class ResidentKeyRequirement(val value: String) {
    DISCOURAGED("discouraged"),
    PREFERRED("preferred"),
    REQUIRED("required"),
}
