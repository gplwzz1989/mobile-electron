# @mobile-electron/cli

> **Mobile Electron 跨端框架官方统一命令行开发工具**

`@mobile-electron/cli` 提供了从开发环境诊断、热重载本地联调、一键构建与资源同步、离线热更差分包生成、到模拟器/真机直接拉起运行的全生命周期命令行工具支持。

---

## 🚀 安装与使用

### 全局安装 (推荐)
```bash
npm install -g @mobile-electron/cli
# 或使用 npm link 进行本地软链接开发
```

### 免安装临时执行
```bash
npx @mobile-electron/cli doctor
```

---

## 📋 常用核心命令

### 1. `mobile-electron doctor`
深度诊断本机跨端开发环境就绪状态（检查 Node.js、JDK、Android SDK、ADB 调试设备、macOS Xcode 及当前项目核心结构）。

```bash
me doctor
```

### 2. `mobile-electron build`
一键构建 @mobile-electron/core SDK、Web 前端应用，并同步产物至 Android Assets 与 iOS Bundle。
- `--mode [local|online]`：指定离线内置模式或在线地址模式（默认 local）
- `--bundle`：额外扫描生成 `.mep` 离线热更差分包及 `update-manifest.json`
- `--apk`：直接调用 Gradle 编译出 Android Debug APK 安装包

```bash
me build --mode local --bundle
```

### 3. `mobile-electron dev`
启动 Vite 本地开发服务器，并智能输出本机局域网 IP 与模拟器回环地址，便于真机和 Android/iOS 模拟器进行实时 HMR 联调。

```bash
me dev
```

### 4. `mobile-electron run <android|ios>`
自动检查连接设备，编译安装包并直接在移动设备或模拟器上启动应用。

```bash
me run android
```

### 5. `mobile-electron serve`
启动本地轻量级热更新差分分发服务与云控配置中心（默认端口 8088）。

```bash
me serve --port 8088
```

### 6. `mobile-electron config`
自检并对比 Android 与 iOS 双端 `app-config.json` 的配置状态，确保默认 URL、离线模式、安全域名白名单规则严格一致。

```bash
me config
```

### 7. `mobile-electron create <projectName>`
调用交互式脚手架初始化新的 Mobile Electron 跨端项目（支持 Vanilla-TS、Vue-TS、React-TS 模板）。

```bash
me create my-hybrid-app --template vue-ts
```

---

## 📄 开源许可
MIT License © Mobile Electron Team
