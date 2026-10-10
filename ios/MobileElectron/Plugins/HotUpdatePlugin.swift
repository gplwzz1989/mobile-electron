//
//  HotUpdatePlugin.swift
//  MobileElectron
//
//  Created for Mobile Electron Framework iOS Container.
//

import Foundation
import WebKit

public class HotUpdatePlugin: IBridgePlugin {
    public let moduleName = "hotUpdate"
    private weak var webView: WKWebView?
    public weak var bridgeEngine: JsBridgeEngine?

    public init(webView: WKWebView? = nil) {
        self.webView = webView
    }

    public func setWebView(_ webView: WKWebView) {
        self.webView = webView
    }

    public func getRequiredTier(action: String) -> Int {
        if action.caseInsensitiveCompare("getCurrentVersion") == .orderedSame {
            return DomainWhitelistManager.TIER_0_PUBLIC
        } else if action.caseInsensitiveCompare("checkForUpdate") == .orderedSame {
            return DomainWhitelistManager.TIER_1_BUSINESS
        }
        return DomainWhitelistManager.TIER_2_CORE
    }

    public func handleAction(
        callingUrl: String,
        action: String,
        params: [String: Any],
        completion: @escaping (_ code: Int, _ data: [String: Any]?, _ message: String) -> Void
    ) {
        if action.caseInsensitiveCompare("getCurrentVersion") == .orderedSame {
            let info = HotUpdateManager.shared.getActiveVersionInfo()
            completion(200, info, "success")
            return
        }

        if action.caseInsensitiveCompare("checkForUpdate") == .orderedSame {
            let customUrl = params["url"] as? String
            HotUpdateManager.shared.checkForUpdate(customUrl: customUrl) { result in
                switch result {
                case .success(let data):
                    completion(200, data, "success")
                case .failure(let error):
                    completion(500, nil, error.localizedDescription)
                }
            }
            return
        }

        if action.caseInsensitiveCompare("downloadAndInstall") == .orderedSame {
            guard let packageUrl = params["packageUrl"] as? String, !packageUrl.isEmpty else {
                completion(400, nil, "缺少 packageUrl 参数")
                return
            }
            let packageHash = (params["packageHash"] as? String) ?? ""

            HotUpdateManager.shared.downloadAndInstall(
                packageUrl: packageUrl,
                expectedHash: packageHash,
                progress: { [weak self] percent, downloaded, total in
                    self?.bridgeEngine?.sendEvent(eventName: "hotUpdate:progress", data: [
                        "percent": percent,
                        "downloadedBytes": downloaded,
                        "totalBytes": total
                    ])
                },
                completion: { result in
                    switch result {
                    case .success(let data):
                        completion(200, data, "success")
                    case .failure(let error):
                        completion(500, nil, error.localizedDescription)
                    }
                }
            )
            return
        }

        if action.caseInsensitiveCompare("applyAndReload") == .orderedSame {
            DispatchQueue.main.async { [weak self] in
                guard let webView = self?.webView else { return }
                // 如果当前加载的是本地文件，重新定位到沙箱最新入口
                let curUrl = webView.url?.absoluteString ?? ""
                if curUrl.hasPrefix("file://") || curUrl.hasPrefix("local://") {
                    WebViewPool.loadUrl(webView: webView, urlString: "file:///dist/index.html")
                } else {
                    webView.reload()
                }
            }
            completion(200, ["applied": true], "success")
            return
        }

        if action.caseInsensitiveCompare("rollback") == .orderedSame {
            let ok = HotUpdateManager.shared.rollback()
            if ok {
                DispatchQueue.main.async { [weak self] in
                    guard let webView = self?.webView else { return }
                    WebViewPool.loadUrl(webView: webView, urlString: "file:///dist/index.html")
                }
                completion(200, ["rolledBack": true], "success")
            } else {
                completion(500, nil, "回滚失败")
            }
            return
        }

        completion(404, nil, "HotUpdatePlugin: 未知动作 \(action)")
    }
}
