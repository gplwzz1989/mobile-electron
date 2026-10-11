/**
 * @file statusBar.ts
 * @description 移动端原生系统状态栏与沉浸式 UI 控制模块
 */

import { MobileElectronClient } from '../client';
import { StatusBarStyle, StatusBarInfo } from '../types';

export class StatusBarModule {
    private client: MobileElectronClient;

    constructor(client: MobileElectronClient) {
        this.client = client;
    }

    /**
     * 设置状态栏文字与图标明暗风格
     * @param style 'light' (浅色白字，适用于深色背景) 或 'dark' (深色黑字，适用于浅色背景)
     */
    public async setStyle(style: StatusBarStyle): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean }>('statusBar', 'setStyle', { style });
        return res?.success ?? true;
    }

    /**
     * 设置状态栏背景颜色 (十六进制，例如 '#FFFFFF' 或 '#0F172A')
     */
    public async setColor(color: string): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean }>('statusBar', 'setColor', { color });
        return res?.success ?? true;
    }

    /**
     * 设置是否开启沉浸式全屏透明状态栏 (网页延伸至状态栏下方)
     */
    public async setTransparent(transparent: boolean = true): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean }>('statusBar', 'setTransparent', { transparent });
        return res?.success ?? true;
    }

    /**
     * 显示状态栏
     */
    public async show(): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean }>('statusBar', 'show');
        return res?.success ?? true;
    }

    /**
     * 隐藏状态栏 (进入全屏无干扰体验)
     */
    public async hide(): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean }>('statusBar', 'hide');
        return res?.success ?? true;
    }

    /**
     * 获取状态栏详细物理尺寸 (例如状态栏高度，用于前端自定义导航栏安全区 paddingTop)
     */
    public async getInfo(): Promise<StatusBarInfo> {
        return this.client.invoke<StatusBarInfo>('statusBar', 'getInfo');
    }
}
