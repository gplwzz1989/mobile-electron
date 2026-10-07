/**
 * @file app.ts
 * @description 宿主应用系统级信息与生命周期控制模块
 */
import { MobileElectronClient } from '../client';
import { AppInfo } from '../types';
export declare class AppModule {
    private readonly client;
    constructor(client: MobileElectronClient);
    /**
     * 获取当前 App 容器运行时详细信息
     */
    getInfo(): Promise<AppInfo>;
    /**
     * 获取框架当前加载的应用配置 (app-config.json)
     */
    getConfig(): Promise<Record<string, any>>;
    /**
     * 退出当前应用程序
     */
    exit(): Promise<void>;
}
