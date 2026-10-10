# Mobile Electron 框架下一阶段完整开发计划与产品演进路线图 (Roadmap)

> **规划周期**：2026 Q4 - 2027 Q2  
> **核心目标**：从单端 Android 容器升级为工业级全平台（Android + iOS）移动端 Electron 跨端框架生态，具备离线包热更新、完整脚手架与动态插件能力。

---

## 1. 演进全景与里程碑规划 (Milestones)

```mermaid
flowchart LR
    M1["里程碑 1 (已完成)<br/>✓ Android微内核容器<br/>✓ app-config配置驱动<br/>✓ @mobile-electron/core SDK<br/>✓ 多Profile物理隔离<br/>✓ 全Web界面化驱动"]
    
    M2["里程碑 2 (已完成)<br/>✓ 在线/离线双模打包支持<br/>✓ 前端Vite相对路径内嵌<br/>✓ AndroidX WebViewAssetLoader<br/>✓ iOS Bundle资源沙盒加载<br/>✓ GitHub Actions自动化CI/CD"]

    M3["里程碑 3 (已完成)<br/>✓ .mep离线包规范与清单生成<br/>✓ Android/iOS 双端沙箱热更新引擎<br/>✓ SHA-256校验与自愈回滚兜底<br/>✓ electron.hotUpdate 前端控制台<br/>✓ create-mobile-electron-app脚手架 (Vue/React/Vanilla)"]

    M4["里程碑 4 (核心插件落地)<br/>✓ Haptics 触感反馈系统<br/>✓ Biometrics 生物识别认证 (Face/Touch ID)<br/>✓ SecureStorage 硬件级安全加密库<br/>✓ Camera/Barcode 拍照与二维码扫描<br/>· 动态云控中心与增量差分 (演进中)"]

    M1 --> M2 --> M3 --> M4
```

---

## 2. 详细演进阶段与具体开发任务

### 阶段一：离线资源包引擎与热更新体系 (Phase 1: Dynamic Offline Bundle & Hot Reload - 已完成)
> **状态**：**已交付落地**。包含 `.mep` 离线包规范、Android/iOS 原生沙箱拦截切换、SHA-256 完整性防篡改校验、自愈回滚机制、前端 SDK API 及可视化热更新面板。

1. **离线资源包规范定义**：
   - 支持将 Vite / Webpack 构建出的 `dist/` 目录压缩为 `.mep` (Mobile Electron Package) 加密压缩包；
   - 内部包含资源清单 `manifest.json`（各文件哈希与版本号）；
2. **原生端本地拦截服务 (`WebViewAssetLoader`)**：
   - 采用 Android 原生 `androidx.webkit.WebViewAssetLoader` 与 iOS `WKURLSchemeHandler`；
   - 拦截请求，优先从本地应用私有沙箱高速读取缓存资源，大幅提升首屏加载速度（首屏渲染从 1500ms 降至 150ms）；
3. **差分增量热更新服务**：
   - App 启动或切到前台时，静默向配置的更新服务器发起轻量轮询；
   - 支持仅下载 Diff 增量文件，在后台解压校验无误后，无感完成下一版本热更新。

---

### 阶段二：iOS 容器内核对齐与功能平移 (Phase 2: Full iOS Container Alignment)
> **目标**：打造 iOS 版原生底座工程，使同一份前端网站代码与 SDK 调用在 iOS 与 Android 上拥有完全一致的行为。

1. **iOS 底座工程搭建 (Swift / Objective-C)**：
   - 基于 `WKWebView` 构建多实例渲染池；
   - 实现 `WKScriptMessageHandler` 与 JSON-RPC 2.0 桥接协议解析；
2. **多 Profile 数据目录隔离落地**：
   - 深入利用 iOS 17+ 提供的 `WKWebsiteDataStore(forIdentifier: uuid)` 实现持久化沙箱物理隔离；
   - iOS 17 以下自动平滑降级为 `WKWebsiteDataStore.nonPersistent()`；
3. **硬件指纹在 WebKit 的注入实现**：
   - 使用 `WKUserScript` 在 `.atDocumentStart` 时机注入 CPU、显卡、屏幕参数虚拟化脚本；
4. **统一平台跨端抹平**：
   - 确保 `electron.window.setStatusBar`、`electron.device.toast`、`electron.cookie.*` 在双端表现一致。

---

### 阶段三：开发者工具链与工程化脚手架 (Phase 3: CLI & Developer Ecosystem - 已交付)
> **状态**：**已交付落地**。包含独立脚手架包 `create-mobile-electron-app`、交互式/参数化初始化流程、Vanilla-TS / Vue-TS / React-TS 三套完整工程模版。

1. **交互式脚手架 CLI (`create-mobile-electron-app`)**：
   ```bash
   npx create-mobile-electron-app my-app --template vue-ts
   ```
   - 自动生成预置 `@mobile-electron/core`、`app-config.json` 与 Vite 的工程模版；
   - 支持 `--template`, `--mode`, `--yes` 等命令行参数自动化初始化；
2. **离线包构建与本地分发调试工具**：
   - `npm run bundle:build`：一键自动扫描并构建 `.mep` 离线更新包；
   - `npm run bundle:serve`：一键启动本地/模拟器热更新分发调试服务。

---

### 阶段四：动态插件化中心与底层扩展能力 (Phase 4: Dynamic Plugin System - 核心插件已交付)
> **状态**：**核心高频插件已落地**。双端零三方外部依赖，完全基于 Android 与 iOS 系统原生硬件与安全框架构建。

1. **统一原生插件规范 (`IBridgePlugin` 跨语言标准)**：
   - Android（Java / AndroidX）与 iOS（Swift / WebKit）统一 JSON-RPC 2.0 桥接协议与三级权限分级安全机制；
2. **开箱即用官方高频特权插件库**：
   - **触感系统 (`electron.haptics`)**：支持轻/中/重 Impact、通知震动 (Success/Warning/Error) 与 Selection 刻度；
   - **生物识别 (`electron.biometrics`)**：集成 Android `BiometricPrompt` 与 iOS `LocalAuthentication` (Face ID / Touch ID)；
   - **硬件加密存储 (`electron.secureStorage`)**：基于 AndroidKeyStore (AES-256-GCM) 与 iOS Keychain Services，芯片级安全沙箱；
   - **媒体与扫码 (`electron.camera`)**：支持原生相机拍摄、相册拾取与毫秒级条码/二维码扫描识别；
3. **安全沙箱强化与白名单拦截**：
   - `DomainWhitelistManager` 双端对齐，按调用者 URL 动态阻断非受信任来源对底层硬件特权的未授权调用。

---

## 3. 开发资源与排期规划概览

| 阶段 | 交付物 | 周期 | 核心负责模块 |
| :--- | :--- | :--- | :--- |
| **P1: 离线包与热更** | `WebViewAssetLoader` 资源拦截、增量差分热更、加载骨架屏 | 4 周 | Android 原生内核 + 部署端微服务 |
| **P2: iOS 底座对齐** | iOS Xcode 工程、`WKWebsiteDataStore` 隔离、双端 SDK 对齐 | 6 周 | iOS 原生研发 + SDK 跨端抹平 |
| **P3: CLI 命令行** | `create-mobile-electron-app`、自动化本地预览与打包命令 | 3 周 | 前端工程化工具链 |
| **P4: 插件生态中心** | 官方四大扩展插件 (MMKV/相机/生物识别/保活)、插件市场标准 | 4 周 | 原生插件研发 + 文档体系建设 |
