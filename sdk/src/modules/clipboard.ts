/**
 * @file clipboard.ts
 * @description 系统剪贴板模块 (mobileElectron.clipboard)
 */

import { MobileElectronClient } from '../client';

export class ClipboardModule {
    constructor(private readonly client: MobileElectronClient) {}

    /**
     * 读取系统剪贴板中的纯文本内容
     */
    public async readText(): Promise<string> {
        const res = await this.client.invoke<{ text: string }>('clipboard', 'readText');
        return res?.text ?? '';
    }

    /**
     * 将指定纯文本写入系统剪贴板
     * @param text 要写入的文本
     */
    public async writeText(text: string): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean }>('clipboard', 'writeText', { text });
        return res?.success ?? true;
    }

    /**
     * 判断系统剪贴板当前是否包含有效文本
     */
    public async hasText(): Promise<boolean> {
        const res = await this.client.invoke<{ hasText: boolean }>('clipboard', 'hasText');
        return res?.hasText ?? false;
    }

    /**
     * 清空系统剪贴板中的内容
     */
    public async clear(): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean }>('clipboard', 'clear');
        return res?.success ?? true;
    }
}
