/**
 * @file types.ts
 * @description Mobile Electron 框架核心 TypeScript 类型契约
 */
export type Platform = 'android' | 'ios' | 'unknown' | 'web-mock';
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
 * 硬件参数与设备指纹配置模型 (CPU/内存/GPU/屏幕)
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
    /** WebGL 渲染器 (UNMASKED_RENDERER_WEBGL)，如 'Adreno (TM) 740', 'Apple GPU' */
    glRenderer?: string;
    /** 平台标识 (navigator.platform)，如 'Linux aarch64', 'Win32' */
    platform?: string;
    /** 触控点数 (navigator.maxTouchPoints) */
    maxTouchPoints?: number;
    /** 屏幕宽度 (screen.width) */
    screenWidth?: number;
    /** 屏幕高度 (screen.height) */
    screenHeight?: number;
    /** 屏幕像素比 (window.devicePixelRatio) */
    devicePixelRatio?: number;
    /** 预设模版 */
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
     */
    profile?: string;
    partition?: string;
    /** 注入的硬件参数与设备指纹配置 */
    hardware?: HardwareConfig;
    /** 快捷硬件预设: 'flagship' | 'midrange' | 'budget' | 'desktop' */
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
export interface StatusBarOptions {
    /** 状态栏背景色 (十六进制，例如 '#FFFFFF' 或 '#000000') */
    color?: string;
    /** 状态栏文字/图标是否为深色 (浅色背景时设为 true) */
    darkIcons?: boolean;
    /** 是否沉浸式全屏渗透 (网页内容延伸至状态栏下方) */
    immersive?: boolean;
}
export interface AppInfo {
    appName: string;
    appId: string;
    version: string;
    framework: string;
    frameworkVersion: string;
    multiProfileSupported: boolean;
    defaultUrl: string;
    config: Record<string, any>;
}
export type UnsubscribeFn = () => void;
