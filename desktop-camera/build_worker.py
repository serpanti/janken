"""Build a self-contained local worker for the current OS/architecture.

Build prerequisites live on the developer/CI machine, never on the player's PC.
The one-folder layout avoids unpacking the ML SDK at every camera startup.
"""
from pathlib import Path
import importlib.metadata
import json
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
DESTINATION = ROOT / 'desktopApp/build/camera-resources/common'


def main():
    try:
        import PyInstaller
    except ImportError:
        raise SystemExit('Install build requirements first: python -m pip install -r desktop-camera/requirements-build.txt')
    if PyInstaller.__version__ != '6.22.3':
        raise SystemExit('Use the pinned PyInstaller from desktop-camera/requirements-build.txt')
    subprocess.run([
        sys.executable, '-m', 'PyInstaller', '--noconfirm',
        '--distpath', str(DESTINATION),
        '--workpath', str(ROOT / 'desktopApp/build/pyinstaller'),
        str(ROOT / 'desktop-camera/camera.spec'),
    ], cwd=ROOT, check=True)
    camera = DESTINATION / 'camera'
    shutil.copy2(ROOT / 'models/gesture_recognizer.task', camera)
    licenses = camera / 'licenses'
    licenses.mkdir(exist_ok=True)
    packages = []
    # Preserve upstream notices shipped by each distribution in this build env.
    for distribution in importlib.metadata.distributions():
        name = distribution.metadata['Name']
        packages.append({'name': name, 'version': distribution.version})
        for entry in distribution.files or []:
            if any(word in entry.name.lower() for word in ('license', 'copying', 'notice')):
                source = Path(distribution.locate_file(entry))
                if source.is_file():
                    target = licenses / name / str(entry).replace('..', '_')
                    target.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copy2(source, target)
    # CPython's license belongs to the interpreter, not to a pip distribution.
    for name in ('LICENSE.txt', 'LICENSE'):
        source = Path(sys.base_prefix) / name
        if source.is_file():
            shutil.copy2(source, licenses / 'Python-LICENSE.txt')
            break
    (licenses / 'build-environment.json').write_text(json.dumps(sorted(packages, key=lambda p: p['name'].lower()), indent=2), encoding='utf-8')
    shutil.copytree(ROOT / 'licenses', DESTINATION / 'licenses', dirs_exist_ok=True)
    for name in ('LICENSE', 'NOTICE.md'):
        shutil.copy2(ROOT / name, DESTINATION / name)
    print(f'Bundled camera runtime: {camera}')


if __name__ == '__main__':
    main()
