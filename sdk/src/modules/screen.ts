/**
 * @file screen.ts
 * @description 移动端屏幕显示、横竖屏旋转与亮度控制模块
 */

import { MobileElectronClient } from '../client';
import { ScreenOrientation } from '../types';

export class ScreenModule {
    private client: MobileElectronClient;

    constructor(client: MobileElectronClient) {
        this.client = client;
    }

    /**
     * 锁定或切换屏幕方向
     * @param orientation 'portrait' (竖屏) | 'landscape' (横屏) | 'sensor' | 'auto' (随重力感应自动旋转)
     */
    public async setOrientation(orientation: ScreenOrientation): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean }>('screen', 'setOrientation', { orientation });
        return res?.success ?? true;
    }

    /**
     * 获取当前屏幕方向 ('portrait' 或 'landscape')
     */
    public async getOrientation(): Promise<'portrait' | 'landscape'> {
        const res = await this.client.invoke<{ orientation: 'portrait' | 'landscape' }>('screen', 'getOrientation');
        return res?.orientation ?? 'portrait';
    }

    /**
     * 设置屏幕防息屏常亮 (常用于视频播放、扫码或阅读场景)
     */
    public async setKeepScreenOn(keepScreenOn: boolean = true): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean }>('screen', 'setKeepScreenOn', { keepScreenOn });
        return res?.success ?? true;
    }

    /**
     * 调节当前窗口屏幕亮度
     * @param brightness 亮度浮点数值 0.0 ~ 1.0 (传入 -1 恢复系统默认自动亮度)
     */
    public async setBrightness(brightness: number): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean }>('screen', 'setBrightness', { brightness });
        return res?.success ?? true;
    }

    /**
     * 获取当前窗口屏幕亮度
     */
    public async getBrightness(): Promise<number> {
        const res = await this.client.invoke<{ brightness: number }>('screen', 'getBrightness');
        return res?.brightness ?? -1;
    }
}
