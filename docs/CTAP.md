CTAP Layer
==========

This is the lowest level *usable* interface FIDOk exposes. At this level, you are directly sending commands
to individual Authenticators, and receiving their unfiltered responses. Anything below this and you're extending
the Platform instead of using it, really.

The library still provides some benefits even at this low level:

- Implementing the hardware interface for sending bytes to a device and receiving them back
- Correctly serializing CTAP commands and responses
- Noticing errors (either in the connection, or in the Authenticator response)
- Providing utility functions for things like PIN/UV token management

This level might be right for you if you're implementing your own Platform using this one as a base, or
if you're trying to do something beyond the scope of what Webauthn supports.

For most use cases, you'd be better off using something more abstracted.

CTAP Client
===========

The first step is to [initialize the library](initialization.md), obtaining an instance of `FIDOkLibrary`.

From that you can access a list of devices (per the `AuthenticatorAccessors`s you gave it):

```kotlin
val devices = library.listDevices(listOf(AuthenticatorTransport.USB))
```

And for a given device, get a CTAP client for communicating with that device:

```kotlin
val ctap = library.ctapClient(device)
```

Now you can send commands and get responses!

Commands and Responses
======================

The `us.q3q.fidok.ctap.commands` package contains many classes for constructing different CTAP
commands and responses, but the easiest way to use them is through the methods on the `CTAPClient`.

For example, to make a new credential:

```kotlin
val credentialResponse = ctap.makeCredential(
    clientDataHash = library.cryptoProvider.sha256(myClientData).hash,
    rpId = "my.cool.rpid.example",
    userId = myThirtyTwoByteLongUserId,
    userDisplayName = "Bob Mcbobson",
    discoverableCredential = false
)
```

On success this will return a `MakeCredentialResponse` containing the created credential and associated
trappings.

On failure this will either throw a `CTAPError` or a `DeviceCommunicationException`. The `CTAPError`
indicates something wrong with the request or the state of the Authenticator - see its `code` or
`message` for more information. The `DeviceCommunicationException` indicates a problem with the
connection to the Authenticator.

Extensions
==========

Using extensions can be a bit tricky, but `FIDOk` makes it easier. Each CTAP extension has an
associated class. To use the extension, create the class, and give its instance to the
relevant `CTAPClient` method.

Let's use the `CredProtectExtension` as an example. Attempting to apply a `credProtect` level of `3`:

```kotlin
val credProtect = CredProtectExtension(3u)

ctap.makeCredential(
    rpId = "my.cool.rpid.example",
    userId = myThirtyTwoByteLongUserId,
    userDisplayName = "Bob Mcbobson",
    extensions = ExtensionSetup(
        listOf(credProtect)
    )
)

val assignedCredProtectLevel = credProtect.getLevel()
```

The value of `assignedCredProtect` level will be either `null`, indicating the cred protect extension
wasn't present in the response from the Authenticator, or the assigned value it sent back (hopefully
three!).
