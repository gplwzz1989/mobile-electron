/**
 * @file mobile-electron-sdk.ts
 * @description 商业级 Mobile Electron 容器前端 TypeScript SDK (支持多 Profile 数据完全隔离)
 * @version 1.2.0
 * 
 * 适用于在任意 Web/H5/Vue/React 应用中以类型安全的方式操控移动端原生 WebView、
 * Cookie、网络拦截、多 Profile 数据目录隔离以及后台无头页面前后台自由切换。
 */

export type Platform = 'android' | 'ios' | 'unknown';

export interface RpcRequestEnvelope {
    id: string;
    module: string;
    action: string;
    params: Record<string, any>;
    timestamp: number;
}

export interface RpcResponseEnvelope<T = any> {
    id: string;
    code: number;
    data: T;
    message: string;
}

export interface RpcEventEnvelope<T = any> {
    event: string;
    data: T;
}

/**
 * 设备硬件参数与指纹伪装配置模型 (CPU/内存/GPU/屏幕)
 */
export interface HardwareConfig {
    /** CPU 核心数 (navigator.hardwareConcurrency)，如 4, 8, 12, 16 */
    cpuCores?: number;
    hardwareConcurrency?: number;
    /** 内存大小 GB (navigator.deviceMemory)，如 2, 4, 8, 12, 16, 32 */
    deviceMemory?: number;
    memory?: number;
    /** WebGL 显卡厂商 (UNMASKED_VENDOR_WEBGL)，如 'Qualcomm', 'Apple Inc.', 'ARM' */
    glVendor?: string;
    /** WebGL 渲染器 (UNMASKED_RENDERER_WEBGL)，如 'Adreno (TM) 740', 'Mali-G715-MC11', 'Apple GPU' */
    glRenderer?: string;
    /** 平台标识 (navigator.platform)，如 'Linux aarch64', 'Win32' */
    platform?: string;
    /** 触控点数 (navigator.maxTouchPoints) */
    maxTouchPoints?: number;
    /** 自定义屏幕宽度 (screen.width) */
    screenWidth?: number;
    /** 自定义屏幕高度 (screen.height) */
    screenHeight?: number;
    /** 屏幕像素比 (window.devicePixelRatio) */
    devicePixelRatio?: number;
    /** 快捷预设模版 */
    preset?: 'flagship' | 'midrange' | 'budget' | 'desktop';
}

export interface PageOptions {
    /** 自定义初始访问地址 (如 https://creator.douyin.com)，传入则在创建后立即后台加载 */
    url?: string;
    headless?: boolean;
    userAgent?: string;
    timeout?: number;
    /**
     * 独立数据目录 (Profile / Partition) 名称。
     * 若指定不同名称，则此 WebView 享有完全物理隔离的 Cookie、LocalStorage、IndexedDB 和缓存！
     * 传 'default' 或留空则使用默认共享环境。
     */
    profile?: string;
    /** 兼容 Electron partition 语法 (例如 'persist:account_1' 或 'session_alice') */
    partition?: string;
    /**
     * 注入的硬件参数与设备指纹配置 (CPU 核心数、RAM 内存大小、WebGL 显卡、屏幕参数)
     */
    hardware?: HardwareConfig;
    /** 快捷硬件预设: 'flagship' (8核/16G) | 'midrange' (8核/8G) | 'budget' (4核/4G) | 'desktop' (16核/32G) */
    hardwarePreset?: 'flagship' | 'midrange' | 'budget' | 'desktop';
}

export interface PageInfo {
    pageId: string;
    url: string;
    title: string;
    isHeadless: boolean;
    isForeground: boolean;
    profile: string;
    hardware?: HardwareConfig;
}

export interface CookieItem {
    name: string;
    value: string;
    domain?: string;
    path?: string;
    secure?: boolean;
    httpOnly?: boolean;
}

export interface NativeRequestOptions {
    url: string;
    method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH';
    headers?: Record<string, string>;
    body?: string;
    timeoutMs?: number;
}

export interface NativeResponse {
    status: number;
    headers: Record<string, string>;
    body: string;
}

export interface CapturedResponse {
    pageId: string;
    url: string;
    status: number;
    headers?: Record<string, string>;
    body: string;
}

export interface CapturedRequest {
    pageId: string;
    url: string;
    method: string;
    headers?: Record<string, string>;
    body?: string;
}

export type UnsubscribeFn = () => void;

/**
 * 单个受控页面实例（对应原生一个独立或后台无头 WebView，可绑定独立 Profile）
 */
export class Page {
    public readonly pageId: string;
    public readonly isHeadless: boolean;
    public readonly profile: string;
    public readonly hardware?: HardwareConfig;
    private readonly client: MobileElectronClient;

    constructor(pageId: string, isHeadless: boolean, profile: string = 'default', client: MobileElectronClient, hardware?: HardwareConfig) {
        this.pageId = pageId;
        this.isHeadless = isHeadless;
        this.profile = profile;
        this.client = client;
        this.hardware = hardware;
    }

    /**
     * 获取当前页面被注入的硬件参数与设备指纹 (CPU核心数/内存/GPU/屏幕)
     */
    public async getHardware(): Promise<HardwareConfig> {
        const res = await this.client.invoke<{ hardware: HardwareConfig }>('page', 'getHardware', { pageId: this.pageId });
        return res.hardware;
    }

    /**
     * 动态修改或重新注入当前页面的硬件参数
     */
    public async setHardware(hw: HardwareConfig): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean }>('page', 'setHardware', { pageId: this.pageId, hardware: hw });
        return res.success;
    }

    /**
     * 导航至指定 URL，等待加载完成
     */
    public async goto(url: string, timeoutMs: number = 15000): Promise<void> {
        return this.client.invoke<void>('page', 'goto', { pageId: this.pageId, url, timeoutMs });
    }

    /**
     * 将该页面从后台静默运行状态无缝切换至前台全屏展示
     */
    public async bringToFront(): Promise<void> {
        return this.client.invoke<void>('page', 'bringToFront', { pageId: this.pageId });
    }

    /**
     * 将当前页面退回后台隐形运行，切回主页
     */
    public async sendToBack(): Promise<void> {
        return this.client.invoke<void>('page', 'sendToBack', { pageId: this.pageId });
    }

    /**
     * 获取当前页面底层的完整 Cookie (自动精准匹配其独立 Profile 分区)
     */
    public async getCookies(): Promise<string> {
        const res = await this.client.invoke<{ cookies: string; profile: string }>('page', 'getCookies', { pageId: this.pageId });
        return res.cookies;
    }

    /**
     * 导入并设置当前页面的 Cookie (精准写入其独立 Profile 分区，不污染其他页面)
     */
    public async setCookies(cookies: string): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean; profile: string }>('page', 'setCookies', { pageId: this.pageId, cookies });
        return res.success;
    }

    /**
     * 清空当前页面对应 Profile 的所有 Cookie 和本地存储 (LocalStorage / IndexedDB)
     */
    public async clearData(): Promise<void> {
        await this.client.invoke<{ success: boolean }>('page', 'clearData', { pageId: this.pageId });
    }

    /**
     * 从当前页面渲染的 DOM / Canvas 中抓取二维码图片（返回 Base64 Data URL）
     */
    public async extractQrCode(selector?: string): Promise<string> {
        const res = await this.client.invoke<{ base64: string }>('page', 'extractQrCode', {
            pageId: this.pageId,
            selector: selector || 'img[src*="qr"], img.qrcode, canvas.qrcode, #qrcode img, #qrcode canvas'
        });
        return res.base64;
    }

    /**
     * 在目标页面上下文执行任意 JavaScript 脚本并取回返回值
     */
    public async evaluate<T = any>(script: string): Promise<T> {
        const res = await this.client.invoke<{ result: T }>('page', 'evaluate', { pageId: this.pageId, script });
        return res.result;
    }

    /**
     * 监听当前页面触发的网络响应
     */
    public onResponse(urlPattern: string, callback: (res: CapturedResponse) => void): UnsubscribeFn {
        return this.client.on('network:responseCaptured', (evt: CapturedResponse) => {
            if (evt.pageId === this.pageId && (!urlPattern || evt.url.includes(urlPattern))) {
                callback(evt);
            }
        });
    }

    /**
     * 关闭并从原生内存中销毁该 WebView 实例
     */
    public async close(): Promise<void> {
        return this.client.invoke<void>('page', 'close', { pageId: this.pageId });
    }
}

/**
 * Mobile Electron 核心客户端
 */
export class MobileElectronClient {
    private readonly platform: Platform;
    private readonly pendingRequests: Map<string, { resolve: Function; reject: Function; timer: any }> = new Map();
    private readonly eventListeners: Map<string, Set<Function>> = new Map();

    constructor() {
        this.platform = this.detectPlatform();
        this.initBridgeListener();
    }

    public get currentPlatform(): Platform {
        return this.platform;
    }

    public get isRunningInContainer(): boolean {
        return this.platform !== 'unknown';
    }

    private detectPlatform(): Platform {
        if (typeof window === 'undefined') return 'unknown';
        if ((window as any).AndroidBridge) return 'android';
        if ((window as any).webkit?.messageHandlers?.NativeBridge) return 'ios';
        return 'unknown';
    }

    private initBridgeListener(): void {
        if (typeof window === 'undefined') return;

        (window as any).__onNativeRpcResponse = (responseJson: string) => {
            try {
                const response: RpcResponseEnvelope = typeof responseJson === 'string'
                    ? JSON.parse(responseJson)
                    : responseJson;

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
                console.error('[MobileElectron] Failed to parse native response:', err);
            }
        };

        (window as any).__onNativeRpcEvent = (eventJson: string) => {
            try {
                const event: RpcEventEnvelope = typeof eventJson === 'string'
                    ? JSON.parse(eventJson)
                    : eventJson;

                const listeners = this.eventListeners.get(event.event);
                if (listeners) {
                    listeners.forEach(fn => fn(event.data));
                }
            } catch (err) {
                console.error('[MobileElectron] Failed to parse native event:', err);
            }
        };
    }

    public invoke<T = any>(module: string, action: string, params: Record<string, any> = {}, timeoutMs: number = 15000): Promise<T> {
        return new Promise<T>((resolve, reject) => {
            if (!this.isRunningInContainer) {
                return reject(new Error('Current environment is not running inside Mobile Electron container'));
            }

            const reqId = 'rpc_' + Date.now() + '_' + Math.random().toString(36).substr(2, 8);
            const request: RpcRequestEnvelope = {
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

            if (this.platform === 'android') {
                (window as any).AndroidBridge.dispatch(payloadStr);
            } else if (this.platform === 'ios') {
                (window as any).webkit.messageHandlers.NativeBridge.postMessage(payloadStr);
            }
        });
    }

    public on(eventName: string, listener: Function): UnsubscribeFn {
        if (!this.eventListeners.has(eventName)) {
            this.eventListeners.set(eventName, new Set());
        }
        this.eventListeners.get(eventName)!.add(listener);

        return () => {
            const listeners = this.eventListeners.get(eventName);
            if (listeners) {
                listeners.delete(listener);
            }
        };
    }

    // ==========================================
    // 业务模块 API 封装
    // ==========================================

    /**
     * 页面与独立 Profile 沙箱管理器模块 (Browser & Multi-Profile)
     */
    public readonly browser = {
        /**
         * 创建一个全新的页面实例
         * @param options.profile 指定独立 Profile 目录名称（例如 'account_alpha'），享有独立 Cookie 和存储
         * @param options.headless 是否后台静默运行
         */
        newPage: async (options: PageOptions = { headless: true, profile: 'default' }): Promise<Page> => {
            const res = await this.invoke<{ pageId: string; profile: string; multiProfileSupported: boolean; hardware: HardwareConfig }>('page', 'create', options);
            return new Page(res.pageId, options.headless ?? true, res.profile || 'default', this, res.hardware);
        },
        /**
         * 获取当前容器所在的主页面实例
         */
        current: (): Page => {
            return new Page('main', false, 'default', this);
        },
        /**
         * 获取容器内所有正在运行的页面及其 Profile 绑定情况
         */
        getAllPages: async (): Promise<PageInfo[]> => {
            const res = await this.invoke<{ pages: PageInfo[] }>('page', 'list');
            return res.pages;
        },
        /**
         * 获取所有现存的 Profile 数据目录列表
         */
        listProfiles: async (): Promise<string[]> => {
            const res = await this.invoke<{ profiles: string[] }>('page', 'listProfiles');
            return res.profiles;
        },
        /**
         * 删除指定的独立 Profile 分区并清空其持久化数据
         */
        deleteProfile: async (profileName: string): Promise<boolean> => {
            const res = await this.invoke<{ success: boolean }>('page', 'deleteProfile', { profile: profileName });
            return res.success;
        },
        /**
         * 将指定后台页面切换至前台全屏展示
         */
        switchToPage: async (pageId: string): Promise<void> => {
            return this.invoke<void>('page', 'bringToFront', { pageId });
        },
        /**
         * 启动抖音创作者中心 (creator.douyin.com) 隐形 WebView 扫码登录与二维码双向状态监控
         */
        startDouyinQrLogin: async (): Promise<{ success: boolean; message: string }> => {
            return this.invoke<{ success: boolean; message: string }>('page', 'startDouyinQrLogin', {});
        }
    };

    /**
     * 底层 Cookie 管理模块（支持全局或针对特定 profile / pageId 独立读写）
     */
    public readonly cookie = {
        get: async (url: string, target?: { pageId?: string; profile?: string }): Promise<string> => {
            const res = await this.invoke<{ cookies: string; profile: string }>('cookie', 'get', {
                url,
                pageId: target?.pageId,
                profile: target?.profile
            });
            return res.cookies;
        },
        set: async (url: string, rawInput: string, target?: { pageId?: string; profile?: string }): Promise<number> => {
            const res = await this.invoke<{ count: number; profile: string }>('cookie', 'import', {
                url,
                input: rawInput,
                pageId: target?.pageId,
                profile: target?.profile
            });
            return res.count;
        },
        clear: async (target?: { pageId?: string; profile?: string }): Promise<boolean> => {
            const res = await this.invoke<{ success: boolean; profile: string }>('cookie', 'clear', {
                pageId: target?.pageId,
                profile: target?.profile
            });
            return res.success;
        }
    };

    /**
     * 原生网络增强模块
     */
    public readonly network = {
        fetchNative: async (options: NativeRequestOptions): Promise<NativeResponse> => {
            return this.invoke<NativeResponse>('network', 'fetch', options);
        },
        onResponse: (urlPattern: string, callback: (res: CapturedResponse) => void): UnsubscribeFn => {
            return this.on('network:responseCaptured', (evt: CapturedResponse) => {
                if (!urlPattern || evt.url.includes(urlPattern)) {
                    callback(evt);
                }
            });
        }
    };

    /**
     * 原生系统设备与界面模块
     */
    public readonly device = {
        getClipboard: async (): Promise<string> => {
            const res = await this.invoke<{ text: string }>('device', 'getClipboard');
            return res.text;
        },
        setClipboard: async (text: string): Promise<boolean> => {
            const res = await this.invoke<{ success: boolean }>('device', 'setClipboard', { text });
            return res.success;
        },
        toast: async (message: string): Promise<void> => {
            return this.invoke<void>('device', 'toast', { message });
        }
    };
}

export const electron = new MobileElectronClient();
if (typeof window !== 'undefined') {
    (window as any).MobileElectron = electron;
}
export default electron;
