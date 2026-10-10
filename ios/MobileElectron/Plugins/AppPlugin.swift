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
        if action == "getInfo" || action == "getConfigStatus" { return DomainWhitelistManager.TIER_0_PUBLIC }
        return DomainWhitelistManager.TIER_1_BUSINESS
    }

    public func handleAction(
        callingUrl: String,
        action: String,
        params: [String: Any],
        completion: @escaping (Int, [String: Any]?, String) -> Void
    ) {
        let mgr = AppConfigManager.shared
        let config = mgr.config

        switch action {
        case "getInfo":
            let info: [String: Any] = [
                "appName": config.app.name,
                "appId": config.app.appId,
                "version": config.app.version,
                "framework": "MobileElectron-iOS",
                "frameworkVersion": "2.0.0",
                "multiProfileSupported": WebViewPool.shared.isMultiProfileSupported(),
                "defaultUrl": config.window.defaultUrl,
                "source": mgr.configSource
            ]
            completion(200, info, "success")

        case "getConfig":
            if let data = try? JSONEncoder().encode(config),
               let dict = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                completion(200, dict, "success")
            } else {
                completion(200, [:], "success")
            }

        case "getConfigStatus":
            let res: [String: Any] = [
                "source": mgr.configSource,
                "defaultUrl": config.window.defaultUrl,
                "whitelistCount": config.security.whitelist.count,
                "version": config.app.version
            ]
            completion(200, res, "success")

        case "fetchRemoteConfig":
            guard let url = params["url"] as? String, !url.trimmingCharacters(in: .whitespaces).isEmpty else {
                completion(400, nil, "Missing remote config URL")
                return
            }
            mgr.fetchRemoteConfig(urlString: url) { success, newConfig, source in
                if success, let newConfig = newConfig,
                   let data = try? JSONEncoder().encode(newConfig),
                   let dict = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                    completion(200, [
                        "success": true,
                        "source": source,
                        "config": dict
                    ], "success")
                } else {
                    completion(500, nil, source)
                }
            }

        case "applyRemoteConfig":
            guard let configDict = params["config"] as? [String: Any],
                  let data = try? JSONSerialization.data(withJSONObject: configDict),
                  let newConfig = try? JSONDecoder().decode(AppConfig.self, from: data) else {
                completion(400, nil, "Invalid config object payload")
                return
            }
            let persist = params["persist"] as? Bool ?? true
            mgr.updateConfig(newConfig, persist: persist)
            completion(200, [
                "success": true,
                "applied": true,
                "config": configDict
            ], "success")

        case "resetRemoteConfig":
            let ok = mgr.resetToDefault()
            completion(200, [
                "success": ok,
                "source": mgr.configSource
            ], "success")

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
