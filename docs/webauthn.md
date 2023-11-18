Webauthn Layer
==============

[CTAP](CTAP.md) is the raw protocol spoken between the FIDOk library and an Authenticator device. But CTAP is a
low-level command/response protocol. It operates on one Authenticator device at a time.

The Webauthn layer is more abstract. With Webauthn, instead of choosing a single Authenticator with which to
communicate, you provide logical operations and constraints, and the FIDOk platform chooses how to execute your
requests.

At this level, the library will:

- Select an appropriate Authenticator to service an operation
- Translate high-level Webauthn requests into low-level CTAP commands
- Process and unwrap CTAP extensions' results for easy use

Getting a Webauthn Client
=========================
To access Webauthn functionality, first [initialize the library](initialization.md). Then call the `webauthn()` method
on the library instance and you're off to the races.

Webauthn provides two APIs: `create`, for making a new Credential, and `get`, for getting an assertion using an
existing Credential.

The FIDOk implementations of these two APIs complies with the Webauthn-3 specification just like in a browser.
They accept and return objects whose names and contents match the standard.

Extensions
==========
Extensions are configured by passing in their parameters as a Kotlin Map. Unimplemented extensions will be ignored.
