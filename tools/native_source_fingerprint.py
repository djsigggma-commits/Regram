#!/usr/bin/env python3
"""Content fingerprint for native sources restored with historical timestamps."""
import hashlib
from pathlib import Path
import sys

IGNORED = {'.git', '.cxx', '.gradle', 'build', '__pycache__'}


def fingerprint(root):
    root = Path(root)
    digest = hashlib.sha256()
    for path in sorted(root.rglob('*')):
        relative = path.relative_to(root)
        if not path.is_file() or any(part in IGNORED for part in relative.parts):
            continue
        digest.update(relative.as_posix().encode('utf-8') + b'\0')
        contents = hashlib.sha256()
        with path.open('rb') as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b''):
                contents.update(chunk)
        digest.update(contents.digest())
    return digest.hexdigest()


if __name__ == '__main__':
    print(fingerprint(sys.argv[1]))
