Starting the Library
====================

In order to do anything with FIDOk, you need to initialize the library.

One you have it [installed](install.md), create an instance of `FIDOkLibrary`:

```kotlin
import us.q3q.fidok.ctap.FIDOkLibrary

val library = FIDOkLibrary.init(
    cryptoProvider = PureJVMCryptoProvider(),
    authenticatorAccessors = listOf(
        BlessedBluezDeviceListing
    )
)
```

In order to do this you need two things:

- A `CryptoProvider` instance, to provide the cryptographic operations the FIDO standard requires
- If you want to be able to discover any Authenticators via the library, you also need at least 
  one `AuthenticatorListing` instance

Both of these things depend on the platform you're on. If you don't care about having any support for things
like PIN protocols, you can always use the `NullCryptoProvider`, but this will render the library half-functional.

Receiving Callbacks
-------------------
Users can connect and disconnect Authenticators, and whether a PIN is necessary or not can depend on the
circumstance. To handle these events, create an instance of `FIDOkCallbacks` and pass it to the library
initializer.

By overriding the `collectPin` method, you can request a PIN from the user and return it to the library for
use in CTAP protocols.

Included Crypto Providers
=========================

| Runtime Platform | Available Crypto Provider(s)                                                            |
|------------------|-----------------------------------------------------------------------------------------|
| JVM              | PureJVMCryptoProvider, NativeBackedCryptoProvider (which then uses BotanCryptoProvider) |
| Linux            | BotanCryptoProvider                                                                     |
| Windows          | BotanCryptoProvider                                                                     |
| Mac OS           | BotanCryptoProvider                                                                     |
| Android (JVM)    | PureJVMCryptoProvider                                                                   |
| iOS              | None                                                                                    |

Included Authenticator Accessors
================================

| Runtime Platform | Available Crypto Provider(s)           |
|------------------|----------------------------------------|
| JVM              | BlessedBluezDeviceListing (Linux only) |
| Linux            | LibHIDDevice, LibPCSCLiteDevice        |
| Windows          | LibHIDDevice, PCSCDevice               |
| Mac OS           | LibHIDDevice, MacPCSCLiteDevice        |
| Android (JVM)    | AndroidUSBHIDListing                   |
| iOS              | None                                   |
