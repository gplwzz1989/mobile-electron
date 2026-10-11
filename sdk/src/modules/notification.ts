/**
 * @file notification.ts
 * @description 本地通知模块 (mobileElectron.notification)
 */

import { MobileElectronClient } from '../client';
import { NotificationOptions, NotificationResult } from '../types';

export class NotificationModule {
    constructor(private readonly client: MobileElectronClient) {}

    /**
     * 派发一条本地通知
     * @param options 通知配置项 (title, body, id, badge, data, sound)
     */
    public async show(options: NotificationOptions): Promise<NotificationResult> {
        const payload: Record<string, any> = {
            ...options
        };
        if (options.data && typeof options.data === 'object') {
            payload.data = JSON.stringify(options.data);
        }
        return this.client.invoke<NotificationResult>('notification', 'show', payload);
    }

    /**
     * 取消指定 ID 的通知
     * @param id 通知唯一 ID
     */
    public async cancel(id: number | string): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean }>('notification', 'cancel', { id });
        return res?.success ?? true;
    }

    /**
     * 取消所有已发送或挂起的本地通知
     */
    public async cancelAll(): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean }>('notification', 'cancelAll');
        return res?.success ?? true;
    }

    /**
     * 检查当前 App 是否具备发送系统通知的权限
     */
    public async areNotificationsEnabled(): Promise<boolean> {
        const res = await this.client.invoke<{ enabled: boolean }>('notification', 'areNotificationsEnabled');
        return res?.enabled ?? true;
    }
}
