FIDO-k
======

This repository contains an early-stage implementation of a FIDO Platform, implementing the
WebAuthN and CTAP2.1 standards ("FIDO2").

A FIDO2 Platform sits between an Authenticator and a Relying Party; it runs on a client
device (such as a laptop or smartphone) and allows the user to communicate with
authenticators.

This software can be linked into an application to add FIDO2 authentication capabilities
to that application. But it's not an implementation of a Relying Party; this is the
software for a client computer potentially talking TO a web service, not the software
running on the web server side.

This particular Platform implementation has a few special properties:

- It exposes a large set of authenticator management functionality, such as the ability
  to set minimum PIN lengths, view and manage stored credentials, etc
- It is a Kotlin Multiplatform project, and is capable of running as either a native
  application (Windows/Mac/Linux/Android/iOS) or on a Java Virtual Machine
- It supports a wide range of different authenticator types, not just USB-HID

Note this means that, despite being a Kotlin project, C code can use this
implementation as a replacement for e.g. `libfido2`!

**Implementation Overall Status: Very Early / Alpha**

This implementation will likely improve in coverage rapidly! It doesn't yet have stable
APIs and isn't nearly feature complete.

# Overview

The core code is all Kotlin/Mutliplatform, and can be used from any target. Interfaces
for cryptography and communicating with authenticators on a byte-by-byte level is
provided by native implementations specific to each platform.

## Protocol Features

| Feature                                   | Status                   |
|-------------------------------------------|--------------------------|
| CTAP2.0 Prototype Credentials Management  | Supported                |
| CTAP2.1 Credentials Management            | Supported                |
| Authenticator Config                      | Supported                |
| Authenticator Reset                       | Supported                |
| Client PIN (set, change, info)            | Supported                |
| PIN Protocol One                          | Supported                |
| PIN Protocol Two                          | Supported                |
| PIN tokens using PIN without permissions  | Supported                |
| PIN tokens using PIN with permissions     | Supported                |
| PIN tokens using onboard UV               | Unsupported              |
| setMinPINLength Extension                 | Supported for management |
| credProtect Extension                     | Supported for management |
| Create-time Extensions (hmac-secret, etc) | Unsupported              |
| LargeBlob management                      | Unsupported              |
| Bio Enrollment                            | Unsupported              |
| Authenticator Selection                   | Incomplete               |
| makeCredential                            | Incomplete               |
| getAssertion                              | Unsupported              |

## Implementation Features

| Level                   | Status        |
|-------------------------|---------------|
| Raw CTAP                | Incomplete    |
| Well-documented APIs    | Unimplemented |
| Webauthn Layer          | Unimplemented |
| Easy Task-Specific APIs | Unimplemented |
| Command Line            | Unimplemented |
| User Interface          | Unimplemented |

## Platforms

| Platform | Status        |
|----------|---------------|
| JVM      | Incomplete    |
| Linux    | Working       |
| MacOS    | Unimplemented |
| Windows  | Unimplemented |
| iOS      | Unimplemented |
| Android  | Unimplemented |

## Authenticator Types

| Attachment / Protocol | Status        |
|-----------------------|---------------|
| USB-HID               | Working       |
| BT-HID                | Working       |
| PC/SC (USB or NFC)    | Working       |
| Bluetooth LE          | Unimplemented |
| E-APDUs               | Working       |
| APDU Chaining         | Working       |
