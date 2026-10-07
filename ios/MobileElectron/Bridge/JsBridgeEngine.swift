//
//  JsBridgeEngine.swift
//  MobileElectron
//
//  Created for Mobile Electron Framework iOS Container.
//

import Foundation
import WebKit

public class JsBridgeEngine: NSObject, WKScriptMessageHandler {
    private weak var webView: WKWebView?
    private var plugins = [String: IBridgePlugin]()
    private let queue = DispatchQueue(label: "com.mobileelectron.bridge.queue", attributes: .concurrent)

    public init(webView: WKWebView) {
        self.webView = webView
        super.init()
    }

    public func registerPlugin(_ plugin: IBridgePlugin) {
        plugins[plugin.moduleName.lowercased()] = plugin
    }

    // 处理来自 JS SDK 的 RPC 消息包
    public func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        guard message.name == "NativeBridge", let jsonString = message.body as? String else { return }

        guard let data = jsonString.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return
        }

        let reqId = json["id"] as? String ?? ""
        let module = (json["module"] as? String ?? "").lowercased()
        let action = json["action"] as? String ?? ""
        let params = json["params"] as? [String: Any] ?? [:]

        let currentUrl = webView?.url?.absoluteString ?? ""

        guard let plugin = plugins[module] else {
            sendResponse(reqId: reqId, code: 404, data: nil, message: "Plugin module '\(module)' not found")
            return
        }

        let tier = plugin.getRequiredTier(action: action)
        if !DomainWhitelistManager.shared.checkPermission(url: currentUrl, requiredTier: tier) {
            sendResponse(reqId: reqId, code: 401, data: nil, message: "Permission Denied: Domain not whitelisted for tier \(tier)")
            return
        }

        queue.async {
            plugin.handleAction(callingUrl: currentUrl, action: action, params: params) { [weak self] code, resData, msg in
                self?.sendResponse(reqId: reqId, code: code, data: resData, message: msg)
            }
        }
    }

    public func sendResponse(reqId: String, code: Int, data: [String: Any]?, message: String) {
        DispatchQueue.main.async { [weak self] in
            let resp: [String: Any] = [
                "id": reqId,
                "code": code,
                "data": data ?? [:],
                "message": message
            ]
            if let respData = try? JSONSerialization.data(withJSONObject: resp),
               let respStr = String(data: respData, encoding: .utf8) {
                let js = "window.__onNativeRpcResponse && window.__onNativeRpcResponse(\(respStr));"
                self?.webView?.evaluateJavaScript(js, completionHandler: nil)
            }
        }
    }

    public func sendEvent(eventName: String, data: [String: Any]) {
        DispatchQueue.main.async { [weak self] in
            let evt: [String: Any] = [
                "event": eventName,
                "data": data
            ]
            if let evtData = try? JSONSerialization.data(withJSONObject: evt),
               let evtStr = String(data: evtData, encoding: .utf8) {
                let js = "window.__onNativeRpcEvent && window.__onNativeRpcEvent(\(evtStr));"
                self?.webView?.evaluateJavaScript(js, completionHandler: nil)
            }
        }
    }
}
