/**
 * @file ipc.ts
 * @description 跨窗口全双工 IPC 通信总线模块 (Cross-Window Full-Duplex IPC Channel)
 * 支持主窗口、独立沙箱页面与画中画悬浮窗之间的双向消息投递与广播
 */
import { MobileElectronClient } from '../client';
import { IpcMessageEvent, UnsubscribeFn } from '../types';
export declare class IpcModule {
    private readonly client;
    constructor(client: MobileElectronClient);
    /**
     * 发送 IPC 消息给其他所有窗口 (广播模式)
     * @param channel 消息频道 / 主题
     * @param payload 携带的数据载荷
     */
    send(channel: string, payload?: any): Promise<boolean>;
    /**
     * 向指定目标窗口定向投递 IPC 消息
     * @param targetId 目标窗口 ID（例如 'main', 'pip_player_01', 'sandbox_01'）
     * @param channel 消息频道 / 主题
     * @param payload 携带的数据载荷
     */
    sendTo(targetId: string, channel: string, payload?: any): Promise<boolean>;
    /**
     * 向所有其他活跃窗口广播 IPC 消息
     * @param channel 消息频道 / 主题
     * @param payload 携带的数据载荷
     */
    broadcast(channel: string, payload?: any): Promise<boolean>;
    /**
     * 持续监听指定 Channel 的跨窗口 IPC 消息
     * @param channel 消息频道 / 主题
     * @param listener 消息接收回调
     * @returns 取消监听的函数
     */
    on<T = any>(channel: string, listener: (event: IpcMessageEvent<T>) => void): UnsubscribeFn;
    /**
     * 仅监听一次指定 Channel 的跨窗口 IPC 消息，触发后自动注销
     * @param channel 消息频道 / 主题
     * @param listener 消息接收回调
     */
    once<T = any>(channel: string, listener: (event: IpcMessageEvent<T>) => void): UnsubscribeFn;
}
