/**
 * @file haptics.ts
 * @description 触感与震动反馈模块
 */

import { MobileElectronClient } from '../client';
import { HapticImpactStyle, HapticNotificationType } from '../types';

export class HapticsModule {
    constructor(private readonly client: MobileElectronClient) {}

    /**
     * 触发冲击碰撞触感反馈 (支持 light, medium, heavy)
     */
    public async impact(style: HapticImpactStyle = 'medium'): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean }>('haptics', 'impact', { style });
        return res?.success ?? true;
    }

    /**
     * 触发状态通知触感反馈 (支持 success, warning, error)
     */
    public async notification(type: HapticNotificationType = 'success'): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean }>('haptics', 'notification', { type });
        return res?.success ?? true;
    }

    /**
     * 触发刻度滚轮/选择项切换极微触感 (类似 iOS 滚轮拨动或 Android 滑动刻度)
     */
    public async selection(): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean }>('haptics', 'selection');
        return res?.success ?? true;
    }

    /**
     * 触发指定时长的标准系统马达震动
     * @param durationMs 震动毫秒数 (默认 200ms)
     */
    public async vibrate(durationMs: number = 200): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean }>('haptics', 'vibrate', { duration: durationMs });
        return res?.success ?? true;
    }
}
