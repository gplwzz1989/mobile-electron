/**
 * @file window.ts
 * @description 宿主窗口、状态栏沉浸、全屏与导航控制模块
 */

import { MobileElectronClient } from '../client';
import { StatusBarOptions } from '../types';

export class WindowModule {
    constructor(private readonly client: MobileElectronClient) {}

    /**
     * 设置原生状态栏样式
     * @param options.color 状态栏颜色十六进制，例如 '#FFFFFF'
     * @param options.darkIcons 图标/文字是否深色
     * @param options.immersive 是否沉浸式无边框（内容延伸至状态栏下）
     */
    public async setStatusBar(options: StatusBarOptions): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean }>('window', 'setStatusBar', options);
        return res.success;
    }

    /**
     * 切换或设置全屏模式（隐藏/显示系统状态栏与虚拟导航栏）
     */
    public async setFullscreen(fullscreen: boolean = true): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean }>('window', 'setFullscreen', { fullscreen });
        return res.success;
    }

    /**
     * 重新加载当前前台活跃页面
     */
    public async reload(): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean }>('window', 'reload');
        return res.success;
    }

    /**
     * 后退至上一历史记录
     */
    public async goBack(): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean }>('window', 'goBack');
        return res.success;
    }

    /**
     * 查询是否可以后退
     */
    public async canGoBack(): Promise<boolean> {
        const res = await this.client.invoke<{ canGoBack: boolean }>('window', 'canGoBack');
        return res.canGoBack;
    }

    /**
     * 控制当前前台窗口加载指定 URL
     */
    public async loadUrl(url: string): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean }>('window', 'loadUrl', { url });
        return res.success;
    }

    /**
     * 显式开启或关闭原生调试工具栏（地址栏/底栏）
     */
    public async setDebugToolbarVisible(visible: boolean): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean }>('window', 'setDebugToolbarVisible', { visible });
        return res.success;
    }
}
