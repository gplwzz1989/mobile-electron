//
//  WindowPlugin.swift
//  MobileElectron
//
//  Created for Mobile Electron Framework iOS Container.
//

import Foundation
import UIKit

public protocol WindowControllable: AnyObject {
    func updateStatusBar(darkIcons: Bool)
    func updateFullscreen(fullscreen: Bool)
    func loadTargetUrl(url: String)
}

public class WindowPlugin: IBridgePlugin {
    public let moduleName = "window"
    private weak var windowController: WindowControllable?
    private let webViewPool: WebViewPool

    public init(windowController: WindowControllable?, webViewPool: WebViewPool = WebViewPool.shared) {
        self.windowController = windowController
        self.webViewPool = webViewPool
    }

    public func getRequiredTier(action: String) -> Int {
        if action == "reload" || action == "goBack" || action == "canGoBack" {
            return DomainWhitelistManager.TIER_0_PUBLIC
        }
        return DomainWhitelistManager.TIER_1_BUSINESS
    }

    public func handleAction(
        callingUrl: String,
        action: String,
        params: [String: Any],
        completion: @escaping (Int, [String: Any]?, String) -> Void
    ) {
        let activePage = webViewPool.getActiveForegroundPage()

        switch action {
        case "setStatusBar":
            let darkIcons = params["darkIcons"] as? Bool ?? true
            DispatchQueue.main.async {
                self.windowController?.updateStatusBar(darkIcons: darkIcons)
            }
            completion(200, ["success": true], "success")

        case "setFullscreen":
            let fullscreen = params["fullscreen"] as? Bool ?? true
            DispatchQueue.main.async {
                self.windowController?.updateFullscreen(fullscreen: fullscreen)
            }
            completion(200, ["success": true, "fullscreen": fullscreen], "success")

        case "reload":
            DispatchQueue.main.async {
                activePage?.webView.reload()
            }
            completion(200, ["success": true], "success")

        case "goBack":
            DispatchQueue.main.async {
                let can = activePage?.webView.canGoBack ?? false
                if can { activePage?.webView.goBack() }
                completion(200, ["success": can], "success")
            }

        case "canGoBack":
            DispatchQueue.main.async {
                let can = activePage?.webView.canGoBack ?? false
                completion(200, ["canGoBack": can], "success")
            }

        case "loadUrl":
            guard let urlStr = params["url"] as? String, !urlStr.isEmpty else {
                completion(400, nil, "Missing url")
                return
            }
            DispatchQueue.main.async {
                self.windowController?.loadTargetUrl(url: urlStr)
            }
            completion(200, ["success": true, "url": urlStr], "success")

        case "setDebugToolbarVisible":
            completion(200, ["success": true], "success")

        default:
            completion(404, nil, "Unknown action '\(action)' in module 'window'")
        }
    }
}
