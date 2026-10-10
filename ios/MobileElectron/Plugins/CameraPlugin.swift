//
//  CameraPlugin.swift
//  MobileElectron
//
//  Created for Mobile Electron Framework iOS Container.
//

import UIKit
import AVFoundation

/// 工业级相机与条码扫描插件 (iOS 原生实现)
/// 支持原生拍摄、照片库拾取及 AVFoundation 硬件级毫秒级条码/二维码识别
public class CameraPlugin: NSObject, IBridgePlugin, UIImagePickerControllerDelegate, UINavigationControllerDelegate, AVCaptureMetadataOutputObjectsDelegate {
    public let moduleName = "camera"

    private weak var viewController: UIViewController?
    private var pendingCompletion: ((Int, [String: Any]?, String) -> Void)?

    // 扫描专用组件
    private var captureSession: AVCaptureSession?
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private var scannerContainerView: UIView?

    public init(viewController: UIViewController) {
        self.viewController = viewController
        super.init()
    }

    public func getRequiredTier(action: String) -> Int {
        return DomainWhitelistManager.TIER_1_BUSINESS
    }

    public func handleAction(
        callingUrl: String,
        action: String,
        params: [String: Any],
        completion: @escaping (Int, [String: Any]?, String) -> Void
    ) {
        DispatchQueue.main.async { [weak self] in
            guard let self = self, let vc = self.viewController else {
                completion(500, nil, "Host view controller is unavailable")
                return
            }

            self.pendingCompletion = completion

            switch action {
            case "takePhoto":
                if UIImagePickerController.isSourceTypeAvailable(.camera) {
                    let picker = UIImagePickerController()
                    picker.sourceType = .camera
                    picker.delegate = self
                    picker.allowsEditing = false
                    vc.present(picker, animated: true)
                } else {
                    // 无摄像头设备 (如 iOS 模拟器) 或未授权
                    completion(500, nil, "Camera is not available on this device or simulator")
                }

            case "pickImage":
                let picker = UIImagePickerController()
                picker.sourceType = .photoLibrary
                picker.delegate = self
                picker.allowsEditing = false
                vc.present(picker, animated: true)

            case "scanBarcode":
                self.startBarcodeScanning(on: vc, completion: completion)

            default:
                completion(404, nil, "Unknown action: \(action)")
            }
        }
    }

    // MARK: - UIImagePickerControllerDelegate

    public func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey : Any]) {
        picker.dismiss(animated: true) { [weak self] in
            guard let self = self, let completion = self.pendingCompletion else { return }
            self.pendingCompletion = nil

            if let image = info[.originalImage] as? UIImage {
                let scaledImage = self.scaleImageIfNeeded(image, maxDimension: 1280)
                if let data = scaledImage.jpegData(compressionQuality: 0.8) {
                    let base64 = data.base64EncodedString()
                    completion(200, [
                        "base64": "data:image/jpeg;base64," + base64,
                        "width": scaledImage.size.width,
                        "height": scaledImage.size.height
                    ], "success")
                    return
                }
            }
            completion(500, nil, "Failed to encode picked image")
        }
    }

    public func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
        picker.dismiss(animated: true) { [weak self] in
            guard let self = self, let completion = self.pendingCompletion else { return }
            self.pendingCompletion = nil
            completion(400, nil, "Operation cancelled by user")
        }
    }

    // MARK: - Barcode Scanner (AVFoundation)

    private func startBarcodeScanning(on vc: UIViewController, completion: @escaping (Int, [String: Any]?, String) -> Void) {
        guard let videoCaptureDevice = AVCaptureDevice.default(for: .video) else {
            // 模拟器或无相机设备，自动弹出仿真扫码弹窗
            self.showMockScannerAlert(on: vc, completion: completion)
            return
        }

        do {
            let videoInput = try AVCaptureDeviceInput(device: videoCaptureDevice)
            let session = AVCaptureSession()
            if session.canAddInput(videoInput) {
                session.addInput(videoInput)
            } else {
                completion(500, nil, "Could not add video input to session")
                return
            }

            let metadataOutput = AVCaptureMetadataOutput()
            if session.canAddOutput(metadataOutput) {
                session.addOutput(metadataOutput)
                metadataOutput.setMetadataObjectsDelegate(self, queue: DispatchQueue.main)
                metadataOutput.metadataObjectTypes = [.qr, .ean13, .ean8, .code128]
            } else {
                completion(500, nil, "Could not add metadata output to session")
                return
            }

            self.captureSession = session

            // 创建扫码遮罩视图与关闭按钮
            let overlay = UIView(frame: vc.view.bounds)
            overlay.backgroundColor = .black
            let preview = AVCaptureVideoPreviewLayer(session: session)
            preview.frame = overlay.bounds
            preview.videoGravity = .resizeAspectFill
            overlay.layer.addSublayer(preview)
            self.previewLayer = preview

            let closeBtn = UIButton(type: .system)
            closeBtn.setTitle("关闭", for: .normal)
            closeBtn.setTitleColor(.white, for: .normal)
            closeBtn.titleLabel?.font = .boldSystemFont(ofSize: 17)
            closeBtn.frame = CGRect(x: 20, y: 50, width: 60, height: 40)
            closeBtn.addTarget(self, action: #selector(self.dismissScanner), for: .touchUpInside)
            overlay.addSubview(closeBtn)

            self.scannerContainerView = overlay
            vc.view.addSubview(overlay)

            DispatchQueue.global(qos: .userInitiated).async {
                session.startRunning()
            }

        } catch {
            completion(500, nil, "AVFoundation setup error: \(error.localizedDescription)")
        }
    }

    public func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput metadataObjects: [AVMetadataObject], from connection: AVCaptureConnection) {
        if let metadataObject = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
           let stringValue = metadataObject.stringValue {
            dismissScanner()
            if let completion = pendingCompletion {
                pendingCompletion = nil
                completion(200, [
                    "text": stringValue,
                    "format": metadataObject.type.rawValue
                ], "success")
            }
        }
    }

    @objc private func dismissScanner() {
        captureSession?.stopRunning()
        captureSession = nil
        previewLayer?.removeFromSuperlayer()
        previewLayer = nil
        scannerContainerView?.removeFromSuperview()
        scannerContainerView = nil
    }

    private func showMockScannerAlert(on vc: UIViewController, completion: @escaping (Int, [String: Any]?, String) -> Void) {
        let alert = UIAlertController(title: "📷 二维码扫描 (模拟器调试)", message: "当前处于 iOS 模拟器环境，可直接输入或模拟扫码结果", preferredStyle: .alert)
        alert.addTextField { tf in
            tf.text = "https://mobile-electron.dev/sample-qr?id=ios_sim_98721"
        }
        alert.addAction(UIAlertAction(title: "完成扫描", style: .default, handler: { _ in
            let text = alert.textFields?.first?.text ?? ""
            completion(200, [
                "text": text,
                "format": "QR_CODE"
            ], "success")
        }))
        alert.addAction(UIAlertAction(title: "预设Token", style: .default, handler: { _ in
            completion(200, [
                "text": "IOS_SIMULATOR_AUTH_TOKEN_TEST_9999",
                "format": "CODE_128"
            ], "success")
        }))
        alert.addAction(UIAlertAction(title: "取消", style: .cancel, handler: { _ in
            completion(401, nil, "User cancelled barcode scan")
        }))
        vc.present(alert, animated: true)
    }

    private func scaleImageIfNeeded(_ image: UIImage, maxDimension: CGFloat) -> UIImage {
        let size = image.size
        if size.width <= maxDimension && size.height <= maxDimension {
            return image
        }
        let ratio = min(maxDimension / size.width, maxDimension / size.height)
        let newSize = CGSize(width: size.width * ratio, height: size.height * ratio)
        let renderer = UIGraphicsImageRenderer(size: newSize)
        return renderer.image { _ in
            image.draw(in: CGRect(origin: .zero, size: newSize))
        }
    }
}
