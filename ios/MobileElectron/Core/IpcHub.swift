//
//  IpcHub.swift
//  MobileElectron
//
//  Created for Mobile Electron Framework iOS Container.
//

import Foundation
import WebKit

public class IpcHub: NSObject {

    public static let shared = IpcHub()

    private var registeredViews: [String: () -> WKWebView?] = [:]

    private override init() {
        super.init()
    }

    /**
     * 注册视图获取提供者 (使用弱引用闭包防止循环引用)
     */
    public func registerView(id: String, provider: @escaping () -> WKWebView?) {
        registeredViews[id] = provider
    }

    /**
     * 注销视图
     */
    public func unregisterView(id: String) {
        registeredViews.removeValue(forKey: id)
    }

    /**
     * 向目标窗口定向投递 IPC 消息
     */
    public func sendTo(senderId: String, targetId: String, channel: String, payload: Any?) -> Bool {
        guard let targetWv = getTargetWebView(targetId: targetId) else {
            return false
        }

        dispatchIpcEvent(to: targetWv, senderId: senderId, targetId: targetId, channel: channel, payload: payload)
        return true
    }

    /**
     * 向所有其他活跃窗口广播 IPC 消息
     */
    public func broadcast(senderId: String, channel: String, payload: Any?) -> Int {
        let targets = collectAllActiveViews()
        var count = 0

        for (id, wv) in targets {
            if id == senderId {
                continue
            }
            dispatchIpcEvent(to: wv, senderId: senderId, targetId: id, channel: channel, payload: payload)
            count += 1
        }
        return count
    }

    private func getTargetWebView(targetId: String) -> WKWebView? {
        if let provider = registeredViews[targetId], let wv = provider() {
            return wv
        }

        // 查活跃的 ManagedPage
        let pages = WebViewPool.shared.getAllPages()
        if let match = pages.first(where: { $0.pageId == targetId }) {
            return match.webView
        }

        return nil
    }

    private func collectAllActiveViews() -> [String: WKWebView] {
        var result: [String: WKWebView] = [:]

        for (id, provider) in registeredViews {
            if let wv = provider() {
                result[id] = wv
            }
        }

        for p in WebViewPool.shared.getAllPages() {
            result[p.pageId] = p.webView
        }

        return result
    }

    private func dispatchIpcEvent(to webView: WKWebView, senderId: String, targetId: String, channel: String, payload: Any?) {
        DispatchQueue.main.async {
            var data: [String: Any] = [
                "channel": channel,
                "senderId": senderId,
                "targetId": targetId,
                "timestamp": Int64(Date().timeIntervalSince1970 * 1000)
            ]
            if let p = payload {
                data["payload"] = p
            } else {
                data["payload"] = NSNull()
            }

            let event: [String: Any] = [
                "event": "ipc.message",
                "data": data
            ]

            do {
                let jsonData = try JSONSerialization.data(withJSONObject: event, options: [])
                if let jsonStr = String(data: jsonData, encoding: .utf8) {
                    let script = "window.__onNativeRpcEvent && window.__onNativeRpcEvent(\(jsonStr));"
                    webView.evaluateJavaScript(script, completionHandler: nil)
                }
            } catch {
                NSLog("[MobileElectron-IpcHub] Failed to serialize IPC event: %@", error.localizedDescription)
            }
        }
    }
}
