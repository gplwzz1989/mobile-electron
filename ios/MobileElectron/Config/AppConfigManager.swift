//
//  AppConfigManager.swift
//  MobileElectron
//
//  Created for Mobile Electron Framework iOS Container.
//

import Foundation

public final class AppConfigManager {
    public static let shared = AppConfigManager()

    public private(set) var config = AppConfig()

    private init() {}

    public func initialize() {
        if let url = Bundle.main.url(forResource: "app-config", withExtension: "json") {
            do {
                let data = try Data(contentsOf: url)
                let decoded = try JSONDecoder().decode(AppConfig.self, from: data)
                self.config = decoded
                NSLog("[AppConfigManager] Successfully loaded app-config.json: %@", decoded.window.defaultUrl)
            } catch {
                NSLog("[AppConfigManager] Failed to decode app-config.json: %@, fallback to defaults", error.localizedDescription)
            }
        } else {
            NSLog("[AppConfigManager] app-config.json not found in main bundle, using defaults")
        }

        applySecurityPolicies()
    }

    private func applySecurityPolicies() {
        let whitelist = DomainWhitelistManager.shared
        for domain in config.security.whitelist {
            whitelist.addTier1Domain(domain)
            whitelist.addTier2Domain(domain)
        }
    }
}
