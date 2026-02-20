import Flutter
import UIKit
import AVFoundation

public class ArTryonViewPlugin: NSObject, FlutterPlugin {
  public static func register(with registrar: FlutterPluginRegistrar) {
    // ✅ Keep your old channel (optional)
    let channel = FlutterMethodChannel(name: "ar_tryon_view", binaryMessenger: registrar.messenger())
    let instance = ArTryonViewPlugin()
    registrar.addMethodCallDelegate(instance, channel: channel)

    // ✅ Register PlatformView (this is what you need for camera+overlay view)
    let factory = ArTryOnViewFactory(messenger: registrar.messenger())
    registrar.register(factory, withId: "ar_tryon_view/native_view")
  }

  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    switch call.method {
    case "getPlatformVersion":
      result("iOS " + UIDevice.current.systemVersion)
    default:
      result(FlutterMethodNotImplemented)
    }
  }
}

// MARK: - Factory

final class ArTryOnViewFactory: NSObject, FlutterPlatformViewFactory {
  private let messenger: FlutterBinaryMessenger

  init(messenger: FlutterBinaryMessenger) {
    self.messenger = messenger
    super.init()
  }

  func createArgsCodec() -> FlutterMessageCodec & NSObjectProtocol {
    FlutterStandardMessageCodec.sharedInstance()
  }

  func create(
  withFrame frame: CGRect,
  viewIdentifier viewId: Int64,
  arguments args: Any?
  ) -> FlutterPlatformView {
    ArTryOnPlatformView(frame: frame, viewId: viewId, messenger: messenger)
  }
}

// MARK: - View container (keeps preview layer resized)

final class ArTryOnContainerView: UIView {
  var previewLayer: AVCaptureVideoPreviewLayer?

  override func layoutSubviews() {
    super.layoutSubviews()
    previewLayer?.frame = bounds
  }
}

// MARK: - PlatformView

final class ArTryOnPlatformView: NSObject, FlutterPlatformView {
  private let container: ArTryOnContainerView
  private let effectView: UIImageView

  private let channel: FlutterMethodChannel

  private let session = AVCaptureSession()
  private var isConfigured = false

  init(frame: CGRect, viewId: Int64, messenger: FlutterBinaryMessenger) {
    container = ArTryOnContainerView(frame: frame)
    effectView = UIImageView(frame: frame)

    // ✅ per-view channel like Android: ar_tryon_view/method_<id>
    channel = FlutterMethodChannel(
      name: "ar_tryon_view/method_\(viewId)",
      binaryMessenger: messenger
    )

    super.init()

    container.backgroundColor = .black

    effectView.isHidden = true
    effectView.alpha = 1.0
    effectView.contentMode = .scaleAspectFit
    effectView.backgroundColor = .clear
    effectView.translatesAutoresizingMaskIntoConstraints = false

    container.addSubview(effectView)
    NSLayoutConstraint.activate([
      effectView.leadingAnchor.constraint(equalTo: container.leadingAnchor),
      effectView.trailingAnchor.constraint(equalTo: container.trailingAnchor),
      effectView.topAnchor.constraint(equalTo: container.topAnchor),
      effectView.bottomAnchor.constraint(equalTo: container.bottomAnchor),
    ])

    channel.setMethodCallHandler { [weak self] call, result in
      self?.handle(call, result: result)
    }
  }

  func view() -> UIView { container }

  deinit { disposeInternal() }

  private func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    switch call.method {
    case "start":
      startCamera(result: result)

    case "stop":
      stopCamera()
      result(nil)

    case "setEffect":
      // optional string-based effect id (not used now)
      result(nil)

    case "setEffectBytes":
      // Flutter Uint8List -> FlutterStandardTypedData
      guard let typed = call.arguments as? FlutterStandardTypedData else {
        result(FlutterError(code: "EFFECT_BYTES_FAILED",
          message: "Expected Uint8List (FlutterStandardTypedData).",
          details: nil))
        return
      }

      let data = typed.data
      if let img = UIImage(data: data) {
        effectView.image = img
        effectView.isHidden = false
      } else {
        effectView.image = nil
        effectView.isHidden = true
      }
      result(nil)

    case "clearEffect":
      effectView.image = nil
      effectView.isHidden = true
      result(nil)

    case "dispose":
      disposeInternal()
      result(nil)

    default:
      result(FlutterMethodNotImplemented)
    }
  }

  // MARK: - Camera

  private func startCamera(result: @escaping FlutterResult) {
    let status = AVCaptureDevice.authorizationStatus(for: .video)

    switch status {
    case .authorized:
      configureAndStart(result: result)

    case .notDetermined:
      AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
        DispatchQueue.main.async {
          guard let self else { return }
          if granted {
            self.configureAndStart(result: result)
          } else {
            result(FlutterError(code: "NO_CAMERA_PERMISSION",
              message: "Camera permission not granted.",
              details: nil))
          }
        }
      }

    case .denied, .restricted:
      result(FlutterError(code: "NO_CAMERA_PERMISSION",
        message: "Camera permission denied/restricted.",
        details: nil))

    @unknown default:
      result(FlutterError(code: "NO_CAMERA_PERMISSION",
        message: "Unknown camera permission state.",
        details: nil))
    }
  }

  private func configureAndStart(result: @escaping FlutterResult) {
    if !isConfigured {
      do {
        try configureSession()
        isConfigured = true
      } catch {
        result(FlutterError(code: "CAMERA_CONFIG_FAILED",
          message: error.localizedDescription,
          details: nil))
        return
      }
    }

    if !session.isRunning {
      DispatchQueue.global(qos: .userInitiated).async { [weak self] in
        self?.session.startRunning()
      }
    }

    result(nil)
  }

  private func configureSession() throws {
    session.beginConfiguration()
    session.sessionPreset = .high

    // remove old inputs
    for input in session.inputs { session.removeInput(input) }

    guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .front) else {
      session.commitConfiguration()
      throw NSError(domain: "ArTryOn", code: -1,
        userInfo: [NSLocalizedDescriptionKey: "Front camera not available."])
    }

    let input = try AVCaptureDeviceInput(device: device)
    guard session.canAddInput(input) else {
      session.commitConfiguration()
      throw NSError(domain: "ArTryOn", code: -2,
        userInfo: [NSLocalizedDescriptionKey: "Cannot add camera input."])
    }
    session.addInput(input)
    session.commitConfiguration()

    // preview layer
    let previewLayer = AVCaptureVideoPreviewLayer(session: session)
    previewLayer.videoGravity = .resizeAspectFill
    previewLayer.frame = container.bounds

    // mirror selfie preview
    if let connection = previewLayer.connection, connection.isVideoMirroringSupported {
      connection.automaticallyAdjustsVideoMirroring = false
      connection.isVideoMirrored = true
    }

    // insert below overlay
    container.layer.sublayers?.removeAll(where: { $0 is AVCaptureVideoPreviewLayer })
    container.layer.insertSublayer(previewLayer, at: 0)
    container.previewLayer = previewLayer
  }

  private func stopCamera() {
    if session.isRunning {
      DispatchQueue.global(qos: .userInitiated).async { [weak self] in
        self?.session.stopRunning()
      }
    }
  }

  private func disposeInternal() {
    channel.setMethodCallHandler(nil)
    stopCamera()
    container.previewLayer?.removeFromSuperlayer()
    container.previewLayer = nil
    effectView.image = nil
    effectView.isHidden = true
  }
}