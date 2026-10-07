//
//  CookiePlugin.swift
//  MobileElectron
//
//  Created for Mobile Electron Framework iOS Container.
//

import Foundation
import WebKit

public class CookiePlugin: IBridgePlugin {
    public let moduleName = "cookie"
    private let webViewPool: WebViewPool

    public init(webViewPool: WebViewPool = WebViewPool.shared) {
        self.webViewPool = webViewPool
    }

    public func getRequiredTier(action: String) -> Int {
        return DomainWhitelistManager.TIER_2_CORE
    }

    private func resolveProfileName(params: [String: Any]) -> String {
        if let pageId = params["pageId"] as? String,
           let page = webViewPool.getPage(pageId: pageId) {
            return page.profileName
        }
        if let profile = params["profile"] as? String ?? params["partition"] as? String {
            return profile
        }
        if let active = webViewPool.getActiveForegroundPage() {
            return active.profileName
        }
        return "default"
    }

    public func handleAction(
        callingUrl: String,
        action: String,
        params: [String: Any],
        completion: @escaping (Int, [String: Any]?, String) -> Void
    ) {
        let profileName = resolveProfileName(params: params)
        let dataStore = webViewPool.getOrCreateDataStore(profileName: profileName)
        let cookieStore = dataStore.httpCookieStore

        switch action {
        case "get", "getAll":
            let isGetAll = (action == "getAll") || (params["all"] as? Bool ?? false)
            var targetHost: String? = nil

            if let urlStr = params["url"] as? String, let url = URL(string: urlStr), let h = url.host {
                targetHost = h.lowercased()
            } else if let domain = params["domain"] as? String, !domain.isEmpty {
                targetHost = domain.lowercased().trimmingCharacters(in: CharacterSet(charactersIn: "."))
            } else if let pageId = params["pageId"] as? String,
                      let page = webViewPool.getPage(pageId: pageId),
                      let pageUrl = page.webView.url,
                      let h = pageUrl.host {
                targetHost = h.lowercased()
            } else if !isGetAll, let url = URL(string: callingUrl), let h = url.host {
                targetHost = h.lowercased()
            }

            cookieStore.getAllCookies { cookies in
                let matched: [HTTPCookie]
                if isGetAll || targetHost == nil {
                    matched = cookies
                } else {
                    let host = targetHost!
                    matched = cookies.filter { cookie in
                        var cDomain = cookie.domain.lowercased()
                        if cDomain.hasPrefix(".") { cDomain.removeFirst() }
                        return host == cDomain || host.hasSuffix("." + cDomain) || cDomain.hasSuffix("." + host)
                    }
                }

                let details: [[String: Any]] = matched.map { c in
                    var dict: [String: Any] = [
                        "name": c.name,
                        "value": c.value,
                        "domain": c.domain,
                        "path": c.path,
                        "httpOnly": c.isHTTPOnly,
                        "secure": c.isSecure
                    ]
                    if let exp = c.expiresDate {
                        dict["expires"] = Int64(exp.timeIntervalSince1970)
                    } else {
                        dict["expires"] = 0
                    }
                    return dict
                }

                let formatted = matched.map { "\($0.name)=\($0.value)" }.joined(separator: "; ")
                completion(200, [
                    "cookies": formatted,
                    "details": details,
                    "count": details.count,
                    "profile": profileName
                ], "success")
            }

        case "import", "set":
            var defaultHost = ""
            var isHttps = true
            if let urlStr = params["url"] as? String, let url = URL(string: urlStr), let h = url.host {
                defaultHost = h
                isHttps = (url.scheme?.lowercased() == "https")
            } else if let pageId = params["pageId"] as? String,
                      let page = webViewPool.getPage(pageId: pageId),
                      let pageUrl = page.webView.url,
                      let h = pageUrl.host {
                defaultHost = h
                isHttps = (pageUrl.scheme?.lowercased() == "https")
            } else if let url = URL(string: callingUrl), let h = url.host {
                defaultHost = h
                isHttps = (url.scheme?.lowercased() == "https")
            }

            let group = DispatchGroup()
            var count = 0

            // 1. 支持传入字典结构体数组 (含 httpOnly、domain、path、expires 等)
            if let arr = (params["cookies"] as? [[String: Any]]) ?? (params["items"] as? [[String: Any]]) {
                for item in arr {
                    guard let name = item["name"] as? String, !name.isEmpty,
                          let val = item["value"] as? String else { continue }

                    let domain = (item["domain"] as? String) ?? defaultHost
                    let path = (item["path"] as? String) ?? "/"
                    let secure = (item["secure"] as? Bool) ?? isHttps
                    let httpOnly = (item["httpOnly"] as? Bool) ?? false

                    var props: [HTTPCookiePropertyKey: Any] = [
                        .name: name,
                        .value: val,
                        .domain: domain,
                        .path: path
                    ]
                    if let expSec = item["expires"] as? Int64, expSec > 0 {
                        props[.expires] = Date(timeIntervalSince1970: TimeInterval(expSec))
                    } else {
                        props[.expires] = Date().addingTimeInterval(86400 * 365)
                    }
                    if secure {
                        props[.secure] = "TRUE"
                    }
                    if httpOnly {
                        props[HTTPCookiePropertyKey("HttpOnly")] = "TRUE"
                    }

                    if let cookie = HTTPCookie(properties: props) {
                        group.enter()
                        cookieStore.setCookie(cookie) {
                            count += 1
                            group.leave()
                        }
                    }
                }
            } else {
                // 2. 支持传入字符串文本
                let rawInput = (params["input"] as? String) ?? (params["cookies"] as? String) ?? ""
                if rawInput.isEmpty {
                    completion(400, nil, "Empty cookie input")
                    return
                }

                let pairs = rawInput.components(separatedBy: ";")
                for rawPair in pairs {
                    let trimmed = rawPair.trimmingCharacters(in: .whitespacesAndNewlines)
                    guard !trimmed.isEmpty else { continue }
                    let parts = trimmed.split(separator: "=", maxSplits: 1).map(String.init)
                    guard parts.count == 2 else { continue }

                    var props: [HTTPCookiePropertyKey: Any] = [
                        .name: parts[0].trimmingCharacters(in: .whitespaces),
                        .value: parts[1].trimmingCharacters(in: .whitespaces),
                        .domain: defaultHost,
                        .path: "/",
                        .expires: Date().addingTimeInterval(86400 * 365)
                    ]
                    if isHttps {
                        props[.secure] = "TRUE"
                    }

                    if let cookie = HTTPCookie(properties: props) {
                        group.enter()
                        cookieStore.setCookie(cookie) {
                            count += 1
                            group.leave()
                        }
                    }
                }
            }

            group.notify(queue: .main) {
                completion(200, ["success": true, "count": count, "profile": profileName], "success")
            }

        case "clear":
            cookieStore.getAllCookies { cookies in
                let group = DispatchGroup()
                for c in cookies {
                    group.enter()
                    cookieStore.delete(c) {
                        group.leave()
                    }
                }
                group.notify(queue: .main) {
                    completion(200, ["success": true, "profile": profileName], "success")
                }
            }

        default:
            completion(404, nil, "Unknown action '\(action)' in module 'cookie'")
        }
    }
}
