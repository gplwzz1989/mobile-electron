# Mobile Electron 移动端跨端开发框架底座

> **版本**：v2.0.0 Enterprise  
> **核心定位**：专为移动端打造的微内核跨端应用容器底座（"Mobile Electron / Puppeteer for Native"）  
> **产品哲学**：**Shell is Engine, UI is Web（原生即引擎，界面皆网页）**

---

## 🚀 项目概述

Mobile Electron 是一款面向企业级移动端混合应用（Android / iOS）的微内核跨端应用底座。前端开发者可使用任意主流 Web 框架（Vue 3 / React / Vite / 静态 H5），通过配套的 `@mobile-electron/core` SDK，像在桌面端开发 Electron 应用一样，随心所欲调用移动端底层系统特权能力。

### 🌟 核心特性

1. **在线 / 本地离线双模打包支持**：
   - **在线模式 (`online`)**：启动直接加载远程 HTTPS 网站，业务无感热更新，底座轻量；
   - **本地离线模式 (`local`)**：前端全量资产（HTML/CSS/JS/图片）及 SDK 内嵌打包进 APK 与 IPA，零网络依赖，秒开启动，支持 ES Module 与离线沙盒运行。
2. **多 Profile 物理级数据隔离**：
   - 底层利用 Chromium `androidx.webkit.ProfileStore` 与 iOS `WKWebsiteDataStore`；
   - 支持同时静默运行多个相互物理隔离的 WebView 页面，各自拥有独立的 SQLite Cookie 数据库及 LocalStorage，绝无串号泄漏风险。
3. **旗舰级硬件指纹注入与反反爬 (Stealth Engine)**：
   - 底层 `.atDocumentStart` 毫秒级注入伪装 CPU 核心、内存、GPU 渲染器、屏幕参数与 Web Audio 浮点噪声；
   - 原型链深度保护，轻松穿透现代商业级风控设备采集。
4. **统一 JSON-RPC 2.0 桥接协议与三级安全网关**：
   - 包含窗口导航、原生 Cookie 控制、独立多页面调度、底层网络穿透、设备剪贴板与 Toast、文件读写、弹窗控制等系统级插件体系。
5. **CI/CD 全自动化构建**：
   - 基于 GitHub Actions，支持一键编译、测试并导出 Android APK 与 iOS IPA 安装包。

---

## 📁 仓库目录结构

```text
.
├── app/                        # Android 原生工程（基于 WebView + JsBridgeEngine）
│   ├── src/main/assets/        # 原生资产目录（内置 app-config.json 与 dist/ 离线包）
│   └── src/main/java/          # 原生微内核与插件实现源码
├── ios/                        # iOS 原生工程（基于 XcodeGen + WKWebView）
│   ├── MobileElectron/         # Swift 原生容器与插件实现源码
│   └── project.yml             # XcodeGen 项目规范配置
├── sdk/                        # 跨端客户端 SDK (@mobile-electron/core)
│   ├── src/                    # TypeScript 源码 (RPC 客户端、Page 抽象、各模块插件)
│   └── dist/                   # 编译生成的 ESM、CJS、UMD 与 .d.ts 声明
├── web/                        # 前端控制台 / Web 界面工程 (Vue/Vite/TypeScript)
│   ├── src/                    # 前端界面源码与业务逻辑
│   └── vite.config.ts          # Vite 配置文件 (已配置相对路径 base: './')
├── scripts/                    # 自动化工程脚本
│   └── prepare-build.js        # 双模打包预处理与资源同步工具
├── docs/                       # 架构设计与技术文档
│   ├── DUAL_MODE_PACKAGING_GUIDE.md          # 📖 在线/离线双模打包使用指南
│   ├── MOBILE_ELECTRON_FRAMEWORK_ARCHITECTURE.md # 📖 容器框架架构设计规范
│   ├── IOS_IMPLEMENTATION_BLUEPRINT.md       # 📖 iOS 端底座实现蓝图
│   ├── SDK_API_REFERENCE.md                  # 📖 前端 SDK 接口参考手册
│   └── NEXT_PHASE_ROADMAP.md                 # 📖 下一阶段产品演进路线图
└── .github/workflows/ci.yml    # GitHub Actions 自动化构建工作流
```

---

## 🛠️ 快速上手与命令指南

### 1. 编译 SDK 与 Web 前端
```bash
# 编译 TypeScript SDK
npm run build:sdk

# 编译 Web 控制台
npm run build:web
```

### 2. 双模打包切换操作

详细说明请参考 [docs/DUAL_MODE_PACKAGING_GUIDE.md](docs/DUAL_MODE_PACKAGING_GUIDE.md)。

#### 模式 A：打包为“本地离线包”App（资源打入安装包）
```bash
# 一键编译 SDK、Vite 相对路径产物，并同步至 Android/iOS 资产目录
npm run mode:local
# 或直接执行：node scripts/prepare-build.js --mode local

# 编译 Android Release APK
./gradlew assembleRelease
# （或者一步到位：npm run build:apk:local）
```

#### 模式 B：打包为“在线远程 URL”App
```bash
# 指定远程目标网页 URL
node scripts/prepare-build.js --mode online --url https://m.baidu.com
# 或：npm run mode:online

# 编译 Android Release APK
./gradlew assembleRelease
```

---

## 🤖 GitHub Actions CI/CD 自动化打包

在 GitHub 仓库中，进入 **Actions** -> 点击 **Mobile Electron CI (Build APK & IPA)** -> 点击 **Run workflow**：
* **`build_mode`**：选择 `local`（内置离线包）或 `online`（在线 URL）；
* **`online_url`**：若为在线模式，输入目标站点地址；
* 流水线会自动输出并归档对应模式的 Android APK 与 iOS IPA 安装包！

---

## 📖 核心文档索引

* [在线/本地双模打包使用指南](docs/DUAL_MODE_PACKAGING_GUIDE.md)
* [框架整体架构设计与产品规范](docs/MOBILE_ELECTRON_FRAMEWORK_ARCHITECTURE.md)
* [前端 SDK 接口使用规范手册](docs/SDK_API_REFERENCE.md)
* [iOS 原生容器实现蓝图](docs/IOS_IMPLEMENTATION_BLUEPRINT.md)
* [下一阶段演进路线图 (Roadmap)](docs/NEXT_PHASE_ROADMAP.md)

---

## 📄 License
MIT License
