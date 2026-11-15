"""Local camera + MediaPipe. stdout is a bounded newline-delimited JSON protocol.

No server, sockets or image files are used for the live camera path. Kotlin owns
the process lifetime; EOF/"stop" on stdin releases the capture device.
"""
from __future__ import annotations

import argparse
import base64
import json
import sys
import threading
import time
from pathlib import Path


def emit(packet: dict) -> None:
    sys.stdout.buffer.write((json.dumps(packet, ensure_ascii=False, separators=(",", ":")) + "\n").encode("utf-8"))
    sys.stdout.buffer.flush()


class CameraFailure(RuntimeError):
    """A deliberately user-facing message, as opposed to an SDK exception."""


class LatestCamera:
    def __init__(self, cv2, source, stopped: threading.Event):
        self.cv2 = cv2
        self.stopped = stopped
        self.lock = threading.Lock()
        self.latest = None
        self.error = None
        self.ready = threading.Event()
        self.capture = cv2.VideoCapture(source)
        if not self.capture.isOpened():
            raise CameraFailure("Не удалось открыть камеру. Проверьте разрешение и закройте приложения, которые её используют.")
        self.capture.set(cv2.CAP_PROP_FRAME_WIDTH, 640)
        self.capture.set(cv2.CAP_PROP_FRAME_HEIGHT, 480)
        self.capture.set(cv2.CAP_PROP_BUFFERSIZE, 1)
        self.thread = threading.Thread(target=self._read, daemon=True)
        self.thread.start()

    def _read(self):
        try:
            while not self.stopped.is_set():
                ok, frame = self.capture.read()
                captured = time.monotonic_ns() // 1_000_000
                if not ok:
                    self.error = "Поток камеры прерван. Подключите камеру и повторите."
                    self.ready.set()
                    break
                with self.lock:
                    self.latest = (captured, frame)
                self.ready.set()
        finally:
            self.capture.release()

    def take(self):
        if not self.ready.wait(10):
            raise CameraFailure("Камера не передаёт изображение. Проверьте её подключение.")
        if self.error:
            raise CameraFailure(self.error)
        with self.lock:
            return self.latest

    def close(self):
        self.stopped.set()
        self.thread.join(timeout=2)


def run(args) -> None:
    import cv2
    import numpy as np
    import mediapipe as mp
    from mediapipe.tasks.python import BaseOptions, vision

    stopped = threading.Event()

    def read_control():
        for line in sys.stdin:
            if line.strip() == "stop":
                break
        stopped.set()

    threading.Thread(target=read_control, daemon=True).start()
    options = vision.GestureRecognizerOptions(
        base_options=BaseOptions(model_asset_buffer=Path(args.model).read_bytes(), delegate=BaseOptions.Delegate.CPU),
        running_mode=vision.RunningMode.VIDEO,
        num_hands=1,
        min_hand_detection_confidence=.55,
        min_hand_presence_confidence=.55,
        min_tracking_confidence=.55,
    )
    camera = None
    fixture = None
    try:
        if args.image:
            fixture = cv2.imdecode(np.fromfile(args.image, dtype=np.uint8), cv2.IMREAD_COLOR)
            if fixture is None:
                raise CameraFailure("Не удалось прочитать тестовое изображение.")
        else:
            source = int(args.source) if args.source.isdecimal() else args.source
            camera = LatestCamera(cv2, source, stopped)
            camera.take()
        with vision.GestureRecognizer.create_from_options(options) as recognizer:
            emit({"type": "ready"})
            count = 0
            last_timestamp = -1
            while not stopped.is_set():
                iteration = time.monotonic()
                if fixture is not None:
                    captured, frame = time.monotonic_ns() // 1_000_000, fixture.copy()
                else:
                    captured, frame = camera.take()
                if captured <= last_timestamp:
                    stopped.wait(.005)
                    continue
                last_timestamp = captured
                if max(frame.shape[:2]) > 640:
                    scale = 640 / max(frame.shape[:2])
                    frame = cv2.resize(frame, (round(frame.shape[1] * scale), round(frame.shape[0] * scale)))
                if not args.no_mirror:
                    frame = cv2.flip(frame, 1)
                height, width = frame.shape[:2]
                rgb = np.ascontiguousarray(cv2.cvtColor(frame, cv2.COLOR_BGR2RGB))
                before = time.monotonic_ns()
                result = recognizer.recognize_for_video(mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb), captured)
                inference = (time.monotonic_ns() - before) // 1_000_000
                categories = result.gestures[0] if result.gestures else []
                category = max(categories, key=lambda c: c.score) if categories else None
                points = result.hand_landmarks[0] if result.hand_landmarks else []
                world_points = result.hand_world_landmarks[0] if result.hand_world_landmarks else []
                ok, jpeg = cv2.imencode(".jpg", frame, [cv2.IMWRITE_JPEG_QUALITY, 75])
                if not ok:
                    raise CameraFailure("Не удалось подготовить изображение камеры.")
                emit({"type": "frame", "label": category.category_name if category else None,
                      "score": float(category.score) if category else 0,
                      "points": [{"x": float(p.x), "y": float(p.y)} for p in points],
                      "worldPoints": [{"x": float(p.x), "y": float(p.y), "z": float(p.z)} for p in world_points],
                      "width": width, "height": height, "timestampMs": captured,
                      "captureAgeMs": max(0, time.monotonic_ns() // 1_000_000 - captured),
                      "inferenceMs": inference, "jpeg": base64.b64encode(jpeg).decode("ascii")})
                count += 1
                if args.max_frames and count >= args.max_frames:
                    break
                stopped.wait(max(0, 1 / 13 - (time.monotonic() - iteration)))
    finally:
        stopped.set()
        if camera:
            camera.close()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", required=True)
    parser.add_argument("--source", default="0")
    parser.add_argument("--image", help="Deterministic fixture source for automated tests; never enabled by default")
    parser.add_argument("--max-frames", type=int, default=0)
    parser.add_argument("--no-mirror", action="store_true")
    args = parser.parse_args()
    try:
        run(args)
        return 0
    except (BrokenPipeError, KeyboardInterrupt):
        return 0
    except ImportError as error:
        print(f"Camera dependency error: {error}", file=sys.stderr)
        emit({"type": "error", "message": "Компонент камеры не установлен. Выполните первоначальную настройку по инструкции к сборке."})
        return 1
    except Exception as error:
        print(f"Camera failure: {type(error).__name__}: {error}", file=sys.stderr)
        emit({"type": "error", "message": str(error) if isinstance(error, CameraFailure) else "Не удалось запустить распознавание. Проверьте компонент камеры и повторите."})
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
