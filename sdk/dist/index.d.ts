/**
 * @file index.ts
 * @description Mobile Electron 跨端开发框架前端 SDK 统一导出入口
 */
import { MobileElectronClient } from './client';
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
export declare class MobileElectron {
    readonly client: MobileElectronClient;
    readonly browser: BrowserModule;
    readonly cookie: CookieModule;
    readonly network: NetworkModule;
    readonly device: DeviceModule;
    readonly window: WindowModule;
    readonly app: AppModule;
    readonly file: FileModule;
    readonly fs: FileModule;
    readonly storage: StorageModule;
    readonly dialog: DialogModule;
    readonly tabBar: TabBarModule;
    readonly debug: DebugModule;
    constructor();
    /** 当前运行平台: 'android' | 'ios' | 'web-mock' */
    get platform(): import("./types").Platform;
    /** 是否真正运行在移动端 Native 容器内部 */
    get isNativeContainer(): boolean;
    /** 统一监听原生推送事件 */
    on(eventName: string, listener: Function): import("./types").UnsubscribeFn;
}
export declare const electron: MobileElectron;
export declare const mobileElectron: MobileElectron;
export default electron;
