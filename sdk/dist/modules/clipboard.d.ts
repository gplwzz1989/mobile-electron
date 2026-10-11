/**
 * @file clipboard.ts
 * @description 系统剪贴板模块 (mobileElectron.clipboard)
 */
import { MobileElectronClient } from '../client';
export declare class ClipboardModule {
    private readonly client;
    constructor(client: MobileElectronClient);
    /**
     * 读取系统剪贴板中的纯文本内容
     */
    readText(): Promise<string>;
    /**
     * 将指定纯文本写入系统剪贴板
     * @param text 要写入的文本
     */
    writeText(text: string): Promise<boolean>;
    /**
     * 判断系统剪贴板当前是否包含有效文本
     */
    hasText(): Promise<boolean>;
    /**
     * 清空系统剪贴板中的内容
     */
    clear(): Promise<boolean>;
}
