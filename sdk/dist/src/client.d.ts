/**
 * @file client.ts
 * @description Mobile Electron 核心通信客户端与 Page 抽象
 */
import { Platform, HardwareConfig, CapturedResponse, UnsubscribeFn } from './types';
export declare class Page {
    readonly pageId: string;
    readonly isHeadless: boolean;
    readonly profile: string;
    readonly hardware?: HardwareConfig;
    private readonly client;
    constructor(pageId: string, isHeadless: boolean, profile: string, client: MobileElectronClient, hardware?: HardwareConfig);
    /**
     * 获取当前页面被注入的硬件参数与设备指纹 (CPU核心数/内存/GPU/屏幕)
     */
    getHardware(): Promise<HardwareConfig>;
    /**
     * 动态修改或重新注入当前页面的硬件参数
     */
    setHardware(hw: HardwareConfig): Promise<boolean>;
    /**
     * 导航至指定 URL，等待加载完成
     */
    goto(url: string, timeoutMs?: number): Promise<void>;
    /**
     * 将该页面从后台静默运行状态无缝切换至前台全屏展示
     */
    bringToFront(): Promise<void>;
    /**
     * 将当前页面退回后台隐形运行，切回主页
     */
    sendToBack(): Promise<void>;
    /**
     * 获取当前页面底层的完整 Cookie (自动精准匹配其独立 Profile 分区)
     */
    getCookies(): Promise<string>;
    /**
     * 导入并设置当前页面的 Cookie (精准写入其独立 Profile 分区，不污染其他页面)
     */
    setCookies(cookies: string): Promise<boolean>;
    /**
     * 清空当前页面对应 Profile 的所有 Cookie 和本地存储 (LocalStorage / IndexedDB)
     */
    clearData(): Promise<void>;
    /**
     * 从当前页面渲染的 DOM / Canvas 中抓取二维码图片（返回 Base64 Data URL）
     */
    extractQrCode(selector?: string): Promise<string>;
    /**
     * 在目标页面上下文执行任意 JavaScript 脚本并取回返回值
     */
    evaluate<T = any>(script: string): Promise<T>;
    /**
     * 监听当前页面触发的网络响应
     */
    onResponse(urlPattern: string, callback: (res: CapturedResponse) => void): UnsubscribeFn;
    /**
     * 关闭并从原生内存中销毁该 WebView 实例
     */
    close(): Promise<void>;
}
export declare class MobileElectronClient {
    private readonly platform;
    private readonly pendingRequests;
    private readonly eventListeners;
    constructor();
    get currentPlatform(): Platform;
    get isRunningInContainer(): boolean;
    private detectPlatform;
    private initBridgeListener;
    invoke<T = any>(module: string, action: string, params?: Record<string, any>, timeoutMs?: number): Promise<T>;
    on(eventName: string, listener: Function): UnsubscribeFn;
}
