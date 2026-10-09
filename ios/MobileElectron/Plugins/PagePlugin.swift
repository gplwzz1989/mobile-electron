//
//  PagePlugin.swift
//  MobileElectron
//
//  Created for Mobile Electron Framework iOS Container.
//

import Foundation
import WebKit

public class PagePlugin: IBridgePlugin {
    public let moduleName = "page"
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
        switch action {
        case "create":
            let ua = params["userAgent"] as? String
            let profile = params["profile"] as? String ?? params["partition"] as? String ?? "default"
            let initialUrl = params["url"] as? String
            let preset = params["preset"] as? String ?? params["hardwarePreset"] as? String ?? "flagship"
            let hwConfig = HardwareConfig.fromPreset(preset)

            let page = webViewPool.createHeadlessPage(customUA: ua, profileName: profile, hardwareConfig: hwConfig, initialUrl: initialUrl)

            completion(200, [
                "pageId": page.pageId,
                "profile": page.profileName,
                "url": initialUrl ?? "",
                "multiProfileSupported": webViewPool.isMultiProfileSupported(),
                "hardware": page.hardwareConfig.toDictionary()
            ], "success")

        case "bringToFront", "show":
            guard let pageId = params["pageId"] as? String, !pageId.isEmpty else {
                completion(400, nil, "Missing pageId")
                return
            }
            let success = webViewPool.switchToForeground(pageId: pageId)
            if success {
                completion(200, ["success": true, "activePageId": pageId], "success")
            } else {
                completion(404, nil, "Page '\(pageId)' not found")
            }

        case "sendToBack", "hide":
            let success = webViewPool.switchToForeground(pageId: "main")
            completion(200, ["success": success], "success")

        case "list":
            let all = webViewPool.getAllPages()
            let list = all.map { p -> [String: Any] in
                return [
                    "pageId": p.pageId,
                    "url": p.webView.url?.absoluteString ?? "",
                    "title": p.webView.title ?? "",
                    "isHeadless": p.isHeadless,
                    "isForeground": p.isForeground,
                    "profile": p.profileName,
                    "hardware": p.hardwareConfig.toDictionary()
                ]
            }
            completion(200, [
                "pages": list,
                "multiProfileSupported": webViewPool.isMultiProfileSupported()
            ], "success")

        case "listProfiles":
            let profiles = webViewPool.getAllProfileNames()
            completion(200, ["profiles": profiles], "success")

        case "goto":
            guard let pageId = params["pageId"] as? String,
                  let urlStr = params["url"] as? String, !urlStr.isEmpty else {
                completion(400, nil, "Missing pageId or valid url")
                return
            }
            guard let page = webViewPool.getPage(pageId: pageId) else {
                completion(404, nil, "Page not found")
                return
            }
            DispatchQueue.main.async {
                WebViewPool.loadUrl(webView: page.webView, urlString: urlStr)
                completion(200, ["success": true], "success")
            }

        case "evaluate":
            guard let pageId = params["pageId"] as? String,
                  let script = params["script"] as? String else {
                completion(400, nil, "Missing pageId or script")
                return
            }
            guard let page = webViewPool.getPage(pageId: pageId) else {
                completion(404, nil, "Page not found")
                return
            }
            DispatchQueue.main.async {
                page.webView.evaluateJavaScript(script) { res, err in
                    if let err = err {
                        completion(500, nil, "JS error: \(err.localizedDescription)")
                    } else {
                        completion(200, ["result": res ?? ""], "success")
                    }
                }
            }

        case "extractQrCode":
            guard let pageId = params["pageId"] as? String,
                  let page = webViewPool.getPage(pageId: pageId) else {
                completion(404, nil, "Page not found")
                return
            }
            let selector = params["selector"] as? String ?? "img[src*='qr'], img.qrcode, canvas.qrcode"
            let js = """
            (function() {
                var el = document.querySelector('\(selector)');
                if (!el) return '';
                if (el.tagName.toLowerCase() === 'img') return el.src;
                if (el.tagName.toLowerCase() === 'canvas') return el.toDataURL();
                return '';
            })();
            """
            DispatchQueue.main.async {
                page.webView.evaluateJavaScript(js) { res, _ in
                    let base64 = res as? String ?? ""
                    completion(200, ["base64": base64], "success")
                }
            }

        case "close":
            guard let pageId = params["pageId"] as? String else {
                completion(400, nil, "Missing pageId")
                return
            }
            webViewPool.closePage(pageId: pageId)
            completion(200, ["success": true], "success")

        case "getCookies", "getAllCookies":
            guard let pageId = params["pageId"] as? String,
                  let page = webViewPool.getPage(pageId: pageId) else {
                completion(404, nil, "Page not found")
                return
            }

            let isGetAll = (action == "getAllCookies") || (params["all"] as? Bool ?? false)
            let host = page.webView.url?.host?.lowercased()

            page.dataStore.httpCookieStore.getAllCookies { cookies in
                let matched: [HTTPCookie]
                if isGetAll || host == nil {
                    matched = cookies
                } else {
                    let h = host!
                    matched = cookies.filter { c in
                        var cd = c.domain.lowercased()
                        if cd.hasPrefix(".") { cd.removeFirst() }
                        return h == cd || h.hasSuffix("." + cd) || cd.hasSuffix("." + h)
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
                    "profile": page.profileName,
                    "pageId": page.pageId
                ], "success")
            }

        case "setCookies":
            guard let pageId = params["pageId"] as? String,
                  let page = webViewPool.getPage(pageId: pageId) else {
                completion(404, nil, "Page not found")
                return
            }

            let currentHost = page.webView.url?.host ?? "douyin.com"
            let cookieStore = page.dataStore.httpCookieStore
            let isHttps = (page.webView.url?.scheme?.lowercased() == "https")
            let group = DispatchGroup()
            var count = 0

            if let arr = (params["cookies"] as? [[String: Any]]) ?? (params["items"] as? [[String: Any]]) {
                for item in arr {
                    guard let name = item["name"] as? String, !name.isEmpty,
                          let val = item["value"] as? String else { continue }

                    let domain = (item["domain"] as? String) ?? currentHost
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
                    if secure { props[.secure] = "TRUE" }
                    if httpOnly { props[HTTPCookiePropertyKey("HttpOnly")] = "TRUE" }

                    if let c = HTTPCookie(properties: props) {
                        group.enter()
                        cookieStore.setCookie(c) {
                            count += 1
                            group.leave()
                        }
                    }
                }
            } else if let rawInput = (params["cookies"] as? String) ?? (params["input"] as? String) {
                let pairs = rawInput.components(separatedBy: ";")
                for pair in pairs {
                    let parts = pair.trimmingCharacters(in: .whitespaces).split(separator: "=", maxSplits: 1).map(String.init)
                    if parts.count == 2 {
                        let props: [HTTPCookiePropertyKey: Any] = [
                            .name: parts[0],
                            .value: parts[1],
                            .domain: currentHost,
                            .path: "/"
                        ]
                        if let c = HTTPCookie(properties: props) {
                            group.enter()
                            cookieStore.setCookie(c) {
                                count += 1
                                group.leave()
                            }
                        }
                    }
                }
            }

            group.notify(queue: .main) {
                completion(200, ["success": true, "count": count, "profile": page.profileName, "pageId": page.pageId], "success")
            }

        case "getLocalStorage":
            guard let pageId = params["pageId"] as? String,
                  let page = webViewPool.getPage(pageId: pageId) else {
                completion(404, nil, "Page not found")
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
            guard let pageId = params["pageId"] as? String,
                  let page = webViewPool.getPage(pageId: pageId) else {
                completion(404, nil, "Page not found")
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
            guard let pageId = params["pageId"] as? String,
                  let page = webViewPool.getPage(pageId: pageId) else {
                completion(404, nil, "Page not found")
                return
            }
            DispatchQueue.main.async {
                page.webView.evaluateJavaScript("localStorage.clear(); 'true';") { _, _ in
                    completion(200, ["success": true, "pageId": page.pageId], "success")
                }
            }

        case "dumpStorage":
            guard let pageId = params["pageId"] as? String,
                  let page = webViewPool.getPage(pageId: pageId) else {
                completion(404, nil, "Page not found")
                return
            }

            let profileName = page.profileName
            let cookieStore = page.dataStore.httpCookieStore
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

        case "startDouyinQrLogin":
            _ = webViewPool.createHeadlessPage(
                customUA: "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15",
                profileName: "douyin_session",
                initialUrl: "https://creator.douyin.com"
            )
            completion(200, ["success": true, "message": "Douyin QR login task initiated"], "success")

        default:
            completion(404, nil, "Unknown action '\(action)' in module 'page'")
        }
    }
}
