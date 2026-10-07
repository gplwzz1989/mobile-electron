//
//  StoragePlugin.swift
//  MobileElectron
//
//  Created for Mobile Electron Framework iOS Container.
//

import Foundation

public class StoragePlugin: IBridgePlugin {
    public let moduleName = "storage"
    private let suiteName = "MobileElectron_Storage"
    private let defaults: UserDefaults

    public init() {
        self.defaults = UserDefaults(suiteName: suiteName) ?? UserDefaults.standard
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
        switch action {
        case "set":
            guard let key = params["key"] as? String, !key.isEmpty else {
                completion(400, nil, "Missing key parameter")
                return
            }
            let value = params["value"] as? String ?? ""
            defaults.set(value, forKey: key)
            completion(200, ["success": true], "success")

        case "get":
            guard let key = params["key"] as? String, !key.isEmpty else {
                completion(400, nil, "Missing key parameter")
                return
            }
            let val = defaults.string(forKey: key)
            completion(200, [
                "value": val as Any,
                "exists": val != nil
            ], "success")

        case "remove":
            guard let key = params["key"] as? String, !key.isEmpty else {
                completion(400, nil, "Missing key parameter")
                return
            }
            defaults.removeObject(forKey: key)
            completion(200, ["success": true], "success")

        case "clear":
            if let dict = defaults.persistentDomain(forName: suiteName) {
                for k in dict.keys {
                    defaults.removeObject(forKey: k)
                }
            }
            completion(200, ["success": true], "success")

        case "keys":
            let all = defaults.dictionaryRepresentation()
            completion(200, ["keys": Array(all.keys)], "success")

        default:
            completion(404, nil, "Unknown action '\(action)' in module 'storage'")
        }
    }
}
