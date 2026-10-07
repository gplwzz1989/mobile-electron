# 商业级 Mobile Electron 容器 iOS 端架构与源码实现规范

本规范提供了 Mobile Electron 在 iOS 端（Swift + WebKit）对应的完整落地代码。设计完全契合前述 RFC-001 规范，保持与 Android 端 100% 统一的 JSON-RPC 2.0 协议和域名白名单安全模型。

---

## 1. 核心目录与类架构设计

```
MobileElectron-iOS/
├── Security/
│   └── DomainWhitelistManager.swift    // 域名白名单与分级鉴权管理器
├── Bridge/
│   ├── JsBridgeEngine.swift            // JSON-RPC 2.0 桥接调度器 (WKScriptMessageHandler)
│   ├── IBridgePlugin.swift             // 插件抽象协议
│   └── BridgeCallback.swift            // 异步回调包装器
├── Plugins/
│   ├── CookiePlugin.swift              // 基于 WKHTTPCookieStore 的底层 Cookie 插件
│   ├── PagePlugin.swift                // 多 WKWebView 与无头 (Headless) 实例调度插件
│   ├── NetworkPlugin.swift             // 基于 URLSession 的原生穿透请求插件
│   └── DevicePlugin.swift              // 剪贴板与原生 UI 插件
└── Core/
    └── WebViewPool.swift               // WKProcessPool 共享池与无头页面回收器
```

---

## 2. 核心 Swift 实现代码

### 2.1 域名白名单管理器 (`DomainWhitelistManager.swift`)

```swift
import Foundation

public final class DomainWhitelistManager {
    public static let shared = DomainWhitelistManager()

    public static let TIER_0_PUBLIC = 0
    public static let TIER_1_BUSINESS = 1
    public static let TIER_2_CORE = 2

    private var tier1Whitelist = Set<String>()
    private var tier2Whitelist = Set<String>()
    private var strictHttps: Bool = true
    private let lock = NSLock()

    private init() {
        // 默认预置白名单
        addTier1Domain("*.baidu.com")
        addTier1Domain("*.github.com")
        addTier1Domain("localhost")
        addTier1Domain("127.0.0.1")

        addTier2Domain("*.baidu.com")
        addTier2Domain("localhost")
        addTier2Domain("127.0.0.1")
    }

    public func addTier1Domain(_ pattern: String) {
        lock.lock()
        defer { lock.unlock() }
        tier1Whitelist.insert(pattern.lowercased())
    }

    public func addTier2Domain(_ pattern: String) {
        lock.lock()
        defer { lock.unlock() }
        tier2Whitelist.insert(pattern.lowercased())
    }

    public func checkPermission(url: String, requiredTier: Int) -> Bool {
        if requiredTier == Self.TIER_0_PUBLIC { return true }
        guard let urlObj = URL(string: url), let host = urlObj.host?.lowercased() else {
            return false
        }

        if strictHttps && host != "localhost" && host != "127.0.0.1" {
            if urlObj.scheme?.lowercased() != "https" {
                return false
            }
        }

        lock.lock()
        defer { lock.unlock() }

        if requiredTier == Self.TIER_1_BUSINESS {
            return matchesAny(host: host, set: tier1Whitelist) || matchesAny(host: host, set: tier2Whitelist)
        }
        if requiredTier == Self.TIER_2_CORE {
            return matchesAny(host: host, set: tier2Whitelist)
        }
        return false
    }

    private func matchesAny(host: String, set: Set<String>) -> Bool {
        for rule in set {
            if rule == host { return true }
            if rule.hasPrefix("*.") {
                let root = String(rule.dropFirst(2))
                if host == root || host.hasSuffix("." + root) {
                    return true
                }
            }
        }
        return false
    }
}
```

---

### 2.2 插件协议与调度网关 (`JsBridgeEngine.swift`)

```swift
import Foundation
import WebKit

public protocol IBridgePlugin: AnyObject {
    var moduleName: String { get }
    func getRequiredTier(action: String) -> Int
    func handleAction(callingUrl: String, action: String, params: [String: Any], completion: @escaping (_ code: Int, _ data: [String: Any]?, _ message: String) -> Void)
}

public class JsBridgeEngine: NSObject, WKScriptMessageHandler {
    private weak var webView: WKWebView?
    private var plugins = [String: IBridgePlugin]()

    public init(webView: WKWebView) {
        self.webView = webView
        super.init()
    }

    public func registerPlugin(_ plugin: IBridgePlugin) {
        plugins[plugin.moduleName.lowercased()] = plugin
    }

    // 处理来自 JS 的 window.webkit.messageHandlers.NativeBridge.postMessage(json)
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
            sendResponse(reqId: reqId, code: 404, data: nil, message: "Module \(module) not found")
            return
        }

        let tier = plugin.getRequiredTier(action: action)
        if !DomainWhitelistManager.shared.checkPermission(url: currentUrl, requiredTier: tier) {
            sendResponse(reqId: reqId, code: 401, data: nil, message: "Domain \(currentUrl) not authorized for tier \(tier)")
            return
        }

        plugin.handleAction(callingUrl: currentUrl, action: action, params: params) { [weak self] code, resData, msg in
            self?.sendResponse(reqId: reqId, code: code, data: resData, message: msg)
        }
    }

    private func sendResponse(reqId: String, code: Int, data: [String: Any]?, message: String) {
        DispatchQueue.main.async { [weak self] in
            var resp: [String: Any] = [
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
}
```

---

### 2.3 底层 Cookie 控制插件 (`CookiePlugin.swift`)

```swift
import Foundation
import WebKit

public class CookiePlugin: IBridgePlugin {
    public let moduleName = "cookie"
    private weak var webView: WKWebView?

    public init(webView: WKWebView) {
        self.webView = webView
    }

    public func getRequiredTier(action: String) -> Int {
        return DomainWhitelistManager.TIER_2_CORE
    }

    public func handleAction(callingUrl: String, action: String, params: [String: Any], completion: @escaping (Int, [String: Any]?, String) -> Void) {
        guard let cookieStore = webView?.configuration.websiteDataStore.httpCookieStore else {
            completion(500, nil, "CookieStore unavailable")
            return
        }

        switch action {
        case "get":
            cookieStore.getAllCookies { cookies in
                let formatted = cookies.map { "\($0.name)=\($0.value)" }.joined(separator: "; ")
                completion(200, ["cookies": formatted], "success")
            }

        case "clear":
            cookieStore.getAllCookies { cookies in
                let group = DispatchGroup()
                for c in cookies {
                    group.enter()
                    cookieStore.delete(c) { group.leave() }
                }
                group.notify(queue: .main) {
                    completion(200, ["success": true], "success")
                }
            }

        default:
            completion(404, nil, "Unknown action \(action)")
        }
    }
}
```

---

### 2.4 原生网络穿透插件 (`NetworkPlugin.swift`)

```swift
import Foundation

public class NetworkPlugin: IBridgePlugin {
    public let moduleName = "network"

    public func getRequiredTier(action: String) -> Int {
        return DomainWhitelistManager.TIER_2_CORE
    }

    public func handleAction(callingUrl: String, action: String, params: [String: Any], completion: @escaping (Int, [String: Any]?, String) -> Void) {
        if action == "fetch" {
            guard let urlStr = params["url"] as? String, let url = URL(string: urlStr) else {
                completion(400, nil, "Invalid URL")
                return
            }

            var request = URLRequest(url: url)
            request.httpMethod = (params["method"] as? String ?? "GET").uppercased()

            if let headers = params["headers"] as? [String: String] {
                for (k, v) in headers {
                    request.setValue(v, forHTTPHeaderField: k)
                }
            }

            if let bodyStr = params["body"] as? String {
                request.httpBody = bodyStr.data(using: .utf8)
            }

            // 发起原生网络请求，完全绕过 WebKit 浏览器的 CORS 限制
            let task = URLSession.shared.dataTask(with: request) { data, response, error in
                if let error = error {
                    completion(500, nil, "Network error: \(error.localizedDescription)")
                    return
                }

                let httpResponse = response as? HTTPURLResponse
                let status = httpResponse?.statusCode ?? 200
                var resHeaders = [String: String]()
                if let allHeaders = httpResponse?.allHeaderFields {
                    for (k, v) in allHeaders {
                        resHeaders["\(k)"] = "\(v)"
                    }
                }

                let body = data != nil ? String(data: data!, encoding: .utf8) ?? "" : ""
                completion(200, [
                    "status": status,
                    "headers": resHeaders,
                    "body": body
                ], "success")
            }
            task.resume()
        } else {
            completion(404, nil, "Unknown action")
        }
    }
}
```

---

### 2.5 多 Profile 数据目录物理隔离与 WebViewPool (`WebViewPool.swift`)

在 iOS 17+ 中，Apple 原生支持为 `WKWebsiteDataStore` 指定 UUID 标识符实现物理持久化分区隔离；在早期版本降级为 `WKWebsiteDataStore.nonPersistent()` 内存隔离。

```swift
import Foundation
import WebKit

public class WebViewPool {
    public static let shared = WebViewPool()

    public class ManagedPage {
        public let pageId: String
        public let webView: WKWebView
        public var isForeground: Bool
        public let profileName: String
        public let dataStore: WKWebsiteDataStore

        public init(pageId: String, webView: WKWebView, isForeground: Bool, profileName: String, dataStore: WKWebsiteDataStore) {
            self.pageId = pageId
            self.webView = webView
            self.isForeground = isForeground
            self.profileName = profileName
            self.dataStore = dataStore
        }
    }

    private var pages = [String: ManagedPage]()
    private var dataStores = [String: WKWebsiteDataStore]()
    private var counter = 100
    private weak var containerView: UIView?

    public func setContainerView(_ container: UIView) {
        self.containerView = container
    }

    /// 获取或创建专属独立 Profile 的 DataStore
    public func getOrCreateDataStore(profileName: String) -> WKWebsiteDataStore {
        if profileName == "default" {
            return WKWebsiteDataStore.default()
        }
        if let existing = dataStores[profileName] {
            return existing
        }

        let store: WKWebsiteDataStore
        if #available(iOS 17.0, *) {
            // iOS 17 原生支持指定 Identifier 的物理独立持久化分区
            let uuid = UUID(uuidString: deterministicUuid(from: profileName)) ?? UUID()
            store = WKWebsiteDataStore(forIdentifier: uuid)
        } else {
            // 降级为非持久化内存隔离沙箱
            store = WKWebsiteDataStore.nonPersistent()
        }
        dataStores[profileName] = store
        return store
    }

    /// 创建具备独立 Profile 隔离的后台无头 WebView
    public func createHeadlessPage(customUA: String? = nil, profileName: String = "default") -> ManagedPage {
        counter += 1
        let pageId = profileName == "default" ? "page_headless_\(counter)" : "page_\(profileName)_\(counter)"
        let dataStore = getOrCreateDataStore(profileName: profileName)

        let config = WKWebViewConfiguration()
        config.websiteDataStore = dataStore

        let webView = WKWebView(frame: CGRect(x: 0, y: 0, width: 1, height: 1), configuration: config)
        webView.isHidden = true
        if let ua = customUA {
            webView.customUserAgent = ua
        }

        let managed = ManagedPage(pageId: pageId, webView: webView, isForeground: false, profileName: profileName, dataStore: dataStore)
        pages[pageId] = managed
        return managed
    }

    /// 平滑将后台页面切换至前台展示
    public func switchToForeground(pageId: String) -> Bool {
        guard let container = containerView, let target = pages[pageId] else { return false }

        // 隐藏当前前台页面
        for (_, page) in pages where page.isForeground {
            page.webView.removeFromSuperview()
            page.isForeground = false
        }

        // 挂载目标页面至主视口
        target.webView.frame = container.bounds
        target.webView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        target.webView.isHidden = false
        container.addSubview(target.webView)
        target.isForeground = true

        return true
    }

    private func deterministicUuid(from string: String) -> String {
        // 生成固定的 36 位 UUID 格式串
        let hash = string.utf8.reduce(0) { ($0 &* 31) &+ Int($1) }
        return String(format: "00000000-0000-0000-0000-%012x", abs(hash))
    }
}
```

```
