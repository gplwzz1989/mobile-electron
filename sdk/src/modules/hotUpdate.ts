/**
 * @file hotUpdate.ts
 * @description 离线资源包引擎与增量热更新控制模块
 */

import { MobileElectronClient } from '../client';
import { HotUpdateCheckResult, HotUpdateProgressEvent, HotUpdateVersionInfo } from '../types';

export class HotUpdateModule {
    constructor(private readonly client: MobileElectronClient) {}

    /**
     * 获取当前生效的离线包版本与来源信息
     */
    public async getCurrentVersion(): Promise<HotUpdateVersionInfo> {
        return this.client.invoke<HotUpdateVersionInfo>('hotUpdate', 'getCurrentVersion');
    }

    /**
     * 检查远端是否有新版本离线包
     * @param url 自定义更新清单地址 (不传则使用默认配置或开发服务器)
     */
    public async checkForUpdate(url?: string): Promise<HotUpdateCheckResult> {
        return this.client.invoke<HotUpdateCheckResult>('hotUpdate', 'checkForUpdate', { url });
    }

    /**
     * 下载离线包并解压安装到本地沙箱 (支持增量差分下载与整包全量下载)
     * @param options packageUrl, packageHash 及可选的增量差分信息 diff
     * @param onProgress 进度回调函数
     */
    public async downloadAndInstall(
        options: { packageUrl: string; packageHash?: string; diff?: any },
        onProgress?: (progress: HotUpdateProgressEvent) => void
    ): Promise<HotUpdateVersionInfo> {
        let cleanup: (() => void) | null = null;
        if (onProgress) {
            cleanup = this.client.on<HotUpdateProgressEvent>('hotUpdate:progress', (evt) => {
                onProgress(evt);
            });
        }

        try {
            return await this.client.invoke<HotUpdateVersionInfo>('hotUpdate', 'downloadAndInstall', options);
        } finally {
            if (cleanup) {
                cleanup();
            }
        }
    }

    /**
     * 应用新版本并立即重新加载页面
     */
    public async applyAndReload(): Promise<{ applied: boolean }> {
        return this.client.invoke<{ applied: boolean }>('hotUpdate', 'applyAndReload');
    }

    /**
     * 安全回滚至原生底座内置静态离线包并重载
     */
    public async rollback(): Promise<{ rolledBack: boolean }> {
        return this.client.invoke<{ rolledBack: boolean }>('hotUpdate', 'rollback');
    }
}
