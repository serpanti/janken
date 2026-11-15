# Run through build_worker.py on the operating system being packaged.
from pathlib import Path
from PyInstaller.utils.hooks import collect_all, copy_metadata

root = Path(SPECPATH).parent
data, binaries, hidden = collect_all('mediapipe')
data += copy_metadata('mediapipe')
a = Analysis(
    [str(root / 'desktop-camera' / 'camera_worker.py')],
    pathex=[], binaries=binaries, datas=data, hiddenimports=hidden,
    hookspath=[], hooksconfig={'matplotlib': {'backends': ['Agg']}},
    runtime_hooks=[], excludes=['tkinter', 'IPython', 'pytest'], noarchive=False,
)
pyz = PYZ(a.pure)
exe = EXE(
    pyz, a.scripts, [], exclude_binaries=True, name='camera',
    debug=False, bootloader_ignore_signals=False, strip=False, upx=False,
    console=True,
    # Keep redirected stdin/stdout for the protocol, without a flashing console.
    hide_console='hide-early',
)
coll = COLLECT(exe, a.binaries, a.datas, strip=False, upx=False, name='camera')
