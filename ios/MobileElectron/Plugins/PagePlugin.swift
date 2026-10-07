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
                  let urlStr = params["url"] as? String,
                  let url = URL(string: urlStr) else {
                completion(400, nil, "Missing pageId or valid url")
                return
            }
            guard let page = webViewPool.getPage(pageId: pageId) else {
                completion(404, nil, "Page not found")
                return
            }
            DispatchQueue.main.async {
                page.webView.load(URLRequest(url: url))
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

        case "getCookies":
            guard let pageId = params["pageId"] as? String,
                  let page = webViewPool.getPage(pageId: pageId) else {
                completion(404, nil, "Page not found")
                return
            }
            page.dataStore.httpCookieStore.getAllCookies { cookies in
                let formatted = cookies.map { "\($0.name)=\($0.value)" }.joined(separator: "; ")
                completion(200, ["cookies": formatted, "profile": page.profileName], "success")
            }

        case "setCookies":
            guard let pageId = params["pageId"] as? String,
                  let page = webViewPool.getPage(pageId: pageId),
                  let rawInput = params["cookies"] as? String else {
                completion(400, nil, "Missing parameters")
                return
            }
            let currentHost = page.webView.url?.host ?? "douyin.com"
            let cookieStore = page.dataStore.httpCookieStore
            let pairs = rawInput.components(separatedBy: ";")
            let group = DispatchGroup()

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
                        cookieStore.setCookie(c) { group.leave() }
                    }
                }
            }
            group.notify(queue: .main) {
                completion(200, ["success": true, "profile": page.profileName], "success")
            }

        case "startDouyinQrLogin":
            // 创建无头页面并加载 creator.douyin.com
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
