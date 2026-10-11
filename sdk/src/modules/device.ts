/**
 * @file device.ts
 * @description 原生系统设备与界面插件 (支持详细硬件配置查询与交互)
 */

import { MobileElectronClient } from '../client';
import { DeviceInfo } from '../types';

export class DeviceModule {
    constructor(private readonly client: MobileElectronClient) {}

    /**
     * 获取当前物理设备的软硬件核心信息 (机型、系统版本、屏幕物理分辨率、UUID/设备唯一标识等)
     */
    public async getInfo(): Promise<DeviceInfo> {
        return this.client.invoke<DeviceInfo>('device', 'getInfo');
    }

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
