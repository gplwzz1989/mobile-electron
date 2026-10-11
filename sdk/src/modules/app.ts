import { MobileElectronClient } from '../client';
import { AppInfo, RemoteConfigStatus, FetchRemoteConfigResult, AppStateChangeEvent, UnsubscribeFn, OpenUrlOptions, OpenUrlResult } from '../types';

export class AppModule {
    constructor(private readonly client: MobileElectronClient) {}

    /**
     * 注册应用前后台生命周期切换事件监听 (active / background)
     */
    public onStateChange(listener: (event: AppStateChangeEvent) => void): UnsubscribeFn {
        return this.client.on<AppStateChangeEvent>('app.onStateChange', listener);
    }

    /**
     * 获取当前 App 容器运行时详细信息
     */
    public async getInfo(): Promise<AppInfo> {
        return this.client.invoke<AppInfo>('app', 'getInfo');
    }

    /**
     * 检查设备是否能够处理指定的自定义协议 Scheme 或 URL (例如 'snssdk1128://', 'weixin://', 'tiktok://')
     * @param url 完整的协议或链接字符串
     */
    public async canOpenUrl(url: string): Promise<boolean> {
        const res = await this.client.invoke<{ canOpen: boolean }>('app', 'canOpenUrl', { url });
        return res?.canOpen ?? false;
    }

    /**
     * 调起系统或第三方外部应用协议 (支持完整的通用协议与参数，如 'snssdk1128://webview?url=https://iesdouyin.com/user/123')
     * @param url 完整协议字符串
     * @param options 可选配置项 (如未安装应用时的 fallbackUrl 兜底地址)
     */
    public async openUrl(url: string, options?: OpenUrlOptions): Promise<OpenUrlResult> {
        return this.client.invoke<OpenUrlResult>('app', 'openUrl', {
            url,
            fallbackUrl: options?.fallbackUrl
        });
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

    /**
     * 将应用程序压入后台并返回手机系统桌面 (等同于用户按下手机 Home 键)
     */
    public async minimize(): Promise<void> {
        await this.client.invoke<void>('app', 'minimize');
    }

    /**
     * 重启当前应用程序 (常用于热更新应用生效后重启容器)
     */
    public async relaunch(): Promise<void> {
        await this.client.invoke<void>('app', 'relaunch');
    }
}
