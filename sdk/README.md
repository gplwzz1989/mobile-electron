# @mobile-electron/core

商业级 Mobile Electron 跨端容器前端开发 SDK，支持在移动端（Android & iOS）以全 Web/TypeScript 驱动原生多 Profile 物理沙箱、Cookie 强管控、网络穿透与系统硬件指纹虚拟化。

---

## 🌟 核心特性

- 🌐 **全 Web 界面驱动**：App 原生只作为极简微内核底座，所有界面完全交由配置的 Web 应用渲染；
- 🛡️ **多 Profile 物理沙箱隔离**：基于底层 Chromium `MULTI_PROFILE`，不同 Web 任务享有完全独立的 SQLite Cookie 数据库及 LocalStorage 分区；
- 🍪 **底层 Cookie 实时读写**：支持全局或针对特定 Profile 独立注入、提取、同步与清空 Cookie；
- 🥷 **硬件参数注入与 Stealth 防检测**：预置旗舰机指纹（CPU 核心、内存、WebGL GPU、屏幕），全自动 DocumentStart 注入与 Function.toString 伪装；
- ⚡ **CORS 穿透与抓包钩子**：利用原生底层网络穿透浏览器同源策略限制，支持全双工事件推送；
- 🪟 **沉浸式视窗与系统控制**：通过 SDK 动态控制沉浸式状态栏、全屏切换、退出应用、原生 Toast 与剪贴板；
- 💻 **现代化开发体验**：同时提供 ESM、CommonJS 与 UMD 全局脚本产物，内置 Web 降级 Mock 仿真，PC 浏览器 `npm run dev` 零报错。

---

## 📦 安装与引入

### 方式 1：npm / pnpm / yarn 安装（推荐前端工程化项目）

```bash
npm install @mobile-electron/core
# or
yarn add @mobile-electron/core
# or
pnpm add @mobile-electron/core
```

在 Vue / React / Vite / Next.js 中引用：

```typescript
import electron, { Page } from '@mobile-electron/core';

// 检查是否运行在移动端 Native 容器中
console.log('当前平台:', electron.platform); // 'android' | 'ios' | 'web-mock'
console.log('容器运行状态:', electron.isNativeContainer);
```

### 方式 2：HTML `<script>` 标签直接引入 (离线包 / 传统 H5)

```html
<script src="./dist/mobile-electron-sdk.js"></script>
<script>
    // 挂载在全局 window.MobileElectron 或 window.mobileElectron
    const electron = window.MobileElectron;
    electron.device.toast('欢迎使用 Mobile Electron 容器！');
</script>
```

---

## 🚀 快速上手示例

### 1. 视窗与沉浸式状态栏控制 (`electron.window`)

```typescript
// 设置沉浸式白底黑字状态栏
await electron.window.setStatusBar({
    color: '#FFFFFF',
    darkIcons: true,
    immersive: true
});

// 切换全屏模式
await electron.window.setFullscreen(true);

// 历史记录后退
await electron.window.goBack();
```

### 2. Cookie 实时读写与隔离 (`electron.cookie`)

```typescript
// 1. 获取指定网站的完整 Cookie
const cookies = await electron.cookie.get('https://example.com');
console.log('当前 Cookies:', cookies);

// 2. 导入写入 Cookie (支持单条或批量分号隔开)
await electron.cookie.set('https://example.com', 'session_id=abc12345; user=alice');

// 3. 针对特定独立 Profile 分区操作
await electron.cookie.set('https://example.com', 'token=xyz', { profile: 'account_alpha' });
```

### 3. 多 Profile 独立任务与页面管理 (`electron.browser`)

```typescript
// 创建一个后台静默运行的独立 Profile 页面 (数据完全物理隔离)
const page = await electron.browser.newPage({
    url: 'https://m.douyin.com',
    headless: true,
    profile: 'account_beta',
    hardwarePreset: 'flagship' // 自动注入 8核/16G/骁龙8 GPU 伪装
});

// 在目标页面上下文执行脚本
const title = await page.evaluate<string>('document.title');

// 将该后台页面无缝切换到前台全屏展示
await page.bringToFront();

// 获取当前页面的隔离 Cookie
const pageCookies = await page.getCookies();

// 销毁页面
await page.close();
```

### 4. 绕过 CORS 的底层网络请求 (`electron.network`)

```typescript
const resp = await electron.network.fetchNative({
    url: 'https://api.example.com/data',
    method: 'POST',
    headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ...'
    },
    body: JSON.stringify({ query: 'status' })
});

console.log('底层原生响应状态码:', resp.status);
console.log('响应内容:', resp.body);
```

### 5. 系统硬件与 App 控制 (`electron.device` & `electron.app`)

```typescript
// 原生 Toast 弹窗
await electron.device.toast('数据已成功同步！');

// 系统剪贴板
await electron.device.setClipboard('待复制的文本内容');
const clipText = await electron.device.getClipboard();

// 获取 App 容器运行时信息
const appInfo = await electron.app.getInfo();
console.log('应用名:', appInfo.appName, 'Chromium隔离支持:', appInfo.multiProfileSupported);

// 退出应用
await electron.app.exit();
```

---

## 🛠️ 本地开发与 Mock 模式

当您在本地电脑浏览器运行 `npm run dev`（非 Android/iOS 原生容器）时，SDK 会自动进入 **`web-mock`** 仿真模式：
- 避免抛出未定义异常导致页面空白崩溃；
- 控制台输出友好的黄色警告，指明当前调用的原生接口与入参；
- 自动返回合理的模拟响应，支持前端团队独立并行开发与 UI 调试。
