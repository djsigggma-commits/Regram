"""Prevent the removed third-party proxy runtime from returning to the APK."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / 'TMessagesProj/src/main'


def test_removed_runtime_has_no_sources_assets_or_resources():
    marker = b'exitfy'
    leftovers = []
    for path in MAIN.rglob('*'):
        if not path.is_file():
            continue
        if 'exitfy' in path.name.lower():
            leftovers.append(str(path.relative_to(ROOT)))
        elif path.suffix in {'.java', '.kt', '.py', '.xml', '.json', '.pro'}:
            if marker in path.read_bytes().lower():
                leftovers.append(str(path.relative_to(ROOT)))
    assert not leftovers, '\n'.join(leftovers)
