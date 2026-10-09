/**
 * @file index.ts
 * @description Mobile Electron 跨端开发框架前端 SDK 统一导出入口
 */

import { MobileElectronClient, Page } from './client';
import { BrowserModule } from './modules/browser';
import { CookieModule } from './modules/cookie';
import { NetworkModule } from './modules/network';
import { DeviceModule } from './modules/device';
import { WindowModule } from './modules/window';
import { AppModule } from './modules/app';
import { FileModule } from './modules/file';
import { StorageModule } from './modules/storage';
import { DialogModule } from './modules/dialog';
import { TabBarModule } from './modules/tabBar';
import { DebugModule } from './modules/debug';

export * from './types';
export { MobileElectronClient, Page } from './client';
export { BrowserModule } from './modules/browser';
export { CookieModule } from './modules/cookie';
export { NetworkModule } from './modules/network';
export { DeviceModule } from './modules/device';
export { WindowModule } from './modules/window';
export { AppModule } from './modules/app';
export { FileModule } from './modules/file';
export { StorageModule } from './modules/storage';
export { DialogModule } from './modules/dialog';
export { TabBarModule } from './modules/tabBar';
export { DebugModule } from './modules/debug';
export { MockEngine } from './mock';

/**
 * Mobile Electron 综合门面 SDK 对象
 */
export class MobileElectron {
    public readonly client: MobileElectronClient;
    public readonly browser: BrowserModule;
    public readonly cookie: CookieModule;
    public readonly network: NetworkModule;
    public readonly device: DeviceModule;
    public readonly window: WindowModule;
    public readonly app: AppModule;
    public readonly file: FileModule;
    public readonly fs: FileModule; // 别名 fs 贴合 Electron / Node.js 习惯
    public readonly storage: StorageModule;
    public readonly dialog: DialogModule;
    public readonly tabBar: TabBarModule;
    public readonly debug: DebugModule;

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
        this.tabBar = new TabBarModule(this.client);
        this.debug = new DebugModule(this.client);
    }

    /** 当前运行平台: 'android' | 'ios' | 'web-mock' */
    public get platform() {
        return this.client.currentPlatform;
    }

    /** 是否真正运行在移动端 Native 容器内部 */
    public get isNativeContainer() {
        return this.client.isRunningInContainer;
    }

    /** 统一监听原生推送事件 */
    public on(eventName: string, listener: Function) {
        return this.client.on(eventName, listener);
    }
}

// 导出全局单例
export const electron = new MobileElectron();
export const mobileElectron = electron;

// 浏览器端 UMD / Script 引入自动全局挂载
if (typeof window !== 'undefined') {
    (window as any).MobileElectron = electron;
    (window as any).mobileElectron = electron;
}

export default electron;
