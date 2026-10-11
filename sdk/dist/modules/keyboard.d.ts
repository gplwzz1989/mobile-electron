/**
 * @file keyboard.ts
 * @description 移动端软键盘交互控制与避让高度感知模块
 */
import { MobileElectronClient } from '../client';
import { KeyboardVisibilityEvent } from '../types';
export declare class KeyboardModule {
    private client;
    constructor(client: MobileElectronClient);
    /**
     * 手动收起并隐藏系统软键盘
     */
    hide(): Promise<boolean>;
    /**
     * 尝试拉起软键盘
     */
    show(): Promise<boolean>;
    /**
     * 查询当前软键盘是否处于可见弹起状态及其弹起高度
     */
    isVisible(): Promise<KeyboardVisibilityEvent>;
    /**
     * 监听系统软键盘弹起与收起事件，以及软键盘弹起高度变化
     * (解决移动端 Web 输入框被软键盘遮挡时的页面自适应上推避让)
     * @param listener 状态回调函数
     * @returns 解除监听的函数
     */
    onVisibilityChange(listener: (event: KeyboardVisibilityEvent) => void): () => void;
}
