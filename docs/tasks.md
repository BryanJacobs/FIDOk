Task-Specific Layer
===================

[CTAP](CTAP.md) and [Webauthn](webauthn.md) are complicated beasts. When you're just trying to accomplish a specific
task, it's nice not to have to worry about them.

This layer provides highly abstracted task-specific operations, for your convenience.

Encryption and Decryption
=========================
The "easy HMAC" layer provides for symmetric authenticated encryption and decryption of arbitrary data,
in such a way that:

1. Anything encrypted using a particular Authenticator can only be decrypted using that Authenticator
1. CTAP-resetting the Authenticator will prevent it from being used to decrypt any data encrypted prior to the reset
1. Anything encrypted using a particular "handle" (returned from the library) and optional "salt" (given TO the
   library) can only be decrypted when that same "handle" and "salt" are presented
1. Tampering with the encrypted data is detected to a high degree of certainty

Example use cases where this might be useful include unlocking an encrypted hard drive or opening a password-manager
file. This construct could be part of a higher-level encryption scheme, but doesn't need to be.

***If you lose the Authenticator used for encrypting a particular piece of encrypted data, there is no recovery***.
If you want to have multiple Authenticators, any one of which is sufficient, you'll need to encrypt the data
multiple times.

Use
---
There are three steps to use the EZHmac function:

1. Create an EZHmac instance
2. Call `setup` to bind an Authenticator
3. Use `encrypt` and `decrypt` functions

First, [initialize the FIDOk library](initialization.md). Then construct an `EZHmac` instance:

```kotlin
import us.q3q.fidok.ez.EZHmac

val ez = EZHmac(library)
```

If you wish, you may provide a "relying party IO" (from CTAP) to the EZHmac constructor. If you choose to do so,
you must make sure to always pass the same value, as keys set up for one relying party will not work for another.

After you have an instance, you must call `setup` to get a handle for using encryption/decryption methods. The
Authenticator presented when `setup` is called is the one to which all encryption/decryption operations will be tied.

`setup()` takes no arguments. Just call it. It returns an opaque byte array necessary to call `encrypt`/`decrypt`.

The final step is to encrypt some data. The whole flow could look like:

```kotlin
import us.q3q.fidok.ez.EZHmac

val ez = EZHmac(library)
val setup = ez.setup() // will prompt for authenticator and create new key handle

val my_data = "something wicked this way comes".encodeToByteArray()

val encrypted = ez.encrypt(setup, my_data) // will prompt for authenticator again if it's not connected
val decrypted_again = ez.decrypt(setup, encrypted) // will prompt for authenticator a third time if disconnected
```

As easy as that. After `setup` has been called once, you never need to call it again - so long as the Authenticator
is not reset, it will remain valid indefinitely.

Rotating Salts
--------------
The `encrypt` and `decrypt` methods take an optional "salt" parameter (32 bytes long). Two different salts will
yield two entirely different encryption keys; in other words, data encrypted using one salt cannot be decrypted
using a different one. This is *in addition to* the requirement to use the same handle obtained from calling `setup()`.

If your application changes salts, you might not want to have the user present their Authenticator twice.
`encryptAndRotate` is a convenience method that will perform a decryption and encryption operation with two
different salts in one call to the Authenticator, and return both results. It can be used for rotating salts:

```kotlin
val setup = ezHmac.setup()
val newSalt = Random.nextBytes(32)

val encrypted = ezHmac.encrypt(setup, toEncrypt)

val rotationResult = ezHmac.decryptAndRotate(setup,
   previouslyEncryptedData = encrypted,
   newSalt = newSalt
)

val decrypted = rotationResult.first
val encryptedAgainUsingNewSalt = rotationResult.second
```

After code like this, `encryptedAgainUsingNewSalt` will contain the original data encrypted differently from before,
but the Authenticator will only need to be used a single time.

Implementation Details
----------------------
The technical nitty-gritty of the implementation follows.

The `setup` command creates a new CTAP Credential using the Relying Party ID given to the `EZHMac` constructor
(or `fidok.nodomain` if none was given). The Credential has the `hmac-secret` CTAP2 extension enabled.

Before encrypting or decrypting data, the Credential is given to the Authenticator along with a 32-byte salt. The
Authenticator returns a 32-byte key in response.

The `encrypt` method pads the input data with random bytes to a multiple of 16 bytes (the AES data block size).
The last byte of the padding will be the number of padding bytes added. In the event the incoming data is already
a perfect multiple of 16 bytes long, a full 16-byte block is added.

A 16-byte random Initialization Vector (IV) is prepended to the data. This IV is then used with AES-256-CBC to
encrypt the data itself.

Finally, an HMAC-SHA256 is calculated over the IV plus the data plus the padding bytes, and appended to the data.
This whole result is returned.

This is an implementation of an "encrypt-then-HMAC" encryption scheme. Its cryptographic strength is the lesser
of the key strength used for the Credential and 128 bits, the strength of the HMAC-SHA256 used. It's pretty
good overall and is generally suitable for encrypting small amounts of data like a password file or a key used
in another scheme.
