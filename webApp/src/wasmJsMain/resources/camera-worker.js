/* Official MediaPipe Tasks runs in this worker; the Compose/UI thread remains available. */
let recognizer = null;

self.onmessage = async ({ data }) => {
  try {
    if (data.type === "init") {
      importScripts(new URL("mediapipe/vision_bundle.js", self.location.href).href);
      const sdk = self.Vision || self.vision || self;
      const FilesetResolver = sdk.FilesetResolver;
      const GestureRecognizer = sdk.GestureRecognizer;
      if (!FilesetResolver || !GestureRecognizer) throw new Error("MediaPipe exports unavailable");
      const files = await FilesetResolver.forVisionTasks(new URL("mediapipe/wasm", self.location.href).href);
      recognizer = await GestureRecognizer.createFromOptions(files, {
        baseOptions: { modelAssetPath: new URL("mediapipe/gesture_recognizer.task", self.location.href).href, delegate: "CPU" },
        runningMode: "VIDEO", numHands: 1,
        minHandDetectionConfidence: .55, minHandPresenceConfidence: .55, minTrackingConfidence: .55,
      });
      self.postMessage({ type: "ready" });
    } else if (data.type === "frame" && recognizer) {
      const start = performance.now();
      try {
        const result = recognizer.recognizeForVideo(data.image, data.timestampMs);
        const category = [...(result.gestures?.[0] || [])].sort((a, b) => b.score - a.score)[0];
        self.postMessage({ type: "frame", label: category?.categoryName ?? null, score: category?.score ?? 0,
          points: (result.landmarks?.[0] || []).map(p => ({ x: p.x, y: p.y })),
          worldPoints: (result.worldLandmarks?.[0] || []).map(p => ({ x: p.x, y: p.y, z: p.z })),
          width: data.width, height: data.height, timestampMs: data.timestampMs,
          inferenceMs: Math.max(0, Math.round(performance.now() - start)) });
      } finally { data.image.close(); }
    }
  } catch (error) {
    console.error("Gesture worker:", error);
    self.postMessage({ type: "error", message: "Не удалось запустить распознавание. Проверьте файлы сборки и повторите." });
  }
};
