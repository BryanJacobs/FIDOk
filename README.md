FIDO-k
======

This repository contains an early-stage implementation of a FIDO Platform, implementing the
WebAuthn-2 and CTAP2.1 standards ("FIDO2"). It also includes some CTAP2.2 and 
WebAuthn-3 functionality.

A FIDO2 Platform sits between an Authenticator and a Relying Party; it runs on a client
device (such as a laptop or smartphone) and allows the user to communicate with
Authenticators.

This software can be linked into an application to add FIDO2 authentication capabilities
to that application. But it's not an implementation of a Relying Party; this is the
software for a client computer potentially talking TO a web service, not the software
running on the web server side.

This particular Platform implementation has a few special properties:

- It exposes a large set of authenticator management functionality, such as the ability
  to set minimum PIN lengths, view and manage stored credentials, etc
- It is a Kotlin Multiplatform project, and is capable of running as either a native
  application (Windows/Mac/Linux/Android/iOS) or on a Java Virtual Machine
- It supports a wide range of different Authenticator types, not just USB-HID

Note this means that, despite being a Kotlin project, C code can use this
implementation as a replacement for e.g. `libfido2`!

**Implementation Overall Status: Beta**

This implementation will likely improve in coverage rapidly! It mostly works, but APIs
are not guaranteed to remain stable yet, and there are surely bugs or incomplete sections.

# Overview

The core code is all Kotlin/Multiplatform, and can be used from any target. Interfaces
for cryptography and communicating with authenticators on a byte-by-byte level are
provided by native implementations specific to each platform.

### Layers

1. Low level [CTAP layer](docs/CTAP.md)
1. [Webauthn standard implementation](docs/webauthn.md)
1. libfido2 [compatibility layer](docs/dropin.md)
1. High level [task-specific APIs](docs/tasks.md)

## Protocol Features

| Feature                                  | Status       |
|------------------------------------------|--------------|
| CTAP2.0 Prototype Credentials Management | Supported    |
| CTAP2.1 Credentials Management           | Supported    |
| Authenticator Config                     | Supported    |
| Authenticator Reset                      | Supported    |
| Client PIN (set, change, info)           | Supported    |
| `setMinPINLength` Command                | Supported    |
| PIN Protocol One                         | Supported    |
| PIN Protocol Two                         | Supported    |
| PIN tokens using PIN without permissions | Supported    |
| PIN tokens using PIN with permissions    | Supported    |
| PIN tokens using onboard UV              | Supported    |
| `minPinLength` Extension                 | Supported    |
| `credProtect` Extension                  | Supported    |
| `hmac-secret` Extension                  | Supported    |
| `credBlob` Extension                     | Supported    |
| `largeBlobKey` Extension                 | Supported    |
| `uvm` Extension (Webauthn)               | Supported    |
| Key Algorithm Types                      | ES256, EdDSA |
| LargeBlob management                     | Unsupported  |
| Bio Enrollment                           | Unsupported  |
| Authenticator Selection                  | Incomplete   |
| MakeCredential                           | Supported    |
| GetAssertion                             | Supported    |
| Self Attestation                         | Supported    |
| Basic Attestation                        | Incomplete   |
| Enterprise Attestation                   | Incomplete   |
| FIDO Metadata Data Service               | Unsupported  |
| Android-key Attestation                  | Unsupported  |
| TPM Attestation                          | Unsupported  |
| CTAP1/U2F                                | Unsupported  |

## Implementation Features

| Level                             | Status           |
|-----------------------------------|------------------|
| Raw CTAP                          | Working          |
| Testing                           | Incomplete       |
| Well-documented APIs              | Incomplete       |
| Well-documented Build/Integration | No               |
| Webauthn Layer                    | Working          |
| Easy Task-Specific APIs           | Incomplete       |
| Command Line                      | Working          |
| Graphical User Interface          | Incomplete       |
| Authenticator Proxying            | Working from HID |

## Platforms

| Platform                         | Status                                               |
|----------------------------------|------------------------------------------------------|
| JVM "fat" JAR                    | Working                                              |
| C/C++ Shared Library             | Working                                              |
| Linux Executable/SO              | Working (.so, elf native binary, AppImage, RPM, DEB) |
| MacOS X86 Executable/framework   | Working (can only be built on Mac OS X86 or Rosetta) |
| MacOS Arm64 Executable/framework | Working (can only be built on Mac OS Arm64)          |
| Windows Executable/DLL           | Working (.dll, .exe native binary)                   |
| iOS Framework                    | Unimplemented                                        |
| Android JAR/SO                   | Incomplete                                           |
| Android Demo Application         | Working (.apk)                                       |
| Web Page :)                      | Unimplemented                                        |

## Authenticator Types / Protocols

| Attachment    | What is this?                       | Status        | Linux          | Mac | Windows        | JVM       | Android    | iOS |
|---------------|-------------------------------------|---------------|----------------|-----|----------------|-----------|------------|-----|
| USB-HID       | Plug-in USB tokens                  | Working       | Y              |   Y | N              | As Native | Y          |     |
| USB-CCID      | Smart Card Readers (via USB)        | Working       | Y (via PC/SC)  |   N | Y (via PC/SC)  | As Native | N          |     |
| NFC           | Near-field tokens (via an antenna)  | Working       | N (Y via CCID) |   N | N (Y via CCID) | As Native | Y          |     |
| Bluetooth-HID | Very strange, not found in the wild | Working       | Y              |   N | N              | As Native | N (and *1) |     |
| Bluetooth LE  | Wireless, battery powered tokens    | Working       | Y              |   N | N              | Y         | *1         |     |
| BLE PC/SC     | Smart Card Readers (via BLE)        | Partial *2    | Y              |   N | N              | As Native | N          |     |
| TPM           | Chips built into computers          | Unimplemented |                |     |                |           |            |     |
| CaBLE         | Authenticators using the Internet!  | Incomplete    |                |     |                |           |            |     |

*1 - Bluetooth LE support for Android is implemented, but for reasons unknown Android restricts the ability to access BLE FIDO
     tokens to "system" applications, so FIDOk can't be used with BLE authenticators over BLE in a normal install

*2 - The only currently-supported reader is the ACR1255U-J1, from Advanced Card Systems, Ltd (ACS). This reader uses a proprietary-but-documented
     protocol, and FIDOk contains a working implementation of that protocol.
