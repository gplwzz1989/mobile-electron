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
export * from './types';
export { MobileElectronClient, Page } from './client';
export { BrowserModule } from './modules/browser';
export { CookieModule } from './modules/cookie';
export { NetworkModule } from './modules/network';
export { DeviceModule } from './modules/device';
export { WindowModule } from './modules/window';
export { AppModule } from './modules/app';
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
