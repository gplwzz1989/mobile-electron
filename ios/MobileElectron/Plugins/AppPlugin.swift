//
//  AppPlugin.swift
//  MobileElectron
//
//  Created for Mobile Electron Framework iOS Container.
//

import Foundation
import UIKit

public class AppPlugin: IBridgePlugin {
    public let moduleName = "app"

    public func getRequiredTier(action: String) -> Int {
        if action == "getInfo" { return DomainWhitelistManager.TIER_0_PUBLIC }
        return DomainWhitelistManager.TIER_1_BUSINESS
    }

    public func handleAction(
        callingUrl: String,
        action: String,
        params: [String: Any],
        completion: @escaping (Int, [String: Any]?, String) -> Void
    ) {
        let config = AppConfigManager.shared.config

        switch action {
        case "getInfo":
            let info: [String: Any] = [
                "appName": config.app.name,
                "appId": config.app.appId,
                "version": config.app.version,
                "framework": "MobileElectron-iOS",
                "frameworkVersion": "2.0.0",
                "multiProfileSupported": WebViewPool.shared.isMultiProfileSupported(),
                "defaultUrl": config.window.defaultUrl
            ]
            completion(200, info, "success")

        case "getConfig":
            if let data = try? JSONEncoder().encode(config),
               let dict = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                completion(200, dict, "success")
            } else {
                completion(200, [:], "success")
            }

        case "exit":
            DispatchQueue.main.async {
                exit(0)
            }
            completion(200, ["success": true], "success")

        default:
            completion(404, nil, "Unknown action '\(action)' in module 'app'")
        }
    }
}
