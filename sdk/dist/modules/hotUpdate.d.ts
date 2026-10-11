/**
 * @file hotUpdate.ts
 * @description 离线资源包引擎与增量热更新控制模块
 */
import { MobileElectronClient } from '../client';
import { HotUpdateCheckResult, HotUpdateProgressEvent, HotUpdateVersionInfo, HotUpdateReadyEvent, UnsubscribeFn } from '../types';
export declare class HotUpdateModule {
    private readonly client;
    constructor(client: MobileElectronClient);
    /**
     * 监听后台静默完成部署的新版本就绪事件 (前台静默差分就绪时触发)
     */
    onUpdateReady(listener: (event: HotUpdateReadyEvent) => void): UnsubscribeFn;
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
     * 下载离线包并解压安装到本地沙箱 (支持增量差分下载与整包全量下载)
     * @param options packageUrl, packageHash 及可选的增量差分信息 diff
     * @param onProgress 进度回调函数
     */
    downloadAndInstall(options: {
        packageUrl: string;
        packageHash?: string;
        diff?: any;
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
