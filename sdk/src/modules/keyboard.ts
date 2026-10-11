/**
 * @file keyboard.ts
 * @description 移动端软键盘交互控制与避让高度感知模块
 */

import { MobileElectronClient } from '../client';
import { KeyboardVisibilityEvent } from '../types';

export class KeyboardModule {
    private client: MobileElectronClient;

    constructor(client: MobileElectronClient) {
        this.client = client;
    }

    /**
     * 手动收起并隐藏系统软键盘
     */
    public async hide(): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean }>('keyboard', 'hide');
        return res?.success ?? true;
    }

    /**
     * 尝试拉起软键盘
     */
    public async show(): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean }>('keyboard', 'show');
        return res?.success ?? true;
    }

    /**
     * 查询当前软键盘是否处于可见弹起状态及其弹起高度
     */
    public async isVisible(): Promise<KeyboardVisibilityEvent> {
        return this.client.invoke<KeyboardVisibilityEvent>('keyboard', 'isVisible');
    }

    /**
     * 监听系统软键盘弹起与收起事件，以及软键盘弹起高度变化
     * (解决移动端 Web 输入框被软键盘遮挡时的页面自适应上推避让)
     * @param listener 状态回调函数
     * @returns 解除监听的函数
     */
    public onVisibilityChange(listener: (event: KeyboardVisibilityEvent) => void): () => void {
        return this.client.on<KeyboardVisibilityEvent>('keyboard.onVisibilityChange', listener);
    }
}
