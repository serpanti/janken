"""Exercise the packaged launcher, bundled JVM and ML worker without a webcam."""
from pathlib import Path
import argparse
import json
import os
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('application', type=Path, help='Path to packaged Janken.exe (Windows) or launcher (macOS/Linux)')
    args = parser.parse_args()
    application = args.application.resolve(strict=True)
    work = ROOT / '.work'
    work.mkdir(exist_ok=True)
    report = work / 'desktop-distribution.properties'
    if report.exists():
        report.unlink()
    environment = os.environ.copy()
    environment['PATH'] = str(Path(os.environ['SystemRoot']) / 'System32') if os.name == 'nt' else '/usr/bin:/bin'
    environment['JAVA_HOME'] = str(work / 'no-external-java')
    environment['JANKEN_PYTHON'] = str(work / 'no-external-python')
    environment['JANKEN_CAMERA_TEST_IMAGE'] = str(ROOT / 'app/src/androidTest/assets/fist.jpg')
    for name in ['PYTHONHOME', 'PYTHONPATH', 'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS']:
        environment.pop(name, None)
    completed = subprocess.run([str(application), '--check-distribution', str(report)],
                               env=environment, cwd=work, capture_output=True, timeout=45)
    contents = report.read_text(encoding='ascii') if report.exists() else ''
    if completed.returncode != 0 or 'status=ok' not in contents:
        raise RuntimeError(f'Packaged launcher failed ({completed.returncode}):\n{contents}\n{completed.stderr.decode("utf-8", errors="replace")}')
    assert 'points=21' in contents and 'move=ROCK' in contents and 'preview=decoded' in contents, contents
    java_home = next(line for line in contents.splitlines() if line.startswith('java.home='))
    assert 'runtime' in java_home, java_home
    evidence = {'status': 'ok', 'application': str(application), 'external_java_home': environment['JAVA_HOME'],
                'external_python': environment['JANKEN_PYTHON'], 'path': environment['PATH'],
                'points': 21, 'move': 'ROCK', 'preview': 'decoded', 'webcam_opened': False}
    (work / 'desktop-distribution-check.json').write_text(json.dumps(evidence, ensure_ascii=False, indent=2), encoding='utf-8')
    print(contents)
    print('Packaged launcher + bundled JVM + bundled camera: OK')


if __name__ == '__main__':
    main()
