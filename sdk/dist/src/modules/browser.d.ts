/**
 * @file browser.ts
 * @description 页面与独立 Profile 沙箱管理器模块
 */
import { MobileElectronClient, Page } from '../client';
import { PageOptions, PageInfo } from '../types';
export declare class BrowserModule {
    private readonly client;
    constructor(client: MobileElectronClient);
    /**
     * 创建一个全新的页面实例
     * @param options.profile 指定独立 Profile 目录名称（例如 'account_alpha'），享有独立 Cookie 和存储
     * @param options.headless 是否后台静默运行
     */
    newPage(options?: PageOptions): Promise<Page>;
    /**
     * 获取当前容器所在的主页面实例
     */
    current(): Page;
    /**
     * 获取容器内所有正在运行的页面及其 Profile 绑定情况
     */
    getAllPages(): Promise<PageInfo[]>;
    /**
     * 获取所有现存的 Profile 数据目录列表
     */
    listProfiles(): Promise<string[]>;
    /**
     * 删除指定的独立 Profile 分区并清空其持久化数据
     */
    deleteProfile(profileName: string): Promise<boolean>;
    /**
     * 将指定后台页面切换至前台全屏展示
     */
    switchToPage(pageId: string): Promise<void>;
    /**
     * 启动抖音创作者中心 (creator.douyin.com) 隐形 WebView 扫码登录与二维码双向状态监控
     */
    startDouyinQrLogin(): Promise<{
        success: boolean;
        message: string;
    }>;
}
