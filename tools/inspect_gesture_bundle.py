"""Inspect the actual local model bundle with the Python standard library only."""
import argparse
import hashlib
from io import BytesIO
from pathlib import Path
import zipfile


def describe(data, indent=''):
    with zipfile.ZipFile(BytesIO(data)) as bundle:
        for entry in bundle.infolist():
            print(f'{indent}{entry.filename} ({entry.file_size:,} bytes)')
            if entry.filename.endswith('.task'):
                describe(bundle.read(entry), indent + '  ')


def main():
    default = Path(__file__).resolve().parents[1] / 'models/gesture_recognizer.task'
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('bundle', nargs='?', type=Path, default=default)
    args = parser.parse_args()
    data = args.bundle.read_bytes()
    print(f'{args.bundle.name} ({len(data):,} bytes)')
    print(f'SHA-256: {hashlib.sha256(data).hexdigest()}')
    describe(data, '  ')


if __name__ == '__main__':
    main()
