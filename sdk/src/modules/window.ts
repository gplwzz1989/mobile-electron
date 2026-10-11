/**
 * @file window.ts
 * @description 宿主窗口、状态栏沉浸、全屏与导航控制模块
 */

import { MobileElectronClient } from '../client';
import {
    StatusBarOptions,
    FloatingWindowOptions,
    FloatingWindowResult,
    FloatingWindowState,
    FloatingWindowUpdateOptions
} from '../types';

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

    /**
     * 创建并在当前应用视图内挂载轻量级画中画 / 悬浮多窗口 (Floating Picture-in-Picture Window)
     * @param options 浮窗配置项（url, title, width, height, x, y, draggable, showControls）
     */
    public async createFloatingWindow(options: FloatingWindowOptions): Promise<FloatingWindowResult> {
        return await this.client.invoke<FloatingWindowResult>('window', 'createFloatingWindow', options);
    }

    /**
     * 关闭并销毁悬浮小窗
     * @param id 浮窗 ID（若未提供则关闭默认或活动中的悬浮窗）
     */
    public async closeFloatingWindow(id?: string): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean }>('window', 'closeFloatingWindow', { id });
        return res.success;
    }

    /**
     * 显式显示或隐藏悬浮窗
     */
    public async setFloatingWindowVisible(id: string, visible: boolean = true): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean }>('window', 'setFloatingWindowVisible', { id, visible });
        return res.success;
    }

    /**
     * 最小化悬浮窗为轻量微型浮标，或从微型浮标还原
     */
    public async minimizeFloatingWindow(id: string, minimized: boolean = true): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean }>('window', 'minimizeFloatingWindow', { id, minimized });
        return res.success;
    }

    /**
     * 动态调整悬浮窗位置与尺寸
     */
    public async updateFloatingWindow(id: string, options: FloatingWindowUpdateOptions): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean }>('window', 'updateFloatingWindow', { id, ...options });
        return res.success;
    }

    /**
     * 向悬浮窗内嵌的独立 WebView 执行 JavaScript 脚本并获取执行结果
     */
    public async evalFloatingWindow<T = any>(id: string, script: string): Promise<T> {
        const res = await this.client.invoke<{ success: boolean; result: T }>('window', 'evalFloatingWindow', { id, script });
        return res.result;
    }

    /**
     * 查询悬浮窗当前实时状态（坐标、尺寸、是否最小化、可见性等）
     */
    public async getFloatingWindowState(id?: string): Promise<FloatingWindowState | null> {
        try {
            return await this.client.invoke<FloatingWindowState>('window', 'getFloatingWindowState', { id });
        } catch {
            return null;
        }
    }
}

