/**
 * @file app.ts
 * @description 宿主应用系统级信息、生命周期控制与动态云控配置模块
 */

import { MobileElectronClient } from '../client';
import { AppInfo, RemoteConfigStatus, FetchRemoteConfigResult } from '../types';

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
     * 获取当前配置生效来源与白名单状态
     */
    public async getConfigStatus(): Promise<RemoteConfigStatus> {
        return this.client.invoke<RemoteConfigStatus>('app', 'getConfigStatus');
    }

    /**
     * 动态从远程云端配置服务器拉取新配置并实时生效持久化
     * @param url 云控配置 JSON 下载地址
     */
    public async fetchRemoteConfig(url: string): Promise<FetchRemoteConfigResult> {
        return this.client.invoke<FetchRemoteConfigResult>('app', 'fetchRemoteConfig', { url });
    }

    /**
     * 运行时动态更新生效配置 (可选择是否持久化到应用沙箱)
     * @param config 新的配置对象
     * @param persist 是否持久化到沙箱缓存 (默认 true)
     */
    public async applyRemoteConfig(config: Record<string, any>, persist: boolean = true): Promise<{ success: boolean; applied: boolean }> {
        return this.client.invoke<{ success: boolean; applied: boolean }>('app', 'applyRemoteConfig', { config, persist });
    }

    /**
     * 重置清除远程沙箱配置，恢复出厂内置 Assets / Bundle 默认配置
     */
    public async resetRemoteConfig(): Promise<{ success: boolean; source: string }> {
        return this.client.invoke<{ success: boolean; source: string }>('app', 'resetRemoteConfig');
    }

    /**
     * 退出当前应用程序
     */
    public async exit(): Promise<void> {
        await this.client.invoke<void>('app', 'exit');
    }
}
