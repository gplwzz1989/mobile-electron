/**
 * @file window.ts
 * @description 宿主窗口、状态栏沉浸、全屏与导航控制模块
 */
import { MobileElectronClient } from '../client';
import { StatusBarOptions, FloatingWindowOptions, FloatingWindowResult, FloatingWindowState, FloatingWindowUpdateOptions } from '../types';
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
    /**
     * 创建并在当前应用视图内挂载轻量级画中画 / 悬浮多窗口 (Floating Picture-in-Picture Window)
     * @param options 浮窗配置项（url, title, width, height, x, y, draggable, showControls）
     */
    createFloatingWindow(options: FloatingWindowOptions): Promise<FloatingWindowResult>;
    /**
     * 关闭并销毁悬浮小窗
     * @param id 浮窗 ID（若未提供则关闭默认或活动中的悬浮窗）
     */
    closeFloatingWindow(id?: string): Promise<boolean>;
    /**
     * 显式显示或隐藏悬浮窗
     */
    setFloatingWindowVisible(id: string, visible?: boolean): Promise<boolean>;
    /**
     * 最小化悬浮窗为轻量微型浮标，或从微型浮标还原
     */
    minimizeFloatingWindow(id: string, minimized?: boolean): Promise<boolean>;
    /**
     * 动态调整悬浮窗位置与尺寸
     */
    updateFloatingWindow(id: string, options: FloatingWindowUpdateOptions): Promise<boolean>;
    /**
     * 向悬浮窗内嵌的独立 WebView 执行 JavaScript 脚本并获取执行结果
     */
    evalFloatingWindow<T = any>(id: string, script: string): Promise<T>;
    /**
     * 查询悬浮窗当前实时状态（坐标、尺寸、是否最小化、可见性等）
     */
    getFloatingWindowState(id?: string): Promise<FloatingWindowState | null>;
}
