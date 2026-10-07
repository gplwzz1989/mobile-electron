// src/mock.ts
var MockEngine = class {
  static mockInvoke(module, action, params = {}) {
    console.warn(`[MobileElectron Mock] (${module}.${action}) called outside of native container. Returning mock fallback.`, params);
    switch (`${module}.${action}`) {
      case "app.getInfo":
        return Promise.resolve({
          appName: "Mobile Electron Web App (Dev Mode)",
          appId: "com.example.mockapp",
          version: "2.0.0-mock",
          framework: "MobileElectron-WebFallback",
          frameworkVersion: "2.0.0",
          multiProfileSupported: false,
          defaultUrl: typeof window !== "undefined" ? window.location.href : "",
          config: {}
        });
      case "app.getConfig":
        return Promise.resolve({});
      case "app.exit":
        alert("[Mock App Exit] exit() called");
        return Promise.resolve({ success: true });
      case "window.setStatusBar":
      case "window.setFullscreen":
      case "window.setDebugToolbarVisible":
        return Promise.resolve({ success: true });
      case "window.goBack":
        if (typeof window !== "undefined" && window.history) {
          window.history.back();
        }
        return Promise.resolve({ success: true });
      case "window.canGoBack":
        return Promise.resolve({ canGoBack: typeof window !== "undefined" && window.history.length > 1 });
      case "window.reload":
        if (typeof window !== "undefined") {
          window.location.reload();
        }
        return Promise.resolve({ success: true });
      case "window.loadUrl":
        if (typeof window !== "undefined" && params.url) {
          window.location.href = params.url;
        }
        return Promise.resolve({ success: true, url: params.url });
      case "dialog.alert":
        if (typeof window !== "undefined") {
          window.alert(params.message || "");
        }
        return Promise.resolve({ confirmed: true });
      case "dialog.confirm":
        const conf = typeof window !== "undefined" ? window.confirm(params.message || "") : true;
        return Promise.resolve({ confirmed: conf });
      case "dialog.prompt":
        const promptVal = typeof window !== "undefined" ? window.prompt(params.message || "", params.defaultValue || "") : null;
        return Promise.resolve({ confirmed: promptVal !== null, value: promptVal });
      case "storage.set":
        if (typeof localStorage !== "undefined" && params.key) {
          localStorage.setItem("me_" + params.key, params.value || "");
        }
        return Promise.resolve({ success: true });
      case "storage.get":
        const sVal = typeof localStorage !== "undefined" && params.key ? localStorage.getItem("me_" + params.key) : null;
        return Promise.resolve({ value: sVal, exists: sVal !== null });
      case "storage.remove":
        if (typeof localStorage !== "undefined" && params.key) {
          localStorage.removeItem("me_" + params.key);
        }
        return Promise.resolve({ success: true });
      case "storage.clear":
        if (typeof localStorage !== "undefined") {
          Object.keys(localStorage).filter((k) => k.startsWith("me_")).forEach((k) => localStorage.removeItem(k));
        }
        return Promise.resolve({ success: true });
      case "storage.keys":
        const sKeys = typeof localStorage !== "undefined" ? Object.keys(localStorage).filter((k) => k.startsWith("me_")).map((k) => k.replace("me_", "")) : [];
        return Promise.resolve({ keys: sKeys });
      case "file.getPaths":
        return Promise.resolve({ documents: "/mock/documents", cache: "/mock/cache", temp: "/mock/temp" });
      case "file.write":
        return Promise.resolve({ success: true, bytesWritten: (params.content || "").length, fullPath: "/mock/documents/" + params.path });
      case "file.read":
        return Promise.resolve({ content: "Mock file content for " + params.path, size: 24, encoding: params.encoding || "utf8" });
      case "file.exists":
        return Promise.resolve({ exists: true, isFile: true, isDirectory: false });
      case "file.delete":
      case "file.mkdir":
      case "file.download":
        return Promise.resolve({ success: true, size: 1024, fullPath: "/mock/documents/" + (params.destinationPath || "") });
      case "file.list":
        return Promise.resolve({ files: [{ name: "sample.txt", isDirectory: false, size: 100, lastModified: Date.now() }] });
      case "device.toast":
        console.log(`[Toast]: ${params.message}`);
        return Promise.resolve({});
      case "device.getClipboard":
        return Promise.resolve({ text: "" });
      case "device.setClipboard":
        return Promise.resolve({ success: true });
      case "cookie.get":
        return Promise.resolve({
          cookies: typeof document !== "undefined" ? document.cookie : "",
          profile: params.profile || "default"
        });
      case "cookie.import":
      case "cookie.set":
        if (typeof document !== "undefined" && params.input) {
          document.cookie = params.input;
        }
        return Promise.resolve({ count: 1, profile: params.profile || "default" });
      case "cookie.clear":
        return Promise.resolve({ success: true, profile: params.profile || "default" });
      case "network.fetch":
        return Promise.resolve({
          status: 200,
          headers: { "content-type": "application/json" },
          body: JSON.stringify({ message: "Mock native response", params })
        });
      case "page.create":
        return Promise.resolve({
          pageId: "page_mock_" + Date.now(),
          profile: params.profile || "default",
          url: params.url || "",
          multiProfileSupported: false,
          hardware: params.hardware || {}
        });
      case "page.list":
        return Promise.resolve({
          pages: [
            { pageId: "main", url: typeof window !== "undefined" ? window.location.href : "", title: "Mock Main", isHeadless: false, isForeground: true, profile: "default" }
          ],
          multiProfileSupported: false
        });
      case "page.listProfiles":
        return Promise.resolve({ profiles: ["default"] });
      case "page.deleteProfile":
      case "page.bringToFront":
      case "page.sendToBack":
      case "page.clearData":
      case "page.close":
        return Promise.resolve({ success: true });
      case "page.startDouyinQrLogin":
        return Promise.resolve({ success: true, message: "Mock Douyin QR Login Triggered" });
      case "page.extractQrCode":
        return Promise.resolve({ base64: "" });
      case "page.evaluate":
        return Promise.resolve({ result: null });
      default:
        return Promise.resolve({ success: true, mock: true });
    }
  }
};
MockEngine.isMockEnabled = true;

// src/client.ts
var Page = class {
  constructor(pageId, isHeadless, profile = "default", client, hardware) {
    this.pageId = pageId;
    this.isHeadless = isHeadless;
    this.profile = profile;
    this.client = client;
    this.hardware = hardware;
  }
  /**
   * 获取当前页面被注入的硬件参数与设备指纹 (CPU核心数/内存/GPU/屏幕)
   */
  async getHardware() {
    const res = await this.client.invoke("page", "getHardware", { pageId: this.pageId });
    return res.hardware;
  }
  /**
   * 动态修改或重新注入当前页面的硬件参数
   */
  async setHardware(hw) {
    const res = await this.client.invoke("page", "setHardware", { pageId: this.pageId, hardware: hw });
    return res.success;
  }
  /**
   * 导航至指定 URL，等待加载完成
   */
  async goto(url, timeoutMs = 15e3) {
    return this.client.invoke("page", "goto", { pageId: this.pageId, url, timeoutMs });
  }
  /**
   * 将该页面从后台静默运行状态无缝切换至前台全屏展示
   */
  async bringToFront() {
    return this.client.invoke("page", "bringToFront", { pageId: this.pageId });
  }
  /**
   * 将当前页面退回后台隐形运行，切回主页
   */
  async sendToBack() {
    return this.client.invoke("page", "sendToBack", { pageId: this.pageId });
  }
  /**
   * 获取当前页面底层的完整 Cookie (自动精准匹配其独立 Profile 分区)
   */
  async getCookies() {
    const res = await this.client.invoke("page", "getCookies", { pageId: this.pageId });
    return res.cookies;
  }
  /**
   * 导入并设置当前页面的 Cookie (精准写入其独立 Profile 分区，不污染其他页面)
   */
  async setCookies(cookies) {
    const res = await this.client.invoke("page", "setCookies", { pageId: this.pageId, cookies });
    return res.success;
  }
  /**
   * 清空当前页面对应 Profile 的所有 Cookie 和本地存储 (LocalStorage / IndexedDB)
   */
  async clearData() {
    await this.client.invoke("page", "clearData", { pageId: this.pageId });
  }
  /**
   * 从当前页面渲染的 DOM / Canvas 中抓取二维码图片（返回 Base64 Data URL）
   */
  async extractQrCode(selector) {
    const res = await this.client.invoke("page", "extractQrCode", {
      pageId: this.pageId,
      selector: selector || 'img[src*="qr"], img.qrcode, canvas.qrcode, #qrcode img, #qrcode canvas'
    });
    return res.base64;
  }
  /**
   * 在目标页面上下文执行任意 JavaScript 脚本并取回返回值
   */
  async evaluate(script) {
    const res = await this.client.invoke("page", "evaluate", { pageId: this.pageId, script });
    return res.result;
  }
  /**
   * 监听当前页面触发的网络响应
   */
  onResponse(urlPattern, callback) {
    return this.client.on("network:responseCaptured", (evt) => {
      if (evt.pageId === this.pageId && (!urlPattern || evt.url.includes(urlPattern))) {
        callback(evt);
      }
    });
  }
  /**
   * 关闭并从原生内存中销毁该 WebView 实例
   */
  async close() {
    return this.client.invoke("page", "close", { pageId: this.pageId });
  }
};
var MobileElectronClient = class {
  constructor() {
    this.pendingRequests = /* @__PURE__ */ new Map();
    this.eventListeners = /* @__PURE__ */ new Map();
    this.platform = this.detectPlatform();
    this.initBridgeListener();
  }
  get currentPlatform() {
    return this.platform;
  }
  get isRunningInContainer() {
    return this.platform === "android" || this.platform === "ios";
  }
  detectPlatform() {
    if (typeof window === "undefined") return "unknown";
    if (window.AndroidBridge) return "android";
    if (window.webkit?.messageHandlers?.NativeBridge) return "ios";
    return "web-mock";
  }
  initBridgeListener() {
    if (typeof window === "undefined") return;
    window.__onNativeRpcResponse = (responseJson) => {
      try {
        const response = typeof responseJson === "string" ? JSON.parse(responseJson) : responseJson;
        const pending = this.pendingRequests.get(response.id);
        if (pending) {
          clearTimeout(pending.timer);
          this.pendingRequests.delete(response.id);
          if (response.code === 200) {
            pending.resolve(response.data);
          } else {
            pending.reject(new Error(`[NativeError ${response.code}] ${response.message}`));
          }
        }
      } catch (err) {
        console.error("[MobileElectron] Failed to parse native response:", err);
      }
    };
    window.__onNativeRpcEvent = (eventJson) => {
      try {
        const event = typeof eventJson === "string" ? JSON.parse(eventJson) : eventJson;
        const listeners = this.eventListeners.get(event.event);
        if (listeners) {
          listeners.forEach((fn) => fn(event.data));
        }
      } catch (err) {
        console.error("[MobileElectron] Failed to parse native event:", err);
      }
    };
  }
  invoke(module, action, params = {}, timeoutMs = 15e3) {
    if (!this.isRunningInContainer) {
      return MockEngine.mockInvoke(module, action, params);
    }
    return new Promise((resolve, reject) => {
      const reqId = "rpc_" + Date.now() + "_" + Math.random().toString(36).substr(2, 8);
      const request = {
        id: reqId,
        module,
        action,
        params,
        timestamp: Date.now()
      };
      const timer = setTimeout(() => {
        this.pendingRequests.delete(reqId);
        reject(new Error(`[NativeTimeout] RPC call ${module}.${action} timed out after ${timeoutMs}ms`));
      }, timeoutMs);
      this.pendingRequests.set(reqId, { resolve, reject, timer });
      const payloadStr = JSON.stringify(request);
      if (this.platform === "android") {
        window.AndroidBridge.dispatch(payloadStr);
      } else if (this.platform === "ios") {
        window.webkit.messageHandlers.NativeBridge.postMessage(payloadStr);
      }
    });
  }
  on(eventName, listener) {
    if (!this.eventListeners.has(eventName)) {
      this.eventListeners.set(eventName, /* @__PURE__ */ new Set());
    }
    this.eventListeners.get(eventName).add(listener);
    return () => {
      const listeners = this.eventListeners.get(eventName);
      if (listeners) {
        listeners.delete(listener);
      }
    };
  }
};

// src/modules/browser.ts
var BrowserModule = class {
  constructor(client) {
    this.client = client;
  }
  /**
   * 创建一个全新的页面实例
   * @param options.profile 指定独立 Profile 目录名称（例如 'account_alpha'），享有独立 Cookie 和存储
   * @param options.headless 是否后台静默运行
   */
  async newPage(options = { headless: true, profile: "default" }) {
    const res = await this.client.invoke("page", "create", options);
    return new Page(res.pageId, options.headless ?? true, res.profile || "default", this.client, res.hardware);
  }
  /**
   * 获取当前容器所在的主页面实例
   */
  current() {
    return new Page("main", false, "default", this.client);
  }
  /**
   * 获取容器内所有正在运行的页面及其 Profile 绑定情况
   */
  async getAllPages() {
    const res = await this.client.invoke("page", "list");
    return res.pages;
  }
  /**
   * 获取所有现存的 Profile 数据目录列表
   */
  async listProfiles() {
    const res = await this.client.invoke("page", "listProfiles");
    return res.profiles;
  }
  /**
   * 删除指定的独立 Profile 分区并清空其持久化数据
   */
  async deleteProfile(profileName) {
    const res = await this.client.invoke("page", "deleteProfile", { profile: profileName });
    return res.success;
  }
  /**
   * 将指定后台页面切换至前台全屏展示
   */
  async switchToPage(pageId) {
    return this.client.invoke("page", "bringToFront", { pageId });
  }
  /**
   * 启动抖音创作者中心 (creator.douyin.com) 隐形 WebView 扫码登录与二维码双向状态监控
   */
  async startDouyinQrLogin() {
    return this.client.invoke("page", "startDouyinQrLogin", {});
  }
};

// src/modules/cookie.ts
var CookieModule = class {
  constructor(client) {
    this.client = client;
  }
  /**
   * 读取指定 URL 下的 Cookie 字符串
   * @param url 目标网站 URL
   * @param target 可选指定读取哪个 pageId 或 profile 独立分区
   */
  async get(url, target) {
    const res = await this.client.invoke("cookie", "get", {
      url,
      pageId: target?.pageId,
      profile: target?.profile
    });
    return res.cookies;
  }
  /**
   * 导入或设置 Cookie
   * @param url 目标网站 URL
   * @param rawInput 支持格式: "key=val; key2=val2" 或 JSON 数组形式
   * @param target 可选指定写入哪个 pageId 或 profile 独立分区
   * @returns 成功设置的 Cookie 键值对数量
   */
  async set(url, rawInput, target) {
    const res = await this.client.invoke("cookie", "import", {
      url,
      input: rawInput,
      pageId: target?.pageId,
      profile: target?.profile
    });
    return res.count;
  }
  /**
   * 清空指定分区或全局 Cookie
   */
  async clear(target) {
    const res = await this.client.invoke("cookie", "clear", {
      pageId: target?.pageId,
      profile: target?.profile
    });
    return res.success;
  }
};

// src/modules/network.ts
var NetworkModule = class {
  constructor(client) {
    this.client = client;
  }
  /**
   * 通过原生底层 HTTP 客户端发起请求，彻底绕过浏览器 CORS 跨域限制与请求头屏蔽
   */
  async fetchNative(options) {
    return this.client.invoke("network", "fetch", options);
  }
  /**
   * 全局监听拦截到的网络响应（如用于捕获二维码轮询结果或特定业务 API）
   */
  onResponse(urlPattern, callback) {
    return this.client.on("network:responseCaptured", (evt) => {
      if (!urlPattern || evt.url.includes(urlPattern)) {
        callback(evt);
      }
    });
  }
};

// src/modules/device.ts
var DeviceModule = class {
  constructor(client) {
    this.client = client;
  }
  /**
   * 读取原生系统剪贴板文本
   */
  async getClipboard() {
    const res = await this.client.invoke("device", "getClipboard");
    return res.text;
  }
  /**
   * 将文本写入原生系统剪贴板
   */
  async setClipboard(text) {
    const res = await this.client.invoke("device", "setClipboard", { text });
    return res.success;
  }
  /**
   * 弹出原生轻量提示 (Toast)
   */
  async toast(message) {
    return this.client.invoke("device", "toast", { message });
  }
};

// src/modules/window.ts
var WindowModule = class {
  constructor(client) {
    this.client = client;
  }
  /**
   * 设置原生状态栏样式
   * @param options.color 状态栏颜色十六进制，例如 '#FFFFFF'
   * @param options.darkIcons 图标/文字是否深色
   * @param options.immersive 是否沉浸式无边框（内容延伸至状态栏下）
   */
  async setStatusBar(options) {
    const res = await this.client.invoke("window", "setStatusBar", options);
    return res.success;
  }
  /**
   * 切换或设置全屏模式（隐藏/显示系统状态栏与虚拟导航栏）
   */
  async setFullscreen(fullscreen = true) {
    const res = await this.client.invoke("window", "setFullscreen", { fullscreen });
    return res.success;
  }
  /**
   * 重新加载当前前台活跃页面
   */
  async reload() {
    const res = await this.client.invoke("window", "reload");
    return res.success;
  }
  /**
   * 后退至上一历史记录
   */
  async goBack() {
    const res = await this.client.invoke("window", "goBack");
    return res.success;
  }
  /**
   * 查询是否可以后退
   */
  async canGoBack() {
    const res = await this.client.invoke("window", "canGoBack");
    return res.canGoBack;
  }
  /**
   * 控制当前前台窗口加载指定 URL
   */
  async loadUrl(url) {
    const res = await this.client.invoke("window", "loadUrl", { url });
    return res.success;
  }
  /**
   * 显式开启或关闭原生调试工具栏（地址栏/底栏）
   */
  async setDebugToolbarVisible(visible) {
    const res = await this.client.invoke("window", "setDebugToolbarVisible", { visible });
    return res.success;
  }
};

// src/modules/app.ts
var AppModule = class {
  constructor(client) {
    this.client = client;
  }
  /**
   * 获取当前 App 容器运行时详细信息
   */
  async getInfo() {
    return this.client.invoke("app", "getInfo");
  }
  /**
   * 获取框架当前加载的应用配置 (app-config.json)
   */
  async getConfig() {
    return this.client.invoke("app", "getConfig");
  }
  /**
   * 退出当前应用程序
   */
  async exit() {
    await this.client.invoke("app", "exit");
  }
};

// src/modules/file.ts
var FileModule = class {
  constructor(client) {
    this.client = client;
  }
  /**
   * 获取应用安全沙箱的基础路径（documents, cache, temp）
   */
  async getPaths() {
    return this.client.invoke("file", "getPaths");
  }
  /**
   * 写入文件（支持 UTF-8 文本或 Base64 二进制数据）
   */
  async write(options) {
    return this.client.invoke("file", "write", options);
  }
  /**
   * 读取文件内容（支持返回文本字符串或 Base64）
   */
  async read(options) {
    return this.client.invoke("file", "read", options);
  }
  /**
   * 快捷写入 UTF-8 文本文件
   */
  async writeText(path, text, directory = "documents") {
    const res = await this.write({ path, content: text, encoding: "utf8", directory });
    return res.fullPath;
  }
  /**
   * 快捷读取 UTF-8 文本文件
   */
  async readText(path, directory = "documents") {
    const res = await this.read({ path, encoding: "utf8", directory });
    return res.content;
  }
  /**
   * 检查文件或目录是否存在
   */
  async exists(path, directory = "documents") {
    return this.client.invoke("file", "exists", { path, directory });
  }
  /**
   * 删除文件或递归删除目录
   */
  async delete(path, directory = "documents") {
    const res = await this.client.invoke("file", "delete", { path, directory });
    return res.success;
  }
  /**
   * 列出指定目录下的文件与子目录列表
   */
  async list(path = "", directory = "documents") {
    const res = await this.client.invoke("file", "list", { path, directory });
    return res.files;
  }
  /**
   * 创建目录
   */
  async mkdir(path, directory = "documents") {
    const res = await this.client.invoke("file", "mkdir", { path, directory });
    return res.success;
  }
  /**
   * 原生后台下载网络文件并保存至指定沙箱路径
   */
  async download(options) {
    return this.client.invoke("file", "download", options);
  }
};

// src/modules/storage.ts
var StorageModule = class {
  constructor(client) {
    this.client = client;
  }
  /**
   * 保存键值数据
   */
  async set(key, value) {
    const res = await this.client.invoke("storage", "set", { key, value });
    return res.success;
  }
  /**
   * 读取键值数据（未找到返回 null）
   */
  async get(key) {
    const res = await this.client.invoke("storage", "get", { key });
    return res.value;
  }
  /**
   * 快捷保存 JSON 对象
   */
  async setObject(key, object) {
    return this.set(key, JSON.stringify(object));
  }
  /**
   * 快捷读取 JSON 对象
   */
  async getObject(key) {
    const str = await this.get(key);
    if (!str) return null;
    try {
      return JSON.parse(str);
    } catch {
      return null;
    }
  }
  /**
   * 删除指定键
   */
  async remove(key) {
    const res = await this.client.invoke("storage", "remove", { key });
    return res.success;
  }
  /**
   * 清空全部持久化键值
   */
  async clear() {
    const res = await this.client.invoke("storage", "clear");
    return res.success;
  }
  /**
   * 获取所有键名列表
   */
  async keys() {
    const res = await this.client.invoke("storage", "keys");
    return res.keys;
  }
};

// src/modules/dialog.ts
var DialogModule = class {
  constructor(client) {
    this.client = client;
  }
  async alert(arg1, title, buttonText) {
    const options = typeof arg1 === "string" ? { message: arg1, title: title || "\u63D0\u793A", buttonText: buttonText || "\u786E\u5B9A" } : arg1;
    const res = await this.client.invoke("dialog", "alert", options);
    return res.confirmed;
  }
  async confirm(arg1, title) {
    const options = typeof arg1 === "string" ? { message: arg1, title: title || "\u8BF7\u786E\u8BA4", confirmText: "\u786E\u8BA4", cancelText: "\u53D6\u6D88" } : arg1;
    const res = await this.client.invoke("dialog", "confirm", options);
    return res.confirmed;
  }
  async prompt(arg1, defaultValue, title) {
    const options = typeof arg1 === "string" ? { message: arg1, defaultValue: defaultValue || "", title: title || "\u8BF7\u8F93\u5165" } : arg1;
    const res = await this.client.invoke("dialog", "prompt", options);
    return res.confirmed ? res.value : null;
  }
};

// src/index.ts
var MobileElectron = class {
  constructor() {
    this.client = new MobileElectronClient();
    this.browser = new BrowserModule(this.client);
    this.cookie = new CookieModule(this.client);
    this.network = new NetworkModule(this.client);
    this.device = new DeviceModule(this.client);
    this.window = new WindowModule(this.client);
    this.app = new AppModule(this.client);
    this.file = new FileModule(this.client);
    this.fs = this.file;
    this.storage = new StorageModule(this.client);
    this.dialog = new DialogModule(this.client);
  }
  /** 当前运行平台: 'android' | 'ios' | 'web-mock' */
  get platform() {
    return this.client.currentPlatform;
  }
  /** 是否真正运行在移动端 Native 容器内部 */
  get isNativeContainer() {
    return this.client.isRunningInContainer;
  }
  /** 统一监听原生推送事件 */
  on(eventName, listener) {
    return this.client.on(eventName, listener);
  }
};
var electron = new MobileElectron();
var mobileElectron = electron;
if (typeof window !== "undefined") {
  window.MobileElectron = electron;
  window.mobileElectron = electron;
}
var index_default = electron;
export {
  AppModule,
  BrowserModule,
  CookieModule,
  DeviceModule,
  DialogModule,
  FileModule,
  MobileElectron,
  MobileElectronClient,
  MockEngine,
  NetworkModule,
  Page,
  StorageModule,
  WindowModule,
  index_default as default,
  electron,
  mobileElectron
};
//# sourceMappingURL=index.esm.js.map
