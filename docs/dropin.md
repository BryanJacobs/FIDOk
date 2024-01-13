libfido2 Drop-In Layer
======================

FIDOk can serve as a partial ABI-compatible replacement for Yubico's
[libfido2](https://developers.yubico.com/libfido2/Manuals/). You can compile
and link your C application against a normal `libfido2.so.1` (or against FIDOk itself...)
and then at run time use `libfidok.so` instead.

Example of how to use the drop-in:

```shell
gcc my_program.c -lfido2 -o my_program # Compile and link against normal libfido2
ln -s /usr/lib/libfidok.so.1 libfido2.so.1 # Create symlink that points to libfidok
env LD_LIBRARY_PATH=. ./my_application # Run with symlink in LD_LIBRARY_PATH ahead of real libfido2
```

Of course, placing `libfidok.so` in your system at the `/usr/lib/libfido2.so.1` path
would have the same effect.

Feature Status:

| Feature                         | Status          |
|---------------------------------|-----------------|
| Device Listing                  | Implemented     |
| Open Device from Listing Result | Implemented     |
| Open Device by Path             | Not Implemented |
| Get Device Info                 | Very Limited    |
| Create Credential               | Implemented     |
| Set/get raw CBOR                | Not Implemented |
| Verify Credential               | Not Implemented |
| Get Assertion                   | Implemented     |
| Extensions                      | Partial         |
| Verify Assertion                | Not Implemented |
| libfido2 crypto functions       | Not Implemented |

