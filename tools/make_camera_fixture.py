"""Build a deterministic fake webcam clip; no physical camera is accessed."""
from pathlib import Path
import cv2
import numpy as np

root = Path(__file__).resolve().parents[1]
source = root / 'app/src/androidTest/assets/fist.jpg'
frame = cv2.imdecode(np.fromfile(source, dtype=np.uint8), cv2.IMREAD_COLOR)
height, width = frame.shape[:2]
scale = min(640 / width, 480 / height)
resized = cv2.resize(frame, (round(width * scale), round(height * scale)))
canvas = np.zeros((480, 640, 3), dtype=np.uint8)
y = (480 - resized.shape[0]) // 2
x = (640 - resized.shape[1]) // 2
canvas[y:y + resized.shape[0], x:x + resized.shape[1]] = resized
yuv = cv2.cvtColor(canvas, cv2.COLOR_BGR2YUV_I420)
destination = root / '.work/camera-fixture.y4m'
destination.parent.mkdir(exist_ok=True)
with destination.open('wb') as stream:
    stream.write(b'YUV4MPEG2 W640 H480 F20:1 Ip A1:1 C420jpeg\n')
    for _ in range(40):
        stream.write(b'FRAME\n')
        stream.write(yuv.tobytes())
print(destination)
