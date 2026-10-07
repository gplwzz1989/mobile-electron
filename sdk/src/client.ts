/**
 * @file client.ts
 * @description Mobile Electron 核心通信客户端与 Page 抽象
 */

import {
    Platform,
    RpcRequestEnvelope,
    RpcResponseEnvelope,
    RpcEventEnvelope,
    HardwareConfig,
    CapturedResponse,
    UnsubscribeFn,
    CookieDetail,
    StorageDump
} from './types';
import { MockEngine } from './mock';

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
     * 获取当前页面底层的完整 Cookie 字符串 (a=b; c=d，包含所有 HttpOnly)
     */
    public async getCookies(url?: string): Promise<string> {
        const res = await this.client.invoke<{ cookies: string; profile: string }>('page', 'getCookies', { pageId: this.pageId, url });
        return res.cookies;
    }

    /**
     * 全量提取当前页面底层的全部 Cookie 结构体对象 (包含所有 HttpOnly、Secure、Domain、Path、Expires)
     */
    public async getAllCookies(url?: string): Promise<CookieDetail[]> {
        const res = await this.client.invoke<{ details: CookieDetail[]; count: number }>('page', 'getAllCookies', { pageId: this.pageId, url });
        return res.details || [];
    }

    /**
     * 导入并设置当前页面的 Cookie (精准写入其独立 Profile 分区，支持字符串或 CookieDetail 数组)
     */
    public async setCookies(cookies: string | CookieDetail[]): Promise<boolean> {
        const payload: Record<string, any> = { pageId: this.pageId };
        if (Array.isArray(cookies)) {
            payload.cookies = cookies;
        } else {
            payload.cookies = cookies;
            payload.input = cookies;
        }
        const res = await this.client.invoke<{ success: boolean; profile: string }>('page', 'setCookies', payload);
        return res.success;
    }

    /**
     * 读取当前页面加载域名下的完整 LocalStorage 键值字典
     */
    public async getLocalStorage(): Promise<Record<string, string>> {
        const res = await this.client.invoke<{ data: Record<string, string> }>('page', 'getLocalStorage', { pageId: this.pageId });
        return res.data || {};
    }

    /**
     * 向当前页面的 LocalStorage 批量写入键值对
     */
    public async setLocalStorage(data: Record<string, string>): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean }>('page', 'setLocalStorage', { pageId: this.pageId, data });
        return res.success;
    }

    /**
     * 清空当前页面的 LocalStorage
     */
    public async clearLocalStorage(): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean }>('page', 'clearLocalStorage', { pageId: this.pageId });
        return res.success;
    }

    /**
     * 一键导出当前页面的全量存储快照 (全部含 HttpOnly 的 Cookie + LocalStorage + SessionStorage)
     */
    public async dumpStorage(): Promise<StorageDump> {
        return this.client.invoke<StorageDump>('page', 'dumpStorage', { pageId: this.pageId });
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
        return this.platform === 'android' || this.platform === 'ios';
    }

    private detectPlatform(): Platform {
        if (typeof window === 'undefined') return 'unknown';
        if ((window as any).AndroidBridge) return 'android';
        if ((window as any).webkit?.messageHandlers?.NativeBridge) return 'ios';
        return 'web-mock';
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
        if (!this.isRunningInContainer) {
            return MockEngine.mockInvoke<T>(module, action, params);
        }

        return new Promise<T>((resolve, reject) => {
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
}
