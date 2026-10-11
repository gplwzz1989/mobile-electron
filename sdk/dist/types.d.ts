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
    /** WebGL 渲染器 (UNMASKED_RENDERER_WEBGL)，如 'Adreno (TM) 750', 'Apple GPU' */
    glRenderer?: string;
    /** 平台标识 (navigator.platform)，如 'Linux aarch64', 'Win32' */
    platform?: string;
    /** 设备具体机型型号 (navigator.userAgentData.model)，如 'SM-S9280' */
    model?: string;
    /** CPU 架构 (如 'arm64', 'x86_64') */
    architecture?: string;
    /** 系统位数 (32 或 64) */
    bitness?: number;
    /** 触控点数 (navigator.maxTouchPoints) */
    maxTouchPoints?: number;
    /** 屏幕宽度 (screen.width) */
    screenWidth?: number;
    /** 屏幕高度 (screen.height) */
    screenHeight?: number;
    /** 屏幕像素比 (window.devicePixelRatio) */
    devicePixelRatio?: number;
    /** 屏幕颜色深度 (screen.colorDepth, 通常 24) */
    colorDepth?: number;
    /** 网络连接类型 (navigator.connection.effectiveType, 如 '5g', '4g', 'wifi') */
    networkType?: string;
    /** 电池电量 (navigator.getBattery level, 0.0 ~ 1.0) */
    batteryLevel?: number;
    /** 电池是否处于充电状态 */
    batteryCharging?: boolean;
    /** 是否开启 Web Audio API 声卡高阶浮点微扰防检测 */
    audioNoiseEnabled?: boolean;
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
export interface CookieDetail {
    name: string;
    value: string;
    domain: string;
    path: string;
    secure: boolean;
    httpOnly: boolean;
    expires?: number;
    sameSite?: string;
}
export type CookieItem = CookieDetail;
export interface StorageDump {
    cookies: CookieDetail[];
    cookieString: string;
    localStorage: Record<string, string>;
    sessionStorage: Record<string, string>;
    url: string;
    profile: string;
    pageId?: string;
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
export type FileEncoding = 'utf8' | 'base64';
export type DirectoryType = 'documents' | 'cache' | 'temp';
export interface FileInfo {
    name: string;
    isDirectory: boolean;
    size: number;
    lastModified: number;
}
export interface FileWriteOptions {
    path: string;
    content: string;
    encoding?: FileEncoding;
    append?: boolean;
    directory?: DirectoryType;
}
export interface FileReadOptions {
    path: string;
    encoding?: FileEncoding;
    directory?: DirectoryType;
}
export interface FileDownloadOptions {
    url: string;
    destinationPath: string;
    directory?: DirectoryType;
}
export interface AlertOptions {
    title?: string;
    message: string;
    buttonText?: string;
}
export interface ConfirmOptions {
    title?: string;
    message: string;
    confirmText?: string;
    cancelText?: string;
}
export interface PromptOptions {
    title?: string;
    message: string;
    defaultValue?: string;
    placeholder?: string;
    confirmText?: string;
    cancelText?: string;
}
export interface TabBarItem {
    id: string;
    title: string;
    icon?: string;
    selectedIcon?: string;
    badge?: string;
}
export interface TabBarOptions {
    selectedId?: string;
    selectedIndex?: number;
    backgroundColor?: string;
    color?: string;
    selectedColor?: string;
    borderTopColor?: string;
    visible?: boolean;
}
export interface TabBarClickEvent {
    id: string;
    index: number;
    title: string;
}
export interface TabBarState {
    visible: boolean;
    selectedId: string;
    selectedIndex: number;
    items: TabBarItem[];
}
export interface FrameworkDebugInfo {
    platform: string;
    sdkVersion?: number;
    pid?: number;
    activeUrl?: string;
    headlessCount?: number;
    tabBarVisible?: boolean;
    devtoolsPort?: number;
}
export interface DevToolsResult {
    success: boolean;
    devtoolsSupported?: boolean;
    opened?: boolean;
    activeUrl?: string;
    remoteDebugging?: boolean;
    remotePort?: number;
    message?: string;
}
export interface HotUpdateVersionInfo {
    version: string;
    buildNumber?: number;
    isHotUpdated: boolean;
    source: 'builtin' | 'sandbox';
    hash?: string;
    appliedAt?: number;
    description?: string;
    fileCount?: number;
    updateType?: 'incremental_diff' | 'full_package';
    diffFilesCount?: number;
    installMode?: 'incremental_diff' | 'full_package';
    downloadedFiles?: number;
    reusedFiles?: number;
}
export interface HotUpdateDiffItem {
    path: string;
    hash: string;
    size: number;
}
export interface HotUpdateDiffInfo {
    isDiffSupported: boolean;
    canUseDiff: boolean;
    reason?: string;
    changedCount?: number;
    reusableCount?: number;
    diffSize?: number;
    fullPackageSize?: number;
    savedPercent?: number;
    filesBaseUrl?: string;
    changedFiles?: HotUpdateDiffItem[];
}
export interface HotUpdateCheckResult {
    hasUpdate: boolean;
    currentVersion: string;
    currentBuild?: number;
    latestVersion: string;
    latestBuild?: number;
    packageUrl: string;
    packageHash: string;
    packageSize: number;
    description?: string;
    mandatory?: boolean;
    diff?: HotUpdateDiffInfo;
}
export interface HotUpdateProgressEvent {
    percent: number;
    downloadedBytes: number;
    totalBytes: number;
}
export type HapticImpactStyle = 'light' | 'medium' | 'heavy';
export type HapticNotificationType = 'success' | 'warning' | 'error';
export interface BiometricsAvailability {
    available: boolean;
    supported: boolean;
    deviceSecure: boolean;
    hasFingerprint: boolean;
    hasFace: boolean;
    biometricType: 'fingerprint' | 'face' | 'none';
}
export interface BiometricsAuthOptions {
    title?: string;
    subtitle?: string;
    description?: string;
    cancelText?: string;
}
export interface BiometricsAuthResult {
    success: boolean;
    authenticated: boolean;
}
export interface SecureStorageResult {
    success: boolean;
}
export interface SecureStorageGetResult {
    value: string | null;
    exists: boolean;
}
export interface SecureStorageKeysResult {
    keys: string[];
}
export interface CameraPhotoOptions {
    quality?: number;
    targetWidth?: number;
}
export interface CameraPhotoResult {
    base64?: string;
    uri?: string;
    width?: number;
    height?: number;
}
export interface BarcodeScanResult {
    text: string;
    format?: string;
}
export interface RemoteConfigStatus {
    source: 'builtin_asset' | 'remote_cached';
    defaultUrl: string;
    whitelistCount: number;
    version: string;
}
export interface FetchRemoteConfigResult {
    success: boolean;
    source: string;
    config: Record<string, any>;
}
export type AppState = 'active' | 'background' | 'inactive';
export interface AppStateChangeEvent {
    state: AppState;
    timestamp: number;
}
export type NetworkType = 'wifi' | 'cellular' | 'ethernet' | 'none' | 'other';
export interface NetworkStatus {
    isConnected: boolean;
    type: NetworkType;
    isMetered: boolean;
}
export interface HotUpdateReadyEvent {
    version: string;
    installMode: 'incremental_diff' | 'full_package';
    appliedAt: number;
}
export interface FloatingWindowOptions {
    id?: string;
    url: string;
    title?: string;
    width?: number;
    height?: number;
    x?: number;
    y?: number;
    draggable?: boolean;
    showControls?: boolean;
}
export interface FloatingWindowResult {
    success: boolean;
    id: string;
    width: number;
    height: number;
    x: number;
    y: number;
}
export interface FloatingWindowState {
    id: string;
    url: string;
    title: string;
    width: number;
    height: number;
    x: number;
    y: number;
    isMinimized: boolean;
    isVisible: boolean;
}
export interface FloatingWindowUpdateOptions {
    width?: number;
    height?: number;
    x?: number;
    y?: number;
}
export interface IpcMessageEvent<T = any> {
    channel: string;
    senderId: string;
    targetId?: string;
    payload: T;
    timestamp: number;
}
export interface IpcSendResult {
    success: boolean;
    recipientCount?: number;
    targetId?: string;
}
export interface NotificationOptions {
    /** 通知标题 (默认 App 应用名称) */
    title?: string;
    /** 通知正文内容 */
    body?: string;
    /** 通知正文内容 (别名兼容) */
    message?: string;
    /** 唯一通知 ID (可选，未指定则自增生成) */
    id?: number | string;
    /** 应用桌面角标数字 (可选) */
    badge?: number;
    /** 点击通知随意携带的自定义业务数据 (JSON 字符串或任意对象) */
    data?: any;
    /** 是否播放默认提示音 (默认 true) */
    sound?: boolean;
}
export interface NotificationResult {
    success: boolean;
    id: number | string;
}
export interface OpenUrlOptions {
    /** 未安装目标应用时的备用网页降级 URL */
    fallbackUrl?: string;
}
export interface OpenUrlResult {
    /** 是否成功调起目标应用或备用地址 */
    success: boolean;
    /** 是否降级打开了 fallback 地址 */
    fallback?: boolean;
    /** 失败原因 (如 'app_not_installed') */
    reason?: string;
    /** 详细错误提示信息 */
    message?: string;
}
export type StatusBarStyle = 'light' | 'dark';
export interface StatusBarInfo {
    height: number;
}
export type ScreenOrientation = 'portrait' | 'landscape' | 'sensor' | 'auto';
export interface ScreenOrientationResult {
    orientation: 'portrait' | 'landscape';
}
export interface KeyboardVisibilityEvent {
    visible: boolean;
    height: number;
}
export interface BatteryStatus {
    level: number;
    isCharging: boolean;
    plugged: 'usb' | 'ac' | 'wireless' | 'unplugged' | string;
}
export interface DeviceInfo {
    brand: string;
    model: string;
    manufacturer: string;
    device: string;
    systemVersion: string;
    apiLevel: number;
    platform: string;
    screenWidth: number;
    screenHeight: number;
    density: number;
    densityDpi: number;
    deviceId: string;
    language: string;
    country: string;
}
export interface ActionSheetOptions {
    title?: string;
    options: string[];
    cancelButtonText?: string;
}
export interface ActionSheetResult {
    index: number;
    value?: string;
    cancelled: boolean;
}
export interface ShareOptions {
    title?: string;
    text?: string;
    url?: string;
}
export interface DownloadFileOptions {
    url: string;
    fileName?: string;
    title?: string;
    description?: string;
    mimeType?: string;
}
export interface DownloadFileResult {
    success: boolean;
    downloadId: number;
    fileName: string;
}
export interface OpenFileOptions {
    path: string;
    directory?: 'documents' | 'cache' | 'temp' | string;
    mimeType?: string;
}
