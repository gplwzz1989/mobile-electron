/**
 * @file device.ts
 * @description 原生系统设备与界面插件
 */

import { MobileElectronClient } from '../client';

export class DeviceModule {
    constructor(private readonly client: MobileElectronClient) {}

    /**
     * 读取原生系统剪贴板文本
     */
    public async getClipboard(): Promise<string> {
        const res = await this.client.invoke<{ text: string }>('device', 'getClipboard');
        return res.text;
    }

    /**
     * 将文本写入原生系统剪贴板
     */
    public async setClipboard(text: string): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean }>('device', 'setClipboard', { text });
        return res.success;
    }

    /**
     * 弹出原生轻量提示 (Toast)
     */
    public async toast(message: string): Promise<void> {
        return this.client.invoke<void>('device', 'toast', { message });
    }
}
