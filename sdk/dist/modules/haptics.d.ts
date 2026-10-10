/**
 * @file haptics.ts
 * @description 触感与震动反馈模块
 */
import { MobileElectronClient } from '../client';
import { HapticImpactStyle, HapticNotificationType } from '../types';
export declare class HapticsModule {
    private readonly client;
    constructor(client: MobileElectronClient);
    /**
     * 触发冲击碰撞触感反馈 (支持 light, medium, heavy)
     */
    impact(style?: HapticImpactStyle): Promise<boolean>;
    /**
     * 触发状态通知触感反馈 (支持 success, warning, error)
     */
    notification(type?: HapticNotificationType): Promise<boolean>;
    /**
     * 触发刻度滚轮/选择项切换极微触感 (类似 iOS 滚轮拨动或 Android 滑动刻度)
     */
    selection(): Promise<boolean>;
    /**
     * 触发指定时长的标准系统马达震动
     * @param durationMs 震动毫秒数 (默认 200ms)
     */
    vibrate(durationMs?: number): Promise<boolean>;
}
