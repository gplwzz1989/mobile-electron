/**
 * @file app.ts
 * @description 宿主应用系统级信息、生命周期控制与动态云控配置模块
 */
import { MobileElectronClient } from '../client';
import { AppInfo, RemoteConfigStatus, FetchRemoteConfigResult, AppStateChangeEvent, UnsubscribeFn } from '../types';
export declare class AppModule {
    private readonly client;
    constructor(client: MobileElectronClient);
    /**
     * 注册应用前后台生命周期切换事件监听 (active / background)
     */
    onStateChange(listener: (event: AppStateChangeEvent) => void): UnsubscribeFn;
    /**
     * 获取当前 App 容器运行时详细信息
     */
    getInfo(): Promise<AppInfo>;
    /**
     * 获取框架当前加载的应用配置 (app-config.json)
     */
    getConfig(): Promise<Record<string, any>>;
    /**
     * 获取当前配置生效来源与白名单状态
     */
    getConfigStatus(): Promise<RemoteConfigStatus>;
    /**
     * 动态从远程云端配置服务器拉取新配置并实时生效持久化
     * @param url 云控配置 JSON 下载地址
     */
    fetchRemoteConfig(url: string): Promise<FetchRemoteConfigResult>;
    /**
     * 运行时动态更新生效配置 (可选择是否持久化到应用沙箱)
     * @param config 新的配置对象
     * @param persist 是否持久化到沙箱缓存 (默认 true)
     */
    applyRemoteConfig(config: Record<string, any>, persist?: boolean): Promise<{
        success: boolean;
        applied: boolean;
    }>;
    /**
     * 重置清除远程沙箱配置，恢复出厂内置 Assets / Bundle 默认配置
     */
    resetRemoteConfig(): Promise<{
        success: boolean;
        source: string;
    }>;
    /**
     * 退出当前应用程序
     */
    exit(): Promise<void>;
}
