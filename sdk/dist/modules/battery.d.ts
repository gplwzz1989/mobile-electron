/**
 * @file battery.ts
 * @description 移动端电池电量与充电状态感知模块
 */
import { MobileElectronClient } from '../client';
import { BatteryStatus } from '../types';
export declare class BatteryModule {
    private client;
    constructor(client: MobileElectronClient);
    /**
     * 获取当前系统电池状态 (电量百分比 0-100、是否在充电、充电方式)
     */
    getStatus(): Promise<BatteryStatus>;
    /**
     * 监听系统电池电量或充电状态变化
     * @param listener 状态回调函数
     * @returns 解除监听的函数
     */
    onChange(listener: (status: BatteryStatus) => void): () => void;
}
