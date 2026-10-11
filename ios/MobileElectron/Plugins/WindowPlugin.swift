//
//  WindowPlugin.swift
//  MobileElectron
//
//  Created for Mobile Electron Framework iOS Container.
//

import Foundation
import UIKit

public protocol WindowControllable: AnyObject {
    var rootContainerView: UIView { get }
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

        case "createFloatingWindow":
            guard let rootView = windowController?.rootContainerView else {
                completion(500, nil, "Root container view not found")
                return
            }
            FloatingWindowManager.shared.createFloatingWindow(in: rootView, options: params, completion: completion)

        case "closeFloatingWindow":
            let id = params["id"] as? String
            let closed = FloatingWindowManager.shared.closeFloatingWindow(id: id)
            completion(200, ["success": closed], "success")

        case "setFloatingWindowVisible":
            let id = params["id"] as? String ?? ""
            let visible = params["visible"] as? Bool ?? true
            let ok = FloatingWindowManager.shared.setFloatingWindowVisible(id: id, visible: visible)
            completion(200, ["success": ok], "success")

        case "minimizeFloatingWindow":
            let id = params["id"] as? String ?? ""
            let minimized = params["minimized"] as? Bool ?? true
            let ok = FloatingWindowManager.shared.minimizeFloatingWindow(id: id, minimize: minimized)
            completion(200, ["success": ok], "success")

        case "updateFloatingWindow":
            let id = params["id"] as? String ?? ""
            let ok = FloatingWindowManager.shared.updateFloatingWindow(id: id, options: params)
            completion(200, ["success": ok], "success")

        case "evalFloatingWindow":
            let id = params["id"] as? String ?? ""
            let script = params["script"] as? String ?? ""
            FloatingWindowManager.shared.evalFloatingWindow(id: id, script: script) { result, error in
                if let err = error {
                    completion(500, nil, err)
                } else {
                    completion(200, ["success": true, "result": result as Any], "success")
                }
            }

        case "getFloatingWindowState":
            let id = params["id"] as? String
            if let state = FloatingWindowManager.shared.getFloatingWindowState(id: id) {
                completion(200, state, "success")
            } else {
                completion(404, nil, "Floating window not found")
            }

        default:
            completion(404, nil, "Unknown action '\(action)' in module 'window'")
        }
    }
}
