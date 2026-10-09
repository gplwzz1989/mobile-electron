/**
 * @file debug.ts
 * @description Mobile Electron 框架调试与 DevTools 模块
 * 支持调出框架调试中心、针对当前 WebView 唤出/收起 DevTools、获取调试状态等
 */

import { MobileElectronClient } from '../client';
import { FrameworkDebugInfo, DevToolsResult } from '../types';

export class DebugModule {
    private readonly client: MobileElectronClient;

    constructor(client: MobileElectronClient) {
        this.client = client;
    }

    /**
     * 调出框架调试中心对话框
     */
    public async show(): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean; visible: boolean }>('debug', 'show', {});
        return res.visible;
    }

    /**
     * 关闭框架调试中心对话框
     */
    public async hide(): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean; visible: boolean }>('debug', 'hide', {});
        return res.visible;
    }

    /**
     * 切换框架调试中心对话框显隐状态
     */
    public async toggle(): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean; visible: boolean }>('debug', 'toggle', {});
        return res.visible;
    }

    /**
     * 打开当前活跃 WebView 的 DevTools（在手机端唤出 Eruda 控制台，并确认开启 Chromium 远程调试）
     */
    public async openDevTools(): Promise<DevToolsResult> {
        return this.client.invoke<DevToolsResult>('debug', 'openDevTools', {});
    }

    /**
     * 关闭当前活跃 WebView 的 DevTools
     */
    public async closeDevTools(): Promise<DevToolsResult> {
        return this.client.invoke<DevToolsResult>('debug', 'closeDevTools', {});
    }

    /**
     * 切换当前活跃 WebView 的 DevTools 显隐
     */
    public async toggleDevTools(): Promise<DevToolsResult> {
        return this.client.invoke<DevToolsResult>('debug', 'toggleDevTools', {});
    }

    /**
     * 获取框架运行环境与调试信息
     */
    public async getInfo(): Promise<FrameworkDebugInfo> {
        return this.client.invoke<FrameworkDebugInfo>('debug', 'getInfo', {});
    }

    /**
     * 设置屏幕右下角悬浮调试球显隐
     */
    public async setFloatingButtonVisible(visible: boolean): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean; visible: boolean }>('debug', 'setFloatingButtonVisible', { visible });
        return res.visible;
    }
}
