//
//  DevicePlugin.swift
//  MobileElectron
//
//  Created for Mobile Electron Framework iOS Container.
//

import Foundation
import UIKit

public class DevicePlugin: IBridgePlugin {
    public let moduleName = "device"

    public func getRequiredTier(action: String) -> Int {
        if action == "toast" { return DomainWhitelistManager.TIER_0_PUBLIC }
        return DomainWhitelistManager.TIER_1_BUSINESS
    }

    public func handleAction(
        callingUrl: String,
        action: String,
        params: [String: Any],
        completion: @escaping (Int, [String: Any]?, String) -> Void
    ) {
        switch action {
        case "toast":
            let message = params["message"] as? String ?? ""
            DispatchQueue.main.async {
                self.showToast(message: message)
            }
            completion(200, [:], "success")

        case "setClipboard":
            let text = params["text"] as? String ?? ""
            DispatchQueue.main.async {
                UIPasteboard.general.string = text
            }
            completion(200, ["success": true], "success")

        case "getClipboard":
            DispatchQueue.main.async {
                let text = UIPasteboard.general.string ?? ""
                completion(200, ["text": text], "success")
            }

        default:
            completion(404, nil, "Unknown action '\(action)' in module 'device'")
        }
    }

    private func showToast(message: String) {
        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let rootVC = windowScene.windows.first(where: { $0.isKeyWindow })?.rootViewController else {
            return
        }

        let alert = UIAlertController(title: nil, message: message, preferredStyle: .alert)
        rootVC.present(alert, animated: true)

        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            alert.dismiss(animated: true)
        }
    }
}
