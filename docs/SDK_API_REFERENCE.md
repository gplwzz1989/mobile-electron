# @mobile-electron/core 前端 SDK 接口规范说明 (API Reference)

> **版本**：v2.0.0  
> **包名**：`@mobile-electron/core`  
> **UMD 全局对象**：`window.MobileElectron` / `window.mobileElectron`

---

## 1. 快速接入

### 1.1 前端工程安装 (npm / yarn / pnpm)

```bash
npm install @mobile-electron/core
```

```typescript
import electron from '@mobile-electron/core';

// 检查容器环境
if (electron.isNativeContainer) {
    console.log('运行在原生容器中，平台:', electron.platform);
} else {
    console.warn('当前运行在 Web Mock 仿真调试环境中');
}
```

### 1.2 传统 HTML / CDN 直接引用

```html
<script src="dist/mobile-electron-sdk.umd.js"></script>
<script>
    const electron = window.MobileElectron;
    electron.device.toast('SDK 加载成功！');
</script>
```

---

## 2. 模块详细接口说明

### 2.1 视窗与导航控制模块 (`electron.window`)

负责控制原生应用窗口、状态栏外观、沉浸式以及页面历史导航。

| 方法名 | 参数 | 返回值 | 说明 |
| :--- | :--- | :--- | :--- |
| `setStatusBar(options)` | `StatusBarOptions` | `Promise<boolean>` | 配置状态栏颜色、图标明暗与沉浸模式 |
| `setFullscreen(fullscreen)` | `boolean` (默认 `true`) | `Promise<boolean>` | 切换或设置是否全屏沉浸（隐藏状态栏） |
| `reload()` | 无 | `Promise<boolean>` | 重新加载当前前台页面 |
| `goBack()` | 无 | `Promise<boolean>` | 返回上一历史页面 |
| `canGoBack()` | 无 | `Promise<boolean>` | 查询当前页面是否能够后退 |
| `loadUrl(url)` | `string` | `Promise<boolean>` | 控制容器加载新 URL |
| `setDebugToolbarVisible(visible)` | `boolean` | `Promise<boolean>` | 显示/隐藏原生调试工具条（地址栏与底栏） |

#### 示例：
```typescript
// 沉浸式透明状态栏（黑色图标）
await electron.window.setStatusBar({
    color: '#FFFFFF',
    darkIcons: true,
    immersive: true
});

// 进入全屏模式
await electron.window.setFullscreen(true);
```

---

### 2.2 宿主应用系统模块 (`electron.app`)

获取宿主应用环境、配置与应用级生命周期。

| 方法名 | 参数 | 返回值 | 说明 |
| :--- | :--- | :--- | :--- |
| `getInfo()` | 无 | `Promise<AppInfo>` | 获取 App 应用名、版本、是否支持 Multi-Profile、配置等 |
| `getConfig()` | 无 | `Promise<Record<string, any>>` | 获取当前生效的 `app-config.json` 原始配置 |
| `exit()` | 无 | `Promise<void>` | 安全关闭并退出当前应用程序 |

#### 示例：
```typescript
const info = await electron.app.getInfo();
console.log('当前 App 版本:', info.version, 'Profile隔离支持:', info.multiProfileSupported);

// 退出应用
await electron.app.exit();
```

---

### 2.3 底层 Cookie 读写模块 (`electron.cookie`)

直接操作 Chromium 底层 SQLite 数据库的 Cookie，支持按域名及 Profile 分区隔离读写。

| 方法名 | 参数 | 返回值 | 说明 |
| :--- | :--- | :--- | :--- |
| `get(url, target?)` | `url: string, target?: { pageId?: string; profile?: string }` | `Promise<string>` | 读取指定 URL 的完整 Cookie 字符串 |
| `set(url, rawInput, target?)` | `url: string, rawInput: string, target?: { pageId?: string; profile?: string }` | `Promise<number>` | 批量或单条写入 Cookie，返回导入成功的键值对数量 |
| `clear(target?)` | `target?: { pageId?: string; profile?: string }` | `Promise<boolean>` | 清空全局或指定 Profile 分区的 Cookie |

#### 示例：
```typescript
// 读取指定 URL 的 Cookie
const cookieStr = await electron.cookie.get('https://m.baidu.com');

// 写入 Cookie 至默认 Profile
await electron.cookie.set('https://m.baidu.com', 'BAIDUID=XXXX; PSTM=123456');

// 写入 Cookie 至独立隔离的 Profile 'account_vip'
await electron.cookie.set('https://m.baidu.com', 'USER=VIP', { profile: 'account_vip' });
```

---

### 2.4 多页面与独立沙箱调度模块 (`electron.browser`)

创建、管理多 WebView，支持后台无头任务（Headless）与前后台无缝调度。

| 方法名 | 参数 | 返回值 | 说明 |
| :--- | :--- | :--- | :--- |
| `newPage(options?)` | `PageOptions` | `Promise<Page>` | 创建一个新页面，支持独立 Profile、硬件指纹与后台静默运行 |
| `current()` | 无 | `Page` | 获取当前主容器页面实例 |
| `getAllPages()` | 无 | `Promise<PageInfo[]>` | 获取当前容器中所有活跃页面及其 Profile 绑定列表 |
| `listProfiles()` | 无 | `Promise<string[]>` | 获取系统中所有已存在的 Profile 数据分区名称 |
| `deleteProfile(name)` | `string` | `Promise<boolean>` | 物理删除指定的独立 Profile 分区并清空持久化文件 |
| `switchToPage(pageId)` | `string` | `Promise<void>` | 将后台指定的 WebView 切换至前台全屏展示 |
| `startDouyinQrLogin()` | 无 | `Promise<{ success: boolean; message: string }>` | 启动抖音创作者中心隐形扫码登录与二维码监控 |

#### `Page` 实例成员方法：

- `page.goto(url, timeoutMs?)`: 导航至指定地址；
- `page.bringToFront()`: 将此页面切到前台展示；
- `page.sendToBack()`: 将此页面退至后台，切回主页；
- `page.getCookies()`: 获取此页面当前 URL 底层的 Cookie（自动匹配其所属 Profile）；
- `page.setCookies(cookies)`: 向此页面所属 Profile 注入 Cookie；
- `page.clearData()`: 清空此 Profile 的所有 Cookie 和本地存储；
- `page.extractQrCode(selector?)`: 从页面 DOM/Canvas 中抓取二维码 Base64 图片；
- `page.evaluate<T>(script)`: 在目标页面上下文中执行任意 JS 脚本；
- `page.getHardware()`: 获取该页面注入的硬件参数（CPU、内存、GPU）；
- `page.setHardware(hw)`: 动态重置该页面的硬件指纹；
- `page.onResponse(urlPattern, callback)`: 监听目标页面捕获的网络响应；
- `page.close()`: 销毁并释放该 WebView 原生内存。

#### 示例：
```typescript
// 创建独立账号沙箱
const page = await electron.browser.newPage({
    url: 'https://m.douyin.com',
    profile: 'account_douyin_01',
    headless: true,
    hardwarePreset: 'flagship'
});

// 在目标页面抓取二维码
const qrBase64 = await page.extractQrCode();

// 销毁页面
await page.close();
```

---

### 2.5 原生网络增强模块 (`electron.network`)

利用原生底层客户端发起 HTTP 请求，彻底绕过 Web CORS 同源策略限制。

| 方法名 | 参数 | 返回值 | 说明 |
| :--- | :--- | :--- | :--- |
| `fetchNative(options)` | `NativeRequestOptions` | `Promise<NativeResponse>` | 发起原生底层 HTTP 请求（绕过 CORS、Cookie 限制） |
| `onResponse(urlPattern, callback)` | `pattern: string, cb: Function` | `UnsubscribeFn` | 注册全局网络响应捕获监听器 |

#### 示例：
```typescript
const res = await electron.network.fetchNative({
    url: 'https://api.github.com/zen',
    method: 'GET'
});
console.log('状态码:', res.status, 'Body:', res.body);
```

---

### 2.6 原生设备与界面模块 (`electron.device`)

| 方法名 | 参数 | 返回值 | 说明 |
| :--- | :--- | :--- | :--- |
| `toast(message)` | `string` | `Promise<void>` | 弹出系统原生轻量 Toast 提示 |
| `getClipboard()` | 无 | `Promise<string>` | 读取系统剪贴板文本 |
| `setClipboard(text)` | `string` | `Promise<boolean>` | 将文本写入系统剪贴板 |

---

### 2.7 本地安全沙箱文件系统 (`electron.file` / `electron.fs`)

提供类似 Node.js `fs` 的移动端沙箱文件系统操控能力，支持文本与二进制读写、目录管理与后台大文件下载。

| 方法名 | 参数 | 返回值 | 说明 |
| :--- | :--- | :--- | :--- |
| `getPaths()` | 无 | `Promise<{ documents, cache, temp }>` | 获取沙箱三大核心目录系统物理绝对路径 |
| `write(options)` | `FileWriteOptions` | `Promise<{ success, bytesWritten, fullPath }>` | 写入文件（支持 UTF-8 / Base64 / 追加模式） |
| `read(options)` | `FileReadOptions` | `Promise<{ content, size, encoding }>` | 读取文件内容（返回文本或 Base64） |
| `writeText(path, text, dir?)` | `path: string, text: string, dir?: DirectoryType` | `Promise<string>` | 快捷写入 UTF-8 文本文件，返回完整路径 |
| `readText(path, dir?)` | `path: string, dir?: DirectoryType` | `Promise<string>` | 快捷读取 UTF-8 文本文件内容 |
| `exists(path, dir?)` | `path: string, dir?: DirectoryType` | `Promise<{ exists, isFile, isDirectory }>` | 检查文件或目录是否存在 |
| `delete(path, dir?)` | `path: string, dir?: DirectoryType` | `Promise<boolean>` | 删除文件或递归删除目录 |
| `list(path?, dir?)` | `path?: string, dir?: DirectoryType` | `Promise<FileInfo[]>` | 遍历指定目录下的文件与文件夹元信息 |
| `mkdir(path, dir?)` | `path: string, dir?: DirectoryType` | `Promise<boolean>` | 递归创建目录 |
| `download(options)` | `FileDownloadOptions` | `Promise<{ success, size, fullPath }>` | 原生后台下载网络大文件并存入沙箱 |

#### 示例：
```typescript
// 1. 写入本地离线配置文件
await electron.fs.writeText('config.json', JSON.stringify({ token: 'abc' }));

// 2. 读取文件
const content = await electron.fs.readText('config.json');

// 3. 原生下载文件
await electron.fs.download({
    url: 'https://example.com/data.zip',
    destinationPath: 'cache_data.zip',
    directory: 'cache'
});
```

---

### 2.8 原生持久化键值存储 (`electron.storage`)

突破 Web 端 `localStorage` 5MB 大小限制，跨 Profile 共享，持久化安全存储企业配置与 Token。

| 方法名 | 参数 | 返回值 | 说明 |
| :--- | :--- | :--- | :--- |
| `set(key, value)` | `key: string, value: string` | `Promise<boolean>` | 保存字符串键值 |
| `get(key)` | `key: string` | `Promise<string \| null>` | 读取字符串键值 |
| `setObject(key, obj)` | `key: string, obj: any` | `Promise<boolean>` | 快捷保存 JSON 对象 |
| `getObject(key)` | `key: string` | `Promise<T \| null>` | 快捷读取 JSON 对象 |
| `remove(key)` | `key: string` | `Promise<boolean>` | 删除指定键 |
| `clear()` | 无 | `Promise<boolean>` | 清空所有键值 |
| `keys()` | 无 | `Promise<string[]>` | 获取全部已存储的键名列表 |

---

### 2.9 原生弹窗与对话框交互 (`electron.dialog`)

提供非阻塞的原生 Material (Android) 与 Cupertino (iOS) 风格对话框。

| 方法名 | 参数 | 返回值 | 说明 |
| :--- | :--- | :--- | :--- |
| `alert(message, title?)` | `string \| AlertOptions` | `Promise<boolean>` | 原生提示弹窗 |
| `confirm(message, title?)` | `string \| ConfirmOptions` | `Promise<boolean>` | 原生确认选择弹窗（返回 true/false） |
| `prompt(message, defaultVal?)` | `string \| PromptOptions` | `Promise<string \| null>` | 原生文本输入弹窗（取消返回 null） |

#### 示例：
```typescript
// 原生确认对话框
const ok = await electron.dialog.confirm('是否确定提交订单？', '系统确认');
if (ok) {
    const remark = await electron.dialog.prompt('请输入订单备注：');
}
```

---

## 3. Web 开发环境与 Mock 仿真调试

当在本地浏览器环境（例如 `http://localhost:5173`）调试时：
- SDK 会自动识别非容器环境，将 `electron.platform` 标记为 `'web-mock'`；
- 调用接口不会发生未捕获异常崩溃；
- 控制台将打印黄色提示：`[MobileElectron Mock] (module.action) called outside of native container.`，并返回合理的模拟数据；
- 极大提升前端开发者“纯浏览器开发、无缝真机运行”的开发体验。
