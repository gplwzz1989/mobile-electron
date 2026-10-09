/**
 * @file debug.ts
 * @description Mobile Electron 框架调试与 DevTools 模块
 * 支持调出框架调试中心、针对当前 WebView 唤出/收起 DevTools、获取调试状态等
 */
import { MobileElectronClient } from '../client';
import { FrameworkDebugInfo, DevToolsResult } from '../types';
export declare class DebugModule {
    private readonly client;
    constructor(client: MobileElectronClient);
    /**
     * 调出框架调试中心对话框
     */
    show(): Promise<boolean>;
    /**
     * 关闭框架调试中心对话框
     */
    hide(): Promise<boolean>;
    /**
     * 切换框架调试中心对话框显隐状态
     */
    toggle(): Promise<boolean>;
    /**
     * 打开当前活跃 WebView 的 DevTools（在手机端唤出 Eruda 控制台，并确认开启 Chromium 远程调试）
     */
    openDevTools(): Promise<DevToolsResult>;
    /**
     * 关闭当前活跃 WebView 的 DevTools
     */
    closeDevTools(): Promise<DevToolsResult>;
    /**
     * 切换当前活跃 WebView 的 DevTools 显隐
     */
    toggleDevTools(): Promise<DevToolsResult>;
    /**
     * 获取框架运行环境与调试信息
     */
    getInfo(): Promise<FrameworkDebugInfo>;
    /**
     * 设置屏幕右下角悬浮调试球显隐
     */
    setFloatingButtonVisible(visible: boolean): Promise<boolean>;
}
