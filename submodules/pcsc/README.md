This directory contains both a Linux shared library for `libpcsclite`,
and the original Debian package containing the library, allowing as easy
check for the provenance of the shared library file.

This .so is bundled because, for better or for worse, Kotlin-Native builds against an
extremely old glibc, and newer versions of PCSClite are incompatible with that glibc
version.

This is only a concern at link time; at run time `FIDOk` will operate properly against
a modern `libpcsclite`. But the library is bundled here to make building possible on
modern Linux systems.
