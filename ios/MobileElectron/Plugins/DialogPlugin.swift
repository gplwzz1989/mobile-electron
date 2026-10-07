//
//  DialogPlugin.swift
//  MobileElectron
//
//  Created for Mobile Electron Framework iOS Container.
//

import Foundation
import UIKit

public class DialogPlugin: IBridgePlugin {
    public let moduleName = "dialog"

    public func getRequiredTier(action: String) -> Int {
        return DomainWhitelistManager.TIER_0_PUBLIC
    }

    public func handleAction(
        callingUrl: String,
        action: String,
        params: [String: Any],
        completion: @escaping (Int, [String: Any]?, String) -> Void
    ) {
        DispatchQueue.main.async {
            guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
                  let rootVC = windowScene.windows.first(where: { $0.isKeyWindow })?.rootViewController else {
                completion(500, nil, "RootViewController not available")
                return
            }

            switch action {
            case "alert":
                let title = params["title"] as? String ?? "提示"
                let message = params["message"] as? String ?? ""
                let buttonText = params["buttonText"] as? String ?? "确定"

                let alert = UIAlertController(title: title, message: message, preferredStyle: .alert)
                alert.addAction(UIAlertAction(title: buttonText, style: .default, handler: { _ in
                    completion(200, ["confirmed": true], "success")
                }))
                rootVC.present(alert, animated: true)

            case "confirm":
                let title = params["title"] as? String ?? "请确认"
                let message = params["message"] as? String ?? ""
                let confirmText = params["confirmText"] as? String ?? "确认"
                let cancelText = params["cancelText"] as? String ?? "取消"

                let alert = UIAlertController(title: title, message: message, preferredStyle: .alert)
                alert.addAction(UIAlertAction(title: cancelText, style: .cancel, handler: { _ in
                    completion(200, ["confirmed": false], "success")
                }))
                alert.addAction(UIAlertAction(title: confirmText, style: .default, handler: { _ in
                    completion(200, ["confirmed": true], "success")
                }))
                rootVC.present(alert, animated: true)

            case "prompt":
                let title = params["title"] as? String ?? "请输入"
                let message = params["message"] as? String ?? ""
                let defaultValue = params["defaultValue"] as? String ?? ""
                let placeholder = params["placeholder"] as? String ?? ""
                let confirmText = params["confirmText"] as? String ?? "确定"
                let cancelText = params["cancelText"] as? String ?? "取消"

                let alert = UIAlertController(title: title, message: message, preferredStyle: .alert)
                alert.addTextField { tf in
                    tf.text = defaultValue
                    tf.placeholder = placeholder
                }
                alert.addAction(UIAlertAction(title: cancelText, style: .cancel, handler: { _ in
                    completion(200, ["confirmed": false, "value": NSNull()], "success")
                }))
                alert.addAction(UIAlertAction(title: confirmText, style: .default, handler: { _ in
                    let text = alert.textFields?.first?.text ?? ""
                    completion(200, ["confirmed": true, "value": text], "success")
                }))
                rootVC.present(alert, animated: true)

            default:
                completion(404, nil, "Unknown action '\(action)' in module 'dialog'")
            }
        }
    }
}
