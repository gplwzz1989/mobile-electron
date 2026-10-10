/**
 * @file hotUpdate.ts
 * @description 离线资源包引擎与增量热更新控制模块
 */
import { MobileElectronClient } from '../client';
import { HotUpdateCheckResult, HotUpdateProgressEvent, HotUpdateVersionInfo } from '../types';
export declare class HotUpdateModule {
    private readonly client;
    constructor(client: MobileElectronClient);
    /**
     * 获取当前生效的离线包版本与来源信息
     */
    getCurrentVersion(): Promise<HotUpdateVersionInfo>;
    /**
     * 检查远端是否有新版本离线包
     * @param url 自定义更新清单地址 (不传则使用默认配置或开发服务器)
     */
    checkForUpdate(url?: string): Promise<HotUpdateCheckResult>;
    /**
     * 下载离线包并解压安装到本地沙箱
     * @param options packageUrl 与 expectedHash
     * @param onProgress 进度回调函数
     */
    downloadAndInstall(options: {
        packageUrl: string;
        packageHash?: string;
    }, onProgress?: (progress: HotUpdateProgressEvent) => void): Promise<HotUpdateVersionInfo>;
    /**
     * 应用新版本并立即重新加载页面
     */
    applyAndReload(): Promise<{
        applied: boolean;
    }>;
    /**
     * 安全回滚至原生底座内置静态离线包并重载
     */
    rollback(): Promise<{
        rolledBack: boolean;
    }>;
}
