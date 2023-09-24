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
| Mac OS           | LibHIDDevice, LibPCSCLiteDevice        |
| Android (JVM)    | AndroidUSBHIDListing                   |
| iOS              | None                                   |
