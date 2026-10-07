/**
 * @file window.ts
 * @description 宿主窗口、状态栏沉浸、全屏与导航控制模块
 */
import { MobileElectronClient } from '../client';
import { StatusBarOptions } from '../types';
export declare class WindowModule {
    private readonly client;
    constructor(client: MobileElectronClient);
    /**
     * 设置原生状态栏样式
     * @param options.color 状态栏颜色十六进制，例如 '#FFFFFF'
     * @param options.darkIcons 图标/文字是否深色
     * @param options.immersive 是否沉浸式无边框（内容延伸至状态栏下）
     */
    setStatusBar(options: StatusBarOptions): Promise<boolean>;
    /**
     * 切换或设置全屏模式（隐藏/显示系统状态栏与虚拟导航栏）
     */
    setFullscreen(fullscreen?: boolean): Promise<boolean>;
    /**
     * 重新加载当前前台活跃页面
     */
    reload(): Promise<boolean>;
    /**
     * 后退至上一历史记录
     */
    goBack(): Promise<boolean>;
    /**
     * 查询是否可以后退
     */
    canGoBack(): Promise<boolean>;
    /**
     * 控制当前前台窗口加载指定 URL
     */
    loadUrl(url: string): Promise<boolean>;
    /**
     * 显式开启或关闭原生调试工具栏（地址栏/底栏）
     */
    setDebugToolbarVisible(visible: boolean): Promise<boolean>;
}
