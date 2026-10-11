#!/usr/bin/env python3
"""Fails if an APK's signing block carries AGP's "Dependency metadata" entry.

AGP encrypts the app's dependency list with Google's public key and stores it in the APK
Signing Block (id 0x504b4453). F-Droid's scanner rejects APKs that contain it.
"""
import struct
import sys

DEPENDENCY_METADATA = 0x504B4453
SIGNING_BLOCK_MAGIC = b"APK Sig Block 42"
END_OF_CENTRAL_DIRECTORY = b"PK\x05\x06"


def signing_block_ids(data: bytes) -> list[int]:
    eocd = data.rfind(END_OF_CENTRAL_DIRECTORY)
    if eocd < 0:
        raise ValueError("not a ZIP file")
    central_directory = struct.unpack_from("<I", data, eocd + 16)[0]
    if data[central_directory - 16:central_directory] != SIGNING_BLOCK_MAGIC:
        raise ValueError("no APK Signing Block (unsigned APK?)")
    block_size = struct.unpack_from("<Q", data, central_directory - 24)[0]
    offset = central_directory - block_size - 8 + 8
    end = central_directory - 24
    ids = []
    while offset < end:
        entry_size, entry_id = struct.unpack_from("<QI", data, offset)
        ids.append(entry_id)
        offset += 8 + entry_size
    return ids


def main() -> int:
    path = sys.argv[1]
    with open(path, "rb") as apk:
        data = apk.read()
    try:
        ids = signing_block_ids(data)
    except ValueError as error:
        print(f"{path}: {error}", file=sys.stderr)
        return 2
    if DEPENDENCY_METADATA in ids:
        print(f"{path}: contains the 'Dependency metadata' signing block F-Droid rejects", file=sys.stderr)
        return 1
    print(f"{path}: signing block ids {', '.join(hex(i) for i in ids)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
