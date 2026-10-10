"""Reject stale compiled FcitxAPI delegate classes before distributing a build."""
import argparse
from pathlib import Path

def methods(path):
    data = path.read_bytes()
    assert data[:4] == b'\xca\xfe\xba\xbe', path
    position = 8
    def integer(size):
        nonlocal position
        value = int.from_bytes(data[position:position + size], 'big')
        position += size
        return value
    constants = {}
    count = integer(2)
    index = 1
    while index < count:
        tag = integer(1)
        if tag == 1:
            size = integer(2)
            constants[index] = data[position:position + size].decode('utf8', errors='replace')
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18): size = 4
        elif tag in (5, 6): size = 8; index += 1
        elif tag in (7, 8, 16, 19, 20): size = 2
        elif tag == 15: size = 3
        else: raise ValueError(f'Unknown class constant {tag}: {path}')
        position += size
        index += 1
    position += 6
    interface_count = integer(2)
    position += interface_count * 2
    def members():
        nonlocal position
        result = {}
        for _ in range(integer(2)):
            access, name, descriptor = integer(2), integer(2), integer(2)
            result[(constants[name], constants[descriptor])] = access
            for _ in range(integer(2)):
                integer(2)
                size = integer(4)
                position += size
        return result
    members()  # Fields precede methods.
    return members()

def check(classes):
    api = methods(classes / 'org/fcitx/fcitx5/android/core/FcitxAPI.class')
    delegate = methods(classes / 'org/fcitx/fcitx5/android/daemon/FcitxDaemon$fcitxImpl$2$1.class')
    required = [signature for signature, flags in api.items() if flags & 0x0400]
    missing = [signature for signature in required if signature not in delegate or delegate[signature] & 0x0400]
    if missing:
        raise SystemExit('FcitxAPI delegate is stale or incomplete: ' + ', '.join(name for name, _ in missing))
    print(f'FcitxAPI delegate: all {len(required)} abstract methods implemented PASS')

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--classes', type=Path, required=True)
    check(parser.parse_args().classes)
