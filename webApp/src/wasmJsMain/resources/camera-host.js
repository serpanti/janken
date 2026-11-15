(function () {
  "use strict";

  function start(callbacks) {
    let stopped = false, stream = null, worker = null, video = null, animation = 0;
    let startupTimer = 0, busy = false, lastPreview = -Infinity, lastInference = -Infinity;
    let rejectReady = null;
    const stop = () => {
      if (stopped) return;
      stopped = true;
      clearTimeout(startupTimer);
      cancelAnimationFrame(animation);
      stream?.getTracks().forEach(track => track.stop());
      if (video) { video.pause(); video.srcObject = null; video.remove(); }
      worker?.terminate();
      rejectReady?.(new Error("Camera session stopped"));
      rejectReady = null;
    };
    const fail = message => {
      if (stopped) return;
      stop();
      callbacks.onError(message);
    };

    (async () => {
      if (!globalThis.isSecureContext || !navigator.mediaDevices?.getUserMedia) {
        fail("Для камеры нужен HTTPS или localhost. Откройте приложение по защищённому адресу.");
        return;
      }
      worker = new Worker(new URL("camera-worker.js", document.baseURI));
      const ready = new Promise((resolve, reject) => {
        rejectReady = reject;
        worker.onmessage = ({ data }) => {
          if (stopped) return;
          if (data.type === "ready") { rejectReady = null; resolve(); }
          else if (data.type === "frame") {
            busy = false;
            callbacks.onObservation(JSON.stringify(data));
          } else if (data.type === "error") {
            fail(data.message || "Ошибка распознавания.");
          }
        };
        worker.onerror = error => { console.error(error); fail("Не удалось загрузить распознавание. Повторите запуск камеры."); };
      });
      // Attach a rejection handler immediately, including while the OS permission dialog is open.
      ready.catch(() => {});
      startupTimer = setTimeout(() => fail("Камера не запустилась. Проверьте разрешение и повторите."), 30_000);
      worker.postMessage({ type: "init" });
      const acquired = await navigator.mediaDevices.getUserMedia({ audio: false,
        video: { facingMode: "user", width: { ideal: 640 }, height: { ideal: 480 } } });
      if (stopped) { acquired.getTracks().forEach(track => track.stop()); return; }
      stream = acquired;
      stream.getVideoTracks().forEach(track => track.addEventListener("ended", () => fail("Камера отключена. Подключите её и повторите.")));
      video = document.createElement("video");
      video.muted = true; video.playsInline = true; video.autoplay = true;
      video.style.cssText = "position:fixed;left:-10px;top:-10px;width:1px;height:1px;opacity:0;pointer-events:none";
      document.body.appendChild(video);
      video.srcObject = stream;
      await Promise.all([video.play(), ready]);
      if (stopped) return;
      clearTimeout(startupTimer);
      const canvas = document.createElement("canvas");
      const context = canvas.getContext("2d", { alpha: false });
      if (!context) throw new Error("Canvas unavailable");
      const mirror = stream.getVideoTracks()[0]?.getSettings().facingMode !== "environment";
      callbacks.onReady();

      function frame(now) {
        if (stopped) return;
        animation = requestAnimationFrame(frame);
        if (!video.videoWidth || now - lastPreview < 50) return;
        lastPreview = now;
        const scale = Math.min(1, 640 / Math.max(video.videoWidth, video.videoHeight));
        const width = Math.round(video.videoWidth * scale), height = Math.round(video.videoHeight * scale);
        if (canvas.width !== width || canvas.height !== height) { canvas.width = width; canvas.height = height; }
        context.setTransform(mirror ? -1 : 1, 0, 0, 1, mirror ? width : 0, 0);
        context.drawImage(video, 0, 0, width, height);
        callbacks.onPreview(canvas.toDataURL("image/jpeg", .75).split(",")[1]);
        if (!busy && now - lastInference >= 75) {
          busy = true;
          lastInference = now;
          createImageBitmap(canvas).then(image => {
            if (stopped) { image.close(); return; }
            worker.postMessage({ type: "frame", image, width, height, timestampMs: Math.floor(now) }, [image]);
          }).catch(error => { console.error(error); fail("Не удалось обработать изображение камеры."); });
        }
      }
      animation = requestAnimationFrame(frame);
    })().catch(error => {
      if (stopped) return;
      console.error("Camera:", error);
      const denied = error?.name === "NotAllowedError" || error?.name === "PermissionDeniedError";
      fail(denied ? "Доступ к камере запрещён. Разрешите камеру для этого сайта в браузере и нажмите «Повторить»."
        : "Камера недоступна. Проверьте подключение и закройте приложения, которые её используют.");
    });
    return { stop };
  }

  globalThis.JankenCamera = { start };
})();
