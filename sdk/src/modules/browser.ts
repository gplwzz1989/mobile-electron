/**
 * @file browser.ts
 * @description 页面与独立 Profile 沙箱管理器模块
 */

import { MobileElectronClient, Page } from '../client';
import { PageOptions, PageInfo, HardwareConfig } from '../types';

export class BrowserModule {
    constructor(private readonly client: MobileElectronClient) {}

    /**
     * 创建一个全新的页面实例
     * @param options.profile 指定独立 Profile 目录名称（例如 'account_alpha'），享有独立 Cookie 和存储
     * @param options.headless 是否后台静默运行
     */
    public async newPage(options: PageOptions = { headless: true, profile: 'default' }): Promise<Page> {
        const res = await this.client.invoke<{ pageId: string; profile: string; multiProfileSupported: boolean; hardware: HardwareConfig }>('page', 'create', options);
        return new Page(res.pageId, options.headless ?? true, res.profile || 'default', this.client, res.hardware);
    }

    /**
     * 获取当前容器所在的主页面实例
     */
    public current(): Page {
        return new Page('main', false, 'default', this.client);
    }

    /**
     * 获取容器内所有正在运行的页面及其 Profile 绑定情况
     */
    public async getAllPages(): Promise<PageInfo[]> {
        const res = await this.client.invoke<{ pages: PageInfo[] }>('page', 'list');
        return res.pages;
    }

    /**
     * 获取所有现存的 Profile 数据目录列表
     */
    public async listProfiles(): Promise<string[]> {
        const res = await this.client.invoke<{ profiles: string[] }>('page', 'listProfiles');
        return res.profiles;
    }

    /**
     * 删除指定的独立 Profile 分区并清空其持久化数据
     */
    public async deleteProfile(profileName: string): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean }>('page', 'deleteProfile', { profile: profileName });
        return res.success;
    }

    /**
     * 将指定后台页面切换至前台全屏展示
     */
    public async switchToPage(pageId: string): Promise<void> {
        return this.client.invoke<void>('page', 'bringToFront', { pageId });
    }

    /**
     * 启动抖音创作者中心 (creator.douyin.com) 隐形 WebView 扫码登录与二维码双向状态监控
     */
    public async startDouyinQrLogin(): Promise<{ success: boolean; message: string }> {
        return this.client.invoke<{ success: boolean; message: string }>('page', 'startDouyinQrLogin', {});
    }
}
