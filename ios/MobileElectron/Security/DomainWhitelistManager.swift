//
//  DomainWhitelistManager.swift
//  MobileElectron
//
//  Created for Mobile Electron Framework iOS Container.
//

import Foundation

public final class DomainWhitelistManager {
    public static let shared = DomainWhitelistManager()

    public static let TIER_0_PUBLIC = 0
    public static let TIER_1_BUSINESS = 1
    public static let TIER_2_CORE = 2

    private var tier1Whitelist = Set<String>()
    private var tier2Whitelist = Set<String>()
    private var strictHttps: Bool = true
    private let lock = NSLock()

    private init() {
        // 默认预置基础白名单
        addTier1Domain("*.baidu.com")
        addTier1Domain("*.douyin.com")
        addTier1Domain("*.amemv.com")
        addTier1Domain("*.github.com")
        addTier1Domain("localhost")
        addTier1Domain("127.0.0.1")

        addTier2Domain("*.baidu.com")
        addTier2Domain("*.douyin.com")
        addTier2Domain("*.amemv.com")
        addTier2Domain("localhost")
        addTier2Domain("127.0.0.1")
    }

    public func addTier1Domain(_ pattern: String) {
        lock.lock()
        defer { lock.unlock() }
        tier1Whitelist.insert(pattern.lowercased())
    }

    public func addTier2Domain(_ pattern: String) {
        lock.lock()
        defer { lock.unlock() }
        tier2Whitelist.insert(pattern.lowercased())
    }

    public func checkPermission(url: String, requiredTier: Int) -> Bool {
        if requiredTier == Self.TIER_0_PUBLIC { return true }
        guard let urlObj = URL(string: url) else { return false }

        // 本地 Bundle / 离线资源包文件协议直接放行
        if urlObj.scheme?.lowercased() == "file" {
            return true
        }

        guard let host = urlObj.host?.lowercased() else {
            return false
        }

        if strictHttps && host != "localhost" && host != "127.0.0.1" {
            if urlObj.scheme?.lowercased() != "https" {
                return false
            }
        }

        lock.lock()
        defer { lock.unlock() }

        if requiredTier == Self.TIER_1_BUSINESS {
            return matchesAny(host: host, set: tier1Whitelist) || matchesAny(host: host, set: tier2Whitelist)
        }
        if requiredTier == Self.TIER_2_CORE {
            return matchesAny(host: host, set: tier2Whitelist)
        }
        return false
    }

    private func matchesAny(host: String, set: Set<String>) -> Bool {
        for rule in set {
            if rule == host { return true }
            if rule.hasPrefix("*.") {
                let root = String(rule.dropFirst(2))
                if host == root || host.hasSuffix("." + root) {
                    return true
                }
            }
        }
        return false
    }
}
