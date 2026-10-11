/**
 * @file device.ts
 * @description 原生系统设备与界面插件 (支持详细硬件配置查询与交互)
 */
import { MobileElectronClient } from '../client';
import { DeviceInfo } from '../types';
export declare class DeviceModule {
    private readonly client;
    constructor(client: MobileElectronClient);
    /**
     * 获取当前物理设备的软硬件核心信息 (机型、系统版本、屏幕物理分辨率、UUID/设备唯一标识等)
     */
    getInfo(): Promise<DeviceInfo>;
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
