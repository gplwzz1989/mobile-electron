/**
 * @file app.ts
 * @description 宿主应用系统级信息与生命周期控制模块
 */

import { MobileElectronClient } from '../client';
import { AppInfo } from '../types';

export class AppModule {
    constructor(private readonly client: MobileElectronClient) {}

    /**
     * 获取当前 App 容器运行时详细信息
     */
    public async getInfo(): Promise<AppInfo> {
        return this.client.invoke<AppInfo>('app', 'getInfo');
    }

    /**
     * 获取框架当前加载的应用配置 (app-config.json)
     */
    public async getConfig(): Promise<Record<string, any>> {
        return this.client.invoke<Record<string, any>>('app', 'getConfig');
    }

    /**
     * 退出当前应用程序
     */
    public async exit(): Promise<void> {
        await this.client.invoke<void>('app', 'exit');
    }
}
