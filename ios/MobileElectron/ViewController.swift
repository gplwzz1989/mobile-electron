//
//  ViewController.swift
//  MobileElectron
//
//  Created for Mobile Electron Framework iOS Container.
//

import UIKit
import WebKit

public class ViewController: UIViewController, WKNavigationDelegate, WindowControllable {

    private var foregroundContainer: UIView!
    private var offscreenContainer: UIView!
    private var mainWebView: WKWebView!

    private var bridgeEngine: JsBridgeEngine!
    private var isStatusBarDarkIcons: Bool = true
    private var isFullscreenMode: Bool = false

    public override var preferredStatusBarStyle: UIStatusBarStyle {
        if #available(iOS 13.0, *) {
            return isStatusBarDarkIcons ? .darkContent : .lightContent
        }
        return isStatusBarDarkIcons ? .default : .lightContent
    }

    public override var prefersStatusBarHidden: Bool {
        return isFullscreenMode
    }

    public override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .white

        // 1. 初始化框架全局配置
        AppConfigManager.shared.initialize()
        let config = AppConfigManager.shared.config

        // 2. 布局前台与后台隐形容器 (Edge-to-Edge 沉浸式)
        setupViews()

        // 3. 构建主 WKWebView 并注册至实例池
        setupMainWebView(config: config)

        // 4. 加载配置中指定的默认启动网页
        loadTargetUrl(url: config.window.defaultUrl)
    }

    private func setupViews() {
        foregroundContainer = UIView(frame: view.bounds)
        foregroundContainer.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(foregroundContainer)

        offscreenContainer = UIView(frame: CGRect(x: 0, y: 0, width: 1, height: 1))
        offscreenContainer.isHidden = true
        view.addSubview(offscreenContainer)

        WebViewPool.shared.setupContainers(foreground: foregroundContainer, offscreen: offscreenContainer)
    }

    private func setupMainWebView(config: AppConfig) {
        let webConfig = WKWebViewConfiguration()
        webConfig.websiteDataStore = WebViewPool.shared.getOrCreateDataStore(profileName: config.profile.defaultProfile)

        // 注入默认硬件伪装脚本到 DocumentStart
        let hwConfig = HardwareConfig.fromPreset(config.profile.hardwarePreset)
        let stealthScript = hwConfig.generateStealthScript()
        let userScript = WKUserScript(source: stealthScript, injectionTime: .atDocumentStart, forMainFrameOnly: false)
        webConfig.userContentController.addUserScript(userScript)

        mainWebView = WKWebView(frame: foregroundContainer.bounds, configuration: webConfig)
        mainWebView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        mainWebView.navigationDelegate = self
        foregroundContainer.addSubview(mainWebView)

        // 绑定原生 Bridge Engine
        bridgeEngine = JsBridgeEngine(webView: mainWebView)
        bridgeEngine.registerPlugin(CookiePlugin())
        bridgeEngine.registerPlugin(PagePlugin())
        bridgeEngine.registerPlugin(NetworkPlugin())
        bridgeEngine.registerPlugin(DevicePlugin())
        bridgeEngine.registerPlugin(WindowPlugin(windowController: self))
        bridgeEngine.registerPlugin(AppPlugin())
        bridgeEngine.registerPlugin(FilePlugin())
        bridgeEngine.registerPlugin(StoragePlugin())
        bridgeEngine.registerPlugin(DialogPlugin())

        webConfig.userContentController.add(bridgeEngine, name: "NativeBridge")

        WebViewPool.shared.registerMainPage(pageId: "main", webView: mainWebView, profileName: config.profile.defaultProfile)
    }

    public func loadTargetUrl(url: String) {
        WebViewPool.loadUrl(webView: mainWebView, urlString: url)
    }

    // MARK: - WindowControllable
    public func updateStatusBar(darkIcons: Bool) {
        self.isStatusBarDarkIcons = darkIcons
        UIView.animate(withDuration: 0.25) {
            self.setNeedsStatusBarAppearanceUpdate()
        }
    }

    public func updateFullscreen(fullscreen: Bool) {
        self.isFullscreenMode = fullscreen
        UIView.animate(withDuration: 0.25) {
            self.setNeedsStatusBarAppearanceUpdate()
        }
    }

    // MARK: - WKNavigationDelegate
    public func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        NSLog("[MobileElectron-iOS] Page loaded: %@", webView.url?.absoluteString ?? "")
    }

    public func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        NSLog("[MobileElectron-iOS] Load failed: %@", error.localizedDescription)
    }
}
