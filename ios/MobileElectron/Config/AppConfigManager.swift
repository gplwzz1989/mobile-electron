//
//  AppConfigManager.swift
//  MobileElectron
//
//  Created for Mobile Electron Framework iOS Container.
//

import Foundation

/// 工业级全局应用配置与云控中心管理器 (iOS Swift 原生实现)
public final class AppConfigManager {
    public static let shared = AppConfigManager()

    public private(set) var config = AppConfig()
    public private(set) var configSource: String = "builtin_asset" // "builtin_asset" | "remote_cached"

    private let remoteConfigFileName = "remote_app_config.json"

    private var remoteConfigFileURL: URL {
        let documentsDir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        return documentsDir.appendingPathComponent(remoteConfigFileName)
    }

    private init() {}

    public func initialize() {
        // 1. 优先尝试从私有沙箱加载云控缓存配置
        if FileManager.default.fileExists(atPath: remoteConfigFileURL.path) {
            do {
                let data = try Data(contentsOf: remoteConfigFileURL)
                let decoded = try JSONDecoder().decode(AppConfig.self, from: data)
                self.config = decoded
                self.configSource = "remote_cached"
                NSLog("[AppConfigManager] Successfully loaded remote cached app config: %@", decoded.window.defaultUrl)
                applySecurityPolicies()
                return
            } catch {
                NSLog("[AppConfigManager] Failed to load remote cached config: %@, fallback to bundle", error.localizedDescription)
            }
        }

        // 2. 兜底从 App Bundle 加载内置出厂配置
        if let url = Bundle.main.url(forResource: "app-config", withExtension: "json") {
            do {
                let data = try Data(contentsOf: url)
                let decoded = try JSONDecoder().decode(AppConfig.self, from: data)
                self.config = decoded
                self.configSource = "builtin_asset"
                NSLog("[AppConfigManager] Successfully loaded builtin app-config.json: %@", decoded.window.defaultUrl)
            } catch {
                NSLog("[AppConfigManager] Failed to decode app-config.json: %@, fallback to defaults", error.localizedDescription)
            }
        } else {
            NSLog("[AppConfigManager] app-config.json not found in main bundle, using defaults")
        }

        applySecurityPolicies()
    }

    public func fetchRemoteConfig(urlString: String, completion: @escaping (Bool, AppConfig?, String) -> Void) {
        guard let url = URL(string: urlString) else {
            completion(false, nil, "Invalid URL string")
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.timeoutInterval = 8.0
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        let task = URLSession.shared.dataTask(with: request) { [weak self] data, response, error in
            guard let self = self else { return }

            if let error = error {
                completion(false, nil, "Network error: \(error.localizedDescription)")
                return
            }

            guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200, let data = data else {
                let code = (response as? HTTPURLResponse)?.statusCode ?? 500
                completion(false, nil, "HTTP error: \(code)")
                return
            }

            do {
                let newConfig = try JSONDecoder().decode(AppConfig.self, from: data)
                // 保存至本地沙箱
                try data.write(to: self.remoteConfigFileURL, options: .atomic)

                DispatchQueue.main.async {
                    self.config = newConfig
                    self.configSource = "remote_cached"
                    self.applySecurityPolicies()
                    completion(true, newConfig, "remote_cached")
                }
            } catch {
                completion(false, nil, "Config parse error: \(error.localizedDescription)")
            }
        }
        task.resume()
    }

    public func resetToDefault() -> Bool {
        try? FileManager.default.removeItem(at: remoteConfigFileURL)

        if let url = Bundle.main.url(forResource: "app-config", withExtension: "json") {
            do {
                let data = try Data(contentsOf: url)
                let decoded = try JSONDecoder().decode(AppConfig.self, from: data)
                self.config = decoded
                self.configSource = "builtin_asset"
                applySecurityPolicies()
                return true
            } catch {
                NSLog("[AppConfigManager] Reset failed: %@", error.localizedDescription)
            }
        }
        self.config = AppConfig()
        self.configSource = "builtin_asset"
        applySecurityPolicies()
        return true
    }

    public func updateConfig(_ newConfig: AppConfig, persist: Bool = true) {
        self.config = newConfig
        self.applySecurityPolicies()

        if persist {
            if let data = try? JSONEncoder().encode(newConfig) {
                try? data.write(to: remoteConfigFileURL, options: .atomic)
                self.configSource = "remote_cached"
            }
        }
    }

    private func applySecurityPolicies() {
        let whitelist = DomainWhitelistManager.shared
        for domain in config.security.whitelist {
            whitelist.addTier1Domain(domain)
            whitelist.addTier2Domain(domain)
        }
    }
}
