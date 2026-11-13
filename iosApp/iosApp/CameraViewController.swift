import UIKit
import AVFoundation
import MediaPipeTasksVision
import JankenShared

/// Native preview is embedded underneath the common Compose hand overlay.
final class CameraViewController: UIViewController, NativeCameraController,
    AVCaptureVideoDataOutputSampleBufferDelegate, GestureRecognizerLiveStreamDelegate {
    var viewController: UIViewController { self }
    private let sink: IosCameraSink
    private let session = AVCaptureSession()
    private let queue = DispatchQueue(label: "dev.janken.camera", qos: .userInitiated)
    private let output = AVCaptureVideoDataOutput()
    private lazy var preview = AVCaptureVideoPreviewLayer(session: session)
    private var recognizer: GestureRecognizer?
    private var configured = false
    private var running = false // Access only on queue.
    private var front = true
    private var lastTimestamp = -1
    private var pending: (timestamp: Int, width: Int, height: Int, image: MPImage)?

    init(sink: IosCameraSink) {
        self.sink = sink
        super.init(nibName: nil, bundle: nil)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        preview.videoGravity = .resizeAspectFill
        view.layer.addSublayer(preview)
    }
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        requestCamera()
    }
    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        stop()
    }
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        preview.frame = view.bounds
        updateOrientation()
    }
    override func viewWillTransition(to size: CGSize, with coordinator: UIViewControllerTransitionCoordinator) {
        super.viewWillTransition(to: size, with: coordinator)
        coordinator.animate(alongsideTransition: nil) { [weak self] _ in self?.updateOrientation() }
    }

    private func requestCamera() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: start()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                DispatchQueue.main.async {
                    guard let self = self, self.viewIfLoaded?.window != nil else { return }
                    if granted { self.start() } else { self.report("Разрешите камеру в настройках приложения и нажмите «Повторить».") }
                }
            }
        default: report("Доступ к камере запрещён. Разрешите его в настройках iOS и нажмите «Повторить».")
        }
    }

    private func start() {
        queue.async { [weak self] in
            guard let self = self, !self.running else { return }
            do {
                guard let path = Bundle.main.path(forResource: "gesture_recognizer", ofType: "task") else {
                    throw CameraFailure.message("Модель распознавания отсутствует в сборке.")
                }
                let options = GestureRecognizerOptions()
                options.baseOptions.modelAssetPath = path
                options.runningMode = .liveStream
                options.numHands = 1
                options.minHandDetectionConfidence = 0.55
                options.minHandPresenceConfidence = 0.55
                options.minTrackingConfidence = 0.55
                options.gestureRecognizerLiveStreamDelegate = self
                self.recognizer = try GestureRecognizer(options: options)
                if !self.configured { try self.configure() }
                self.output.setSampleBufferDelegate(self, queue: self.queue)
                self.lastTimestamp = -1
                self.pending = nil
                self.running = true
                self.session.startRunning()
                DispatchQueue.main.async { [weak self] in
                    guard let self = self else { return }
                    self.updateOrientation()
                    self.sink.ready()
                }
            } catch {
                self.running = false
                self.recognizer = nil
                self.report((error as? CameraFailure)?.description ?? "Не удалось запустить камеру и распознавание. Повторите попытку.")
            }
        }
    }

    private func configure() throws {
        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .front)
            ?? AVCaptureDevice.default(for: .video) else {
            throw CameraFailure.message("На устройстве нет доступной камеры.")
        }
        let input = try AVCaptureDeviceInput(device: device)
        session.beginConfiguration()
        defer { session.commitConfiguration() }
        session.sessionPreset = .vga640x480
        guard session.canAddInput(input), session.canAddOutput(output) else {
            throw CameraFailure.message("Камера недоступна. Закройте другие приложения с камерой и повторите.")
        }
        session.addInput(input)
        output.videoSettings = [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA]
        output.alwaysDiscardsLateVideoFrames = true
        session.addOutput(output)
        front = device.position == .front
        configured = true
    }

    private func updateOrientation() {
        let orientation: AVCaptureVideoOrientation
        switch view.window?.windowScene?.interfaceOrientation {
        case .landscapeLeft: orientation = .landscapeLeft
        case .landscapeRight: orientation = .landscapeRight
        case .portraitUpsideDown: orientation = .portraitUpsideDown
        default: orientation = .portrait
        }
        if let connection = preview.connection {
            if connection.isVideoOrientationSupported { connection.videoOrientation = orientation }
            if connection.isVideoMirroringSupported { connection.automaticallyAdjustsVideoMirroring = false; connection.isVideoMirrored = front }
        }
        queue.async { [weak self] in
            guard let self = self, let connection = self.output.connection(with: .video) else { return }
            if connection.isVideoOrientationSupported { connection.videoOrientation = orientation }
            if connection.isVideoMirroringSupported { connection.automaticallyAdjustsVideoMirroring = false; connection.isVideoMirrored = self.front }
        }
    }

    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        guard running, pending == nil, let recognizer = recognizer else { return }
        let timestamp = Int(ProcessInfo.processInfo.systemUptime * 1000)
        guard timestamp - lastTimestamp >= 75, let buffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        lastTimestamp = timestamp
        do {
            // AVCaptureVideoDataOutput has already rotated/mirrored the actual pixels.
            let image = try MPImage(sampleBuffer: sampleBuffer, orientation: .up)
            pending = (timestamp, CVPixelBufferGetWidth(buffer), CVPixelBufferGetHeight(buffer), image)
            try recognizer.recognizeAsync(image: image, timestampInMilliseconds: timestamp)
        } catch { pending = nil; report("Не удалось обработать кадр. Повторите запуск камеры."); stop() }
    }

    func gestureRecognizer(_ gestureRecognizer: GestureRecognizer, didFinishGestureRecognition result: GestureRecognizerResult?,
        timestampInMilliseconds: Int, error: Error?) {
        queue.async { [weak self] in
            guard let self = self, self.running, let packet = self.pending,
                  packet.timestamp == timestampInMilliseconds else { return }
            self.pending = nil
            guard error == nil, let result = result else {
                self.report("Ошибка распознавания. Повторите запуск камеры.")
                self.stop()
                return
            }
            let category = result.gestures.first?.max(by: { $0.score < $1.score })
            let points = result.landmarks.first?.map { ["x": Double($0.x), "y": Double($0.y)] } ?? []
            let worldPoints = result.worldLandmarks.first?.map { ["x": Double($0.x), "y": Double($0.y), "z": Double($0.z)] } ?? []
            let label: Any
            if let name = category?.categoryName { label = name } else { label = NSNull() }
            let json: [String: Any] = ["type": "frame", "label": label,
                "score": category.map { Double($0.score) } ?? 0,
                "points": points, "worldPoints": worldPoints, "width": packet.width, "height": packet.height,
                "timestampMs": packet.timestamp,
                "inferenceMs": max(0, Int(ProcessInfo.processInfo.systemUptime * 1000) - packet.timestamp)]
            guard let data = try? JSONSerialization.data(withJSONObject: json), let text = String(data: data, encoding: .utf8) else { return }
            DispatchQueue.main.async { [weak self] in self?.sink.result(json: text) }
        }
    }

    func stop() {
        queue.async { [self] in
            self.running = false
            self.output.setSampleBufferDelegate(nil, queue: nil)
            if self.session.isRunning { self.session.stopRunning() }
            self.pending = nil
            self.recognizer = nil
        }
    }

    private func report(_ message: String) {
        DispatchQueue.main.async { [weak self] in self?.sink.failure(message: message) }
    }
}

private enum CameraFailure: Error {
    case message(String)
    var description: String { switch self { case .message(let text): return text } }
}
