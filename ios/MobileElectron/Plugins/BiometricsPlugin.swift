//
//  BiometricsPlugin.swift
//  MobileElectron
//
//  Created for Mobile Electron Framework iOS Container.
//

import Foundation
import LocalAuthentication

public class BiometricsPlugin: IBridgePlugin {
    public let moduleName = "biometrics"

    public init() {}

    public func getRequiredTier(action: String) -> Int {
        if action.caseInsensitiveCompare("isAvailable") == .orderedSame {
            return DomainWhitelistManager.TIER_0_PUBLIC
        }
        return DomainWhitelistManager.TIER_1_BUSINESS
    }

    public func handleAction(
        callingUrl: String,
        action: String,
        params: [String: Any],
        completion: @escaping (_ code: Int, _ data: [String: Any]?, _ message: String) -> Void
    ) {
        if action.caseInsensitiveCompare("isAvailable") == .orderedSame {
            let context = LAContext()
            var error: NSError?
            let canEvaluate = context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error)

            var biometricType = "none"
            if #available(iOS 11.0, *) {
                switch context.biometryType {
                case .faceID:
                    biometricType = "face"
                case .touchID:
                    biometricType = "fingerprint"
                default:
                    biometricType = "none"
                }
            }

            let res: [String: Any] = [
                "available": canEvaluate,
                "supported": canEvaluate || (error?.code != LAError.biometryNotAvailable.rawValue),
                "deviceSecure": canEvaluate,
                "hasFingerprint": biometricType == "fingerprint",
                "hasFace": biometricType == "face",
                "biometricType": biometricType
            ]
            completion(200, res, "success")
            return
        }

        if action.caseInsensitiveCompare("authenticate") == .orderedSame {
            let context = LAContext()
            let reason = (params["description"] as? String)
                ?? (params["title"] as? String)
                ?? "请验证 Face ID 或 Touch ID 以确认身份"
            let cancelText = params["cancelText"] as? String

            if let cancelText = cancelText, !cancelText.isEmpty {
                context.localizedCancelTitle = cancelText
            }

            var error: NSError?
            guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) else {
                completion(501, nil, error?.localizedDescription ?? "设备不支持或未录入生物识别特征")
                return
            }

            context.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: reason) { success, evalError in
                if success {
                    completion(200, ["success": true, "authenticated": true], "success")
                } else {
                    let code = (evalError as? LAError)?.code.rawValue ?? 1001
                    completion(code, nil, evalError?.localizedDescription ?? "认证失败或已取消")
                }
            }
            return
        }

        completion(404, nil, "BiometricsPlugin: 未知动作 \(action)")
    }
}
