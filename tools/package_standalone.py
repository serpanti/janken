"""Copy, verify and archive a self-contained Windows application image."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import shutil
import stat
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'outputs'


def filesystem_path(path):
    return '\\\\?\\' + str(path) if os.name == 'nt' else str(path)


def remove_readonly(function, path, error):
    os.chmod(path, stat.S_IWRITE | stat.S_IREAD)
    function(path)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('image', type=Path)
    args = parser.parse_args()
    source = args.image.resolve(strict=True)
    assert (source / 'Janken.exe').is_file()
    assert (source / 'runtime/lib/modules').is_file()
    OUT.mkdir(exist_ok=True)
    destination = OUT / 'Janken-Windows-standalone'
    assert destination.resolve().parent == OUT.resolve() and destination.name == 'Janken-Windows-standalone'
    if destination.exists():
        shutil.rmtree(filesystem_path(destination), onexc=remove_readonly)
    shutil.copytree(filesystem_path(source), filesystem_path(destination))
    resources = destination / 'app/resources'
    shutil.copytree(ROOT / 'licenses', resources / 'licenses', dirs_exist_ok=True)
    for name in ('LICENSE', 'NOTICE.md'):
        shutil.copy2(ROOT / name, resources / name)
    version = next(line.split('jpackage.app-version=', 1)[1] for line in (source / 'app/Janken.cfg').read_text(encoding='utf-8').splitlines() if 'jpackage.app-version=' in line)
    (destination / 'README.txt').write_text(f'''Janken — Windows x64, прототип {version}

Полностью распакуйте архив и запустите Janken.exe.
Java, Python, pip и другие инструменты устанавливать не требуется.
Все соседние каталоги обязательны: переносите папку целиком.
Камера включается после нажатия кнопки в приложении.
Распознавание работает локально. Модель и библиотеки уже включены.
Лицензии: app/resources/LICENSE, NOTICE.md, licenses и camera/licenses;
лицензии Java: runtime/legal.

Проверен запуск через встроенную JVM и распознавание тестовых изображений.
Физическая веб-камера в этой среде не проверялась.
''', encoding='utf-8')
    subprocess.run([sys.executable, str(ROOT / 'tools/check_desktop_distribution.py'), str(destination / 'Janken.exe')], check=True)
    environment = os.environ.copy()
    environment['JANKEN_CAMERA_EXECUTABLE'] = str(resources / 'camera/camera.exe')
    subprocess.run([sys.executable, str(ROOT / 'desktop-camera/test_worker.py')], env=environment, check=True)
    package = OUT / 'Janken-Windows-standalone.zip'
    archive_root = Path(filesystem_path(destination))
    with zipfile.ZipFile(package, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for directory, _, names in os.walk(archive_root):
            for name in sorted(names):
                file = Path(directory) / name
                archive.write(file, Path(destination.name) / file.relative_to(archive_root))
    with zipfile.ZipFile(package) as archive:
        if damaged_file := archive.testzip():
            raise ValueError(f'Damaged archive entry: {damaged_file}')
        count = len(archive.infolist())
    with package.open('rb') as stream:
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    evidence = {'file': str(package), 'bytes': package.stat().st_size, 'sha256': digest, 'files': count}
    (ROOT / '.work/standalone-artifact.json').write_text(json.dumps(evidence, indent=2), encoding='utf-8')
    print(json.dumps(evidence, indent=2))


if __name__ == '__main__':
    main()
