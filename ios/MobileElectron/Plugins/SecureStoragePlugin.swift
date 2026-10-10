//
//  SecureStoragePlugin.swift
//  MobileElectron
//
//  Created for Mobile Electron Framework iOS Container.
//

import Foundation
import Security

/// 工业级硬件安全密钥库存储插件 (Keychain Services)
/// 零第三方依赖，数据受到 iOS Secure Enclave 硬件与系统沙盒加密隔离
public class SecureStoragePlugin: IBridgePlugin {
    public let moduleName = "secureStorage"
    private let serviceName = "com.mobileelectron.vault"

    public init() {}

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
        case "setItem":
            guard let key = params["key"] as? String, !key.isEmpty else {
                completion(400, nil, "Missing key parameter")
                return
            }
            let value = params["value"] as? String ?? ""
            let data = value.data(using: .utf8) ?? Data()

            // 先尝试删除旧数据
            let deleteQuery: [String: Any] = [
                kSecClass as String: kSecClassGenericPassword,
                kSecAttrService as String: serviceName,
                kSecAttrAccount as String: key
            ]
            SecItemDelete(deleteQuery as CFDictionary)

            // 写入新数据
            let addQuery: [String: Any] = [
                kSecClass as String: kSecClassGenericPassword,
                kSecAttrService as String: serviceName,
                kSecAttrAccount as String: key,
                kSecValueData as String: data,
                kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock
            ]
            let status = SecItemAdd(addQuery as CFDictionary, nil)
            if status == errSecSuccess {
                completion(200, ["success": true], "success")
            } else {
                completion(500, nil, "Keychain write error (OSStatus: \(status))")
            }

        case "getItem":
            guard let key = params["key"] as? String, !key.isEmpty else {
                completion(400, nil, "Missing key parameter")
                return
            }
            let query: [String: Any] = [
                kSecClass as String: kSecClassGenericPassword,
                kSecAttrService as String: serviceName,
                kSecAttrAccount as String: key,
                kSecReturnData as String: true,
                kSecMatchLimit as String: kSecMatchLimitOne
            ]
            var item: CFTypeRef?
            let status = SecItemCopyMatching(query as CFDictionary, &item)
            if status == errSecSuccess, let data = item as? Data, let str = String(data: data, encoding: .utf8) {
                completion(200, ["value": str, "exists": true], "success")
            } else if status == errSecItemNotFound {
                completion(200, ["value": NSNull(), "exists": false], "success")
            } else {
                completion(500, nil, "Keychain read error (OSStatus: \(status))")
            }

        case "removeItem":
            guard let key = params["key"] as? String, !key.isEmpty else {
                completion(400, nil, "Missing key parameter")
                return
            }
            let query: [String: Any] = [
                kSecClass as String: kSecClassGenericPassword,
                kSecAttrService as String: serviceName,
                kSecAttrAccount as String: key
            ]
            let status = SecItemDelete(query as CFDictionary)
            if status == errSecSuccess || status == errSecItemNotFound {
                completion(200, ["success": true], "success")
            } else {
                completion(500, nil, "Keychain delete error (OSStatus: \(status))")
            }

        case "clear":
            let query: [String: Any] = [
                kSecClass as String: kSecClassGenericPassword,
                kSecAttrService as String: serviceName
            ]
            let status = SecItemDelete(query as CFDictionary)
            if status == errSecSuccess || status == errSecItemNotFound {
                completion(200, ["success": true], "success")
            } else {
                completion(500, nil, "Keychain clear error (OSStatus: \(status))")
            }

        case "getAllKeys":
            let query: [String: Any] = [
                kSecClass as String: kSecClassGenericPassword,
                kSecAttrService as String: serviceName,
                kSecReturnAttributes as String: true,
                kSecMatchLimit as String: kSecMatchLimitAll
            ]
            var item: CFTypeRef?
            let status = SecItemCopyMatching(query as CFDictionary, &item)
            var keys: [String] = []
            if status == errSecSuccess, let list = item as? [[String: Any]] {
                for attr in list {
                    if let account = attr[kSecAttrAccount as String] as? String {
                        keys.append(account)
                    }
                }
            }
            completion(200, ["keys": keys], "success")

        default:
            completion(404, nil, "Unknown action: \(action)")
        }
    }
}
