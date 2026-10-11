/**
 * @file screen.ts
 * @description 移动端屏幕显示、横竖屏旋转与亮度控制模块
 */
import { MobileElectronClient } from '../client';
import { ScreenOrientation } from '../types';
export declare class ScreenModule {
    private client;
    constructor(client: MobileElectronClient);
    /**
     * 锁定或切换屏幕方向
     * @param orientation 'portrait' (竖屏) | 'landscape' (横屏) | 'sensor' | 'auto' (随重力感应自动旋转)
     */
    setOrientation(orientation: ScreenOrientation): Promise<boolean>;
    /**
     * 获取当前屏幕方向 ('portrait' 或 'landscape')
     */
    getOrientation(): Promise<'portrait' | 'landscape'>;
    /**
     * 设置屏幕防息屏常亮 (常用于视频播放、扫码或阅读场景)
     */
    setKeepScreenOn(keepScreenOn?: boolean): Promise<boolean>;
    /**
     * 调节当前窗口屏幕亮度
     * @param brightness 亮度浮点数值 0.0 ~ 1.0 (传入 -1 恢复系统默认自动亮度)
     */
    setBrightness(brightness: number): Promise<boolean>;
    /**
     * 获取当前窗口屏幕亮度
     */
    getBrightness(): Promise<number>;
}
