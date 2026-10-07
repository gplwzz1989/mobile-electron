# Mobile Electron iOS 原生容器工程 (Swift + WebKit)

这是 Mobile Electron 跨端开发框架在 iOS 平台的原生微内核容器实现，与 Android 端在协议、配置与能力层实现 **100% 对齐**。

---

## 🌟 核心特性与双端对齐

1. **统一通信桥梁 (JSON-RPC 2.0)**：
   - 监听前端 SDK 调用的 `window.webkit.messageHandlers.NativeBridge.postMessage(json)`；
   - 结果统一通过 `window.__onNativeRpcResponse(json)` 异步回调响应，事件通过 `window.__onNativeRpcEvent(json)` 广播。
2. **多 Profile 独立沙箱隔离 (`WKWebsiteDataStore`)**：
   - iOS 17+ 原生支持 `WKWebsiteDataStore(forIdentifier: UUID)`，每个 Profile 享有物理独立的持久化 Cookie 数据库与 LocalStorage；
   - 低于 iOS 17 时平滑降级为 `WKWebsiteDataStore.nonPersistent()` 内存沙箱。
3. **底层 Cookie 精准管控 (`WKHTTPCookieStore`)**：
   - 支持实时读写、批量导入与一键清空指定域名与分区的 Cookie。
4. **底层硬件指纹防检测 (Stealth)**：
   - 采用 `WKUserScript` 在 `.atDocumentStart` 注入 CPU、内存、GPU 与屏幕虚拟指纹，防止目标网站指纹风控。
5. **配置驱动启动 (`app-config.json`)**：
   - 启动时自动读取配置文件中的 `window.defaultUrl`，支持本地离线包或线上 HTTPS 网站。

---

## 📁 目录结构

```
ios/
└── MobileElectron/
    ├── Config/
    │   ├── AppConfig.swift             // 容器配置模型
    │   └── AppConfigManager.swift      // app-config.json 解析中心
    ├── Security/
    │   └── DomainWhitelistManager.swift// 三级白名单安全网关
    ├── Bridge/
    │   ├── IBridgePlugin.swift         // 统一插件协议
    │   └── JsBridgeEngine.swift        // WKScriptMessageHandler 调度引擎
    ├── Core/
    │   ├── HardwareConfig.swift        // WebKit 硬件伪装与 Stealth 脚本
    │   └── WebViewPool.swift           // 多实例池与 Profile 物理隔离底座
    ├── Plugins/
    │   ├── CookiePlugin.swift          // WKHTTPCookieStore 插件
    │   ├── PagePlugin.swift            // 多页面、无头任务、二维码抓取
    │   ├── NetworkPlugin.swift         // URLSession 绕过 CORS 原生网络插件
    │   ├── DevicePlugin.swift          // 剪贴板与原生 UI 插件
    │   ├── WindowPlugin.swift          // 状态栏沉浸、全屏与历史导航
    │   └── AppPlugin.swift             // 应用信息与生命周期
    ├── ViewController.swift            // 全 Web 沉浸驱动主视窗
    ├── AppDelegate.swift
    ├── SceneDelegate.swift
    └── app-config.json                 // 应用启动配置文件
```

---

## 🛠️ 在 Xcode 中接入与运行

1. 打开 Xcode，选择 **Create a new Xcode Project** -> **iOS App**（Interface: Storyboard / Swift）；
2. 项目名称命名为 `MobileElectron`；
3. 将 `ios/MobileElectron/` 目录下的所有 `.swift` 文件与 `app-config.json` 拖入 Xcode 工程中；
4. 选择真机或 iPhone 模拟器，点击 **Run (Cmd+R)** 即可启动容器；
5. 容器将自动全屏加载 `app-config.json` 中配置的默认网站，前端通过 `@mobile-electron/core` SDK 即可直接调用上述全部原生接口！
