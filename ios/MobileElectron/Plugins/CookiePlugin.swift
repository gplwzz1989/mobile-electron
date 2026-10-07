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

    public func handleAction(
        callingUrl: String,
        action: String,
        params: [String: Any],
        completion: @escaping (Int, [String: Any]?, String) -> Void
    ) {
        let profileName = params["profile"] as? String ?? "default"
        let dataStore = webViewPool.getOrCreateDataStore(profileName: profileName)
        let cookieStore = dataStore.httpCookieStore

        switch action {
        case "get":
            let urlStr = params["url"] as? String ?? callingUrl
            guard let url = URL(string: urlStr), let host = url.host?.lowercased() else {
                completion(400, nil, "Invalid URL")
                return
            }

            cookieStore.getAllCookies { cookies in
                let matched = cookies.filter { cookie in
                    let cDomain = cookie.domain.lowercased()
                    return host == cDomain || host.hasSuffix("." + cDomain) || cDomain.hasSuffix(host)
                }
                let formatted = matched.map { "\($0.name)=\($0.value)" }.joined(separator: "; ")
                completion(200, ["cookies": formatted, "profile": profileName], "success")
            }

        case "import", "set":
            guard let urlStr = params["url"] as? String ?? (params["input"] as? String != nil ? callingUrl : nil),
                  let url = URL(string: urlStr),
                  let host = url.host else {
                completion(400, nil, "Invalid target URL")
                return
            }

            let rawInput = params["input"] as? String ?? ""
            if rawInput.isEmpty {
                completion(400, nil, "Empty cookie input")
                return
            }

            let pairs = rawInput.components(separatedBy: ";")
            let group = DispatchGroup()
            var count = 0

            for rawPair in pairs {
                let trimmed = rawPair.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !trimmed.isEmpty else { continue }
                let parts = trimmed.split(separator: "=", maxSplits: 1).map(String.init)
                guard parts.count == 2 else { continue }

                var props: [HTTPCookiePropertyKey: Any] = [
                    .name: parts[0].trimmingCharacters(in: .whitespaces),
                    .value: parts[1].trimmingCharacters(in: .whitespaces),
                    .domain: host,
                    .path: "/",
                    .expires: Date().addingTimeInterval(86400 * 365)
                ]
                if url.scheme?.lowercased() == "https" {
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

            group.notify(queue: .main) {
                completion(200, ["count": count, "profile": profileName], "success")
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
