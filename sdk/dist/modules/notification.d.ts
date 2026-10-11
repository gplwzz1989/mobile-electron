/**
 * @file notification.ts
 * @description 本地通知模块 (mobileElectron.notification)
 */
import { MobileElectronClient } from '../client';
import { NotificationOptions, NotificationResult } from '../types';
export declare class NotificationModule {
    private readonly client;
    constructor(client: MobileElectronClient);
    /**
     * 派发一条本地通知
     * @param options 通知配置项 (title, body, id, badge, data, sound)
     */
    show(options: NotificationOptions): Promise<NotificationResult>;
    /**
     * 取消指定 ID 的通知
     * @param id 通知唯一 ID
     */
    cancel(id: number | string): Promise<boolean>;
    /**
     * 取消所有已发送或挂起的本地通知
     */
    cancelAll(): Promise<boolean>;
    /**
     * 检查当前 App 是否具备发送系统通知的权限
     */
    areNotificationsEnabled(): Promise<boolean>;
}
