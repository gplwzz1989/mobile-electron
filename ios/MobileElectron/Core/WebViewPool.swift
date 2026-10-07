//
//  WebViewPool.swift
//  MobileElectron
//
//  Created for Mobile Electron Framework iOS Container.
//

import Foundation
import WebKit

public class WebViewPool: NSObject {
    public static let shared = WebViewPool()

    public class ManagedPage {
        public let pageId: String
        public let webView: WKWebView
        public var isForeground: Bool
        public var isHeadless: Bool
        public let profileName: String
        public let dataStore: WKWebsiteDataStore
        public var hardwareConfig: HardwareConfig
        public let createdAt: Date

        public init(
            pageId: String,
            webView: WKWebView,
            isForeground: Bool,
            isHeadless: Bool,
            profileName: String,
            dataStore: WKWebsiteDataStore,
            hardwareConfig: HardwareConfig
        ) {
            self.pageId = pageId
            self.webView = webView
            self.isForeground = isForeground
            self.isHeadless = isHeadless
            self.profileName = profileName
            self.dataStore = dataStore
            self.hardwareConfig = hardwareConfig
            self.createdAt = Date()
        }
    }

    private var pages = [String: ManagedPage]()
    private var dataStores = [String: WKWebsiteDataStore]()
    private var idCounter = 100
    private let lock = NSLock()

    private weak var foregroundContainerView: UIView?
    private weak var offscreenContainerView: UIView?

    public var onActivePageChanged: ((ManagedPage?) -> Void)?

    private override init() {
        super.init()
    }

    public func setupContainers(foreground: UIView, offscreen: UIView) {
        self.foregroundContainerView = foreground
        self.offscreenContainerView = offscreen
    }

    public func registerMainPage(pageId: String = "main", webView: WKWebView, profileName: String = "default") {
        lock.lock()
        defer { lock.unlock() }

        let store = webView.configuration.websiteDataStore
        let page = ManagedPage(
            pageId: pageId,
            webView: webView,
            isForeground: true,
            isHeadless: false,
            profileName: profileName,
            dataStore: store,
            hardwareConfig: HardwareConfig()
        )
        pages[pageId] = page
    }

    public func getOrCreateDataStore(profileName: String) -> WKWebsiteDataStore {
        lock.lock()
        defer { lock.unlock() }

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

    public func createHeadlessPage(
        customUA: String? = nil,
        profileName: String = "default",
        hardwareConfig: HardwareConfig = HardwareConfig(),
        initialUrl: String? = nil
    ) -> ManagedPage {
        lock.lock()
        idCounter += 1
        let pageId = profileName == "default" ? "page_headless_\(idCounter)" : "page_\(profileName)_\(idCounter)"
        lock.unlock()

        let store = getOrCreateDataStore(profileName: profileName)

        let config = WKWebViewConfiguration()
        config.websiteDataStore = store

        // 注入硬件伪装脚本到 DocumentStart
        let stealthScript = hardwareConfig.generateStealthScript()
        let userScript = WKUserScript(source: stealthScript, injectionTime: .atDocumentStart, forMainFrameOnly: false)
        config.userContentController.addUserScript(userScript)

        let webView = WKWebView(frame: CGRect(x: 0, y: 0, width: 1, height: 1), configuration: config)
        webView.isHidden = true
        if let ua = customUA, !ua.isEmpty {
            webView.customUserAgent = ua
        }

        if let offscreen = offscreenContainerView {
            DispatchQueue.main.async {
                offscreen.addSubview(webView)
            }
        }

        let managed = ManagedPage(
            pageId: pageId,
            webView: webView,
            isForeground: false,
            isHeadless: true,
            profileName: profileName,
            dataStore: store,
            hardwareConfig: hardwareConfig
        )

        lock.lock()
        pages[pageId] = managed
        lock.unlock()

        if let urlStr = initialUrl, let url = URL(string: urlStr) {
            DispatchQueue.main.async {
                webView.load(URLRequest(url: url))
            }
        }

        return managed
    }

    public func switchToForeground(pageId: String) -> Bool {
        lock.lock()
        guard let target = pages[pageId], let container = foregroundContainerView else {
            lock.unlock()
            return false
        }

        // 把现有前台移出或隐藏
        for (_, page) in pages where page.isForeground {
            DispatchQueue.main.async {
                page.webView.removeFromSuperview()
                if let offscreen = self.offscreenContainerView {
                    offscreen.addSubview(page.webView)
                }
            }
            page.isForeground = false
        }

        target.isForeground = true
        target.isHeadless = false
        lock.unlock()

        DispatchQueue.main.async {
            target.webView.frame = container.bounds
            target.webView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            target.webView.isHidden = false
            container.addSubview(target.webView)
            self.onActivePageChanged?(target)
        }

        return true
    }

    public func getPage(pageId: String) -> ManagedPage? {
        lock.lock()
        defer { lock.unlock() }
        return pages[pageId]
    }

    public func getActiveForegroundPage() -> ManagedPage? {
        lock.lock()
        defer { lock.unlock() }
        return pages.values.first(where: { $0.isForeground })
    }

    public func getAllPages() -> [ManagedPage] {
        lock.lock()
        defer { lock.unlock() }
        return Array(pages.values)
    }

    public func getAllProfileNames() -> [String] {
        lock.lock()
        defer { lock.unlock() }
        var set = Set<String>()
        set.insert("default")
        for p in pages.values {
            set.insert(p.profileName)
        }
        return Array(set)
    }

    public func closePage(pageId: String) {
        lock.lock()
        guard let page = pages.removeValue(forKey: pageId) else {
            lock.unlock()
            return
        }
        lock.unlock()

        DispatchQueue.main.async {
            page.webView.stopLoading()
            page.webView.removeFromSuperview()
        }
    }

    public func isMultiProfileSupported() -> Bool {
        if #available(iOS 17.0, *) {
            return true
        }
        return false
    }

    private func deterministicUuid(from string: String) -> String {
        let hash = string.utf8.reduce(0) { ($0 &* 31) &+ Int($1) }
        return String(format: "00000000-0000-0000-0000-%012x", abs(hash))
    }
}
