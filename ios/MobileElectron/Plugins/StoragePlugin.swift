//
//  StoragePlugin.swift
//  MobileElectron
//
//  Created for Mobile Electron Framework iOS Container.
//

import Foundation
import WebKit

public class StoragePlugin: IBridgePlugin {
    public let moduleName = "storage"
    private let suiteName = "MobileElectron_Storage"
    private let defaults: UserDefaults
    private let webViewPool: WebViewPool

    public init(webViewPool: WebViewPool = WebViewPool.shared) {
        self.defaults = UserDefaults(suiteName: suiteName) ?? UserDefaults.standard
        self.webViewPool = webViewPool
    }

    public func getRequiredTier(action: String) -> Int {
        return DomainWhitelistManager.TIER_1_BUSINESS
    }

    private func resolveTargetPage(params: [String: Any]) -> WebViewPool.ManagedPage? {
        if let pageId = params["pageId"] as? String {
            return webViewPool.getPage(pageId: pageId)
        }
        return webViewPool.getActiveForegroundPage()
    }

    public func handleAction(
        callingUrl: String,
        action: String,
        params: [String: Any],
        completion: @escaping (Int, [String: Any]?, String) -> Void
    ) {
        switch action {
        // ==================== 原生 UserDefaults 存储 ====================
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

        // ==================== WebView LocalStorage 读写 ====================
        case "getLocalStorage":
            guard let page = resolveTargetPage(params: params) else {
                completion(404, nil, "Target WebView page not found")
                return
            }

            DispatchQueue.main.async {
                let script = """
                (function() {
                    try {
                        var res = {};
                        for (var i = 0; i < localStorage.length; i++) {
                            var k = localStorage.key(i);
                            if (k !== null) res[k] = localStorage.getItem(k);
                        }
                        return JSON.stringify({ success: true, data: res });
                    } catch(e) {
                        return JSON.stringify({ success: false, error: String(e) });
                    }
                })();
                """
                page.webView.evaluateJavaScript(script) { result, error in
                    guard error == nil, let jsonStr = result as? String,
                          let data = jsonStr.data(using: .utf8),
                          let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                          let success = json["success"] as? Bool, success else {
                        completion(500, nil, "Failed to read localStorage: \(error?.localizedDescription ?? "unknown")")
                        return
                    }

                    let items = json["data"] as? [String: Any] ?? [:]
                    completion(200, [
                        "data": items,
                        "count": items.count,
                        "pageId": page.pageId,
                        "url": page.webView.url?.absoluteString ?? ""
                    ], "success")
                }
            }

        case "setLocalStorage":
            guard let page = resolveTargetPage(params: params) else {
                completion(404, nil, "Target WebView page not found")
                return
            }

            var toSet: [String: Any] = (params["data"] as? [String: Any]) ?? (params["items"] as? [String: Any]) ?? [:]
            if toSet.isEmpty, let key = params["key"] as? String, let val = params["value"] {
                toSet[key] = val
            }

            guard !toSet.isEmpty,
                  let jsonData = try? JSONSerialization.data(withJSONObject: toSet),
                  let jsonString = String(data: jsonData, encoding: .utf8) else {
                completion(400, nil, "Invalid data to set in localStorage")
                return
            }

            DispatchQueue.main.async {
                let escaped = jsonString.replacingOccurrences(of: "\\", with: "\\\\")
                    .replacingOccurrences(of: "`", with: "\\`")
                    .replacingOccurrences(of: "$", with: "\\$")
                let script = """
                (function() {
                    try {
                        var items = JSON.parse(`\(escaped)`);
                        for (var k in items) {
                            if (Object.prototype.hasOwnProperty.call(items, k)) {
                                localStorage.setItem(k, items[k]);
                            }
                        }
                        return JSON.stringify({ success: true, count: Object.keys(items).length });
                    } catch(e) {
                        return JSON.stringify({ success: false, error: String(e) });
                    }
                })();
                """
                page.webView.evaluateJavaScript(script) { result, error in
                    if let err = error {
                        completion(500, nil, "Failed to set localStorage: \(err.localizedDescription)")
                    } else {
                        completion(200, ["success": true, "pageId": page.pageId], "success")
                    }
                }
            }

        case "clearLocalStorage":
            guard let page = resolveTargetPage(params: params) else {
                completion(404, nil, "Target WebView page not found")
                return
            }
            DispatchQueue.main.async {
                page.webView.evaluateJavaScript("localStorage.clear(); 'true';") { _, _ in
                    completion(200, ["success": true, "pageId": page.pageId], "success")
                }
            }

        // ==================== 一键导出全量存储 (Cookies含HttpOnly + LocalStorage) ====================
        case "dumpStorage":
            guard let page = resolveTargetPage(params: params) else {
                completion(404, nil, "Target WebView page not found")
                return
            }

            let profileName = page.profileName
            let dataStore = webViewPool.getOrCreateDataStore(profileName: profileName)
            let cookieStore = dataStore.httpCookieStore
            let pageUrl = page.webView.url?.absoluteString ?? ""
            let host = page.webView.url?.host?.lowercased()

            cookieStore.getAllCookies { cookies in
                let matched: [HTTPCookie]
                if let h = host {
                    matched = cookies.filter { c in
                        var cd = c.domain.lowercased()
                        if cd.hasPrefix(".") { cd.removeFirst() }
                        return h == cd || h.hasSuffix("." + cd) || cd.hasSuffix("." + h)
                    }
                } else {
                    matched = cookies
                }

                let cookieDetails: [[String: Any]] = matched.map { c in
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
                let cookieString = matched.map { "\($0.name)=\($0.value)" }.joined(separator: "; ")

                DispatchQueue.main.async {
                    let script = """
                    (function() {
                        var ls = {}, ss = {};
                        try {
                            for (var i = 0; i < localStorage.length; i++) {
                                var k = localStorage.key(i);
                                if (k !== null) ls[k] = localStorage.getItem(k);
                            }
                        } catch(e) {}
                        try {
                            for (var j = 0; j < sessionStorage.length; j++) {
                                var sk = sessionStorage.key(j);
                                if (sk !== null) ss[sk] = sessionStorage.getItem(sk);
                            }
                        } catch(e) {}
                        return JSON.stringify({ localStorage: ls, sessionStorage: ss });
                    })();
                    """
                    page.webView.evaluateJavaScript(script) { result, _ in
                        var lsObj: [String: Any] = [:]
                        var ssObj: [String: Any] = [:]
                        if let jsonStr = result as? String,
                           let data = jsonStr.data(using: .utf8),
                           let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                            lsObj = json["localStorage"] as? [String: Any] ?? [:]
                            ssObj = json["sessionStorage"] as? [String: Any] ?? [:]
                        }

                        completion(200, [
                            "pageId": page.pageId,
                            "profile": profileName,
                            "url": pageUrl,
                            "cookies": cookieDetails,
                            "cookieString": cookieString,
                            "localStorage": lsObj,
                            "sessionStorage": ssObj
                        ], "success")
                    }
                }
            }

        default:
            completion(404, nil, "Unknown action '\(action)' in module 'storage'")
        }
    }
}
