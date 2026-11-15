"""Integration checks use public Google image fixtures, never the user's webcam."""
from pathlib import Path
import base64
import json
import os
import queue
import subprocess
import sys
import threading
import unittest

ROOT = Path(__file__).resolve().parents[1]
MODEL = ROOT / "models/gesture_recognizer.task"
FIXTURES = ROOT / "app/src/androidTest/assets"


class WorkerTests(unittest.TestCase):
    def start(self, image, mirror=False, model=MODEL):
        bundled = os.environ.get("JANKEN_CAMERA_EXECUTABLE")
        command = [bundled] if bundled else [sys.executable, str(ROOT / "desktop-camera/camera_worker.py")]
        args = [*command, "--model", str(model), "--image", str(image)]
        if not mirror:
            args.append("--no-mirror")
        environment = os.environ.copy()
        if bundled:
            environment.pop('PYTHONHOME', None)
            environment.pop('PYTHONPATH', None)
            environment['PATH'] = str(Path(os.environ['SystemRoot']) / 'System32') if sys.platform == 'win32' else '/usr/bin:/bin'
        process = subprocess.Popen(args, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, env=environment)
        packets = queue.Queue()

        def read():
            try:
                for line in process.stdout:
                    packets.put(json.loads(line))
            except Exception as error:
                packets.put(error)
            finally:
                packets.put(None)

        threading.Thread(target=read, daemon=True).start()
        return process, packets

    def stop(self, process):
        if process.poll() is None:
            process.stdin.write(b"stop\n")
            process.stdin.flush()
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=5)
            self.fail("Camera worker did not stop after stdin control")
        if process.returncode:
            self.fail(process.stderr.read().decode("utf-8", errors="replace")[-2000:])
        for pipe in [process.stdin, process.stdout, process.stderr]:
            pipe.close()

    def observe(self, image, label, mirror=False):
        process, packets = self.start(image, mirror)
        try:
            self.assertEqual(packets.get(timeout=20)["type"], "ready")
            frame = packets.get(timeout=10)
            self.assertEqual(frame["type"], "frame")
            self.assertEqual(frame["label"], label)
            self.assertGreater(frame["score"], .65)
            self.assertEqual(len(frame["points"]), 21)
            self.assertEqual(len(frame["worldPoints"]), 21)
            self.assertTrue(all(set(point) == {'x', 'y', 'z'} for point in frame['worldPoints']))
            self.assertGreaterEqual(frame["captureAgeMs"], 0)
            import cv2
            import numpy as np
            preview = cv2.imdecode(np.frombuffer(base64.b64decode(frame["jpeg"]), dtype=np.uint8), cv2.IMREAD_COLOR)
            self.assertEqual(preview.shape[:2], (frame["height"], frame["width"]))
        finally:
            self.stop(process)

    def test_fist(self):
        self.observe(FIXTURES / "fist.jpg", "Closed_Fist")

    def test_scissors(self):
        self.observe(FIXTURES / "victory.jpg", "Victory")

    def test_mirrored_front_camera_input(self):
        self.observe(FIXTURES / "fist.jpg", "Closed_Fist", mirror=True)

    def test_unrelated_gesture_is_preserved_for_common_filter(self):
        self.observe(FIXTURES / "thumb_up.jpg", "Thumb_Up")


if __name__ == "__main__":
    unittest.main(verbosity=2)
