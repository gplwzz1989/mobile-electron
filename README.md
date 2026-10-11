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
2. **离线包增量差分热更与前台静默更新闭环**：
   - 基于 SHA-256 细粒度资源清单比对（Diff Engine），只下载修改与新增文件，省流 90%+；
   - 切回前台自动触发静默防抖检查与拉取，`onUpdateReady` 派发就绪通知，下次启动或点击无缝秒级生效。
3. **应用内画中画悬浮多窗口 (Floating Overlay Window)**：
   - 移动端单屏创新多窗口方案，内嵌独立 WebView，支持手指自由拖拽、屏幕边缘自动阻尼吸附、折叠为浮标小球与跨窗口 JS 通信；
   - **100% 免敏感高危权限**，直接在宿主 Activity / UIViewController 根容器上渲染，完全符合各大应用商店审核标准。
4. **多 Profile 物理级数据隔离**：
   - 底层利用 Chromium `androidx.webkit.ProfileStore` 与 iOS `WKWebsiteDataStore`；
   - 支持同时静默运行多个相互物理隔离的 WebView 页面，各自拥有独立的 SQLite Cookie 数据库及 LocalStorage，绝无串号泄漏风险。
5. **旗舰级硬件指纹注入与反反爬 (Stealth Engine)**：
   - 底层 `.atDocumentStart` 毫秒级注入伪装 CPU 核心、内存、GPU 渲染器、屏幕参数与 Web Audio 浮点噪声；
   - 原型链深度保护，轻松穿透现代商业级风控设备采集。
6. **统一 JSON-RPC 2.0 桥接协议与 14 大原生系统特权插件**：
   - 包含窗口与画中画、Cookie/Storage、网络与生命周期感知、文件读写、生物认证 FaceID/指纹、触感震动、相机扫码、动态 TabBar 等。
7. **全栈脚手架与自动化测试套件**：
   - 自带 `@mobile-electron/cli`（支持 `doctor` 跨端环境体检）；
   - 自动化测试套件 100% 覆盖协议层、SDK 接口、热更差分算法及白名单沙箱。

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
├── packages/                   # 跨端工具包 (@mobile-electron/cli 等)
├── tests/                      # 全栈自动化测试套件 (协议层、SDK、热更差分、白名单)
├── scripts/                    # 自动化工程脚本 (双模打包、热更打包分发)
├── docs/                       # 架构设计与技术文档
│   ├── ARCHITECTURE_AND_DEVELOPER_GUIDE.md  # 📖 全景架构与开发者实战指南 (必读)
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

### 3. 一键真机 / 模拟器部署安装与热跑 (Run & DevTools)
```bash
# 自动捕获在线设备/模拟器端口，编译并安装 APK，自动启动并开启 Chrome DevTools 映射
npm run run:android

# 极速热跑（跳过编译直接复用现有 APK 安装）:
npx mobile-electron run android --skip-build

# 启动并实时查看原生 Logcat 日志:
npx mobile-electron run android --log
```

### 4. NPM 生态分发与离线包归档 (NPM Ecosystem Packaging)
```bash
# 一键打包核心 SDK、CLI 工具及脚手架为规范的离线 tarball (.tgz)
npm run pack:all

# 打包产物将自动归档至 release/npm/：
# - release/npm/mobile-electron-core-2.0.0.tgz
# - release/npm/mobile-electron-cli-2.0.0.tgz
# - release/npm/create-mobile-electron-app-2.0.0.tgz
```

---

## 🤖 GitHub Actions CI/CD 自动化打包与质量门禁

项目内置全自动化的工业级 CI/CD 流水线（`.github/workflows/ci.yml`）：
1. **🧪 自动化单元测试门禁 (`test`)**：在 Android 与 iOS 编译前运行全套自动化测试，100% 通过方可继续，阻断任何破坏性改动；
2. **🌐 Web & SDK 资产构建 (`build-web`)**：自动构建 TS SDK 与 Vite 产物；
3. **📦 NPM 生态包打包归档 (`pack-npm`)**：自动生成 3 个子包的 `.tgz` 离线分发包并上传为 Artifact；
4. **🤖 Android APK 构建 (`build-android`)**：编译并归档 Debug & Release APK；
5. **🍎 iOS IPA 构建 (`build-ios`)**：基于 macOS-14 + XcodeGen 全自动编译出免签 IPA；
6. **🚀 GitHub Release 自动发布 (`release`)**：打 Tag（如 `v2.0.0`）自动归档发布 APK、IPA 与 NPM 离线包。

**手动触发双模打包**：
在 GitHub 仓库中，进入 **Actions** -> 点击 **Mobile Electron CI (Build APK & IPA)** -> 点击 **Run workflow**：
* **`build_mode`**：选择 `local`（内置离线包）或 `online`（在线 URL）；
* **`online_url`**：若为在线模式，输入目标站点地址。

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
