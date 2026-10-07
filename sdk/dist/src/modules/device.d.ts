/**
 * @file device.ts
 * @description 原生系统设备与界面插件
 */
import { MobileElectronClient } from '../client';
export declare class DeviceModule {
    private readonly client;
    constructor(client: MobileElectronClient);
    /**
     * 读取原生系统剪贴板文本
     */
    getClipboard(): Promise<string>;
    /**
     * 将文本写入原生系统剪贴板
     */
    setClipboard(text: string): Promise<boolean>;
    /**
     * 弹出原生轻量提示 (Toast)
     */
    toast(message: string): Promise<void>;
}
