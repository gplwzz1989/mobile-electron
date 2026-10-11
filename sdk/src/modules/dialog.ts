/**
 * @file dialog.ts
 * @description 原生交互弹窗与对话框模块 (无阻塞 Alert/Confirm/Prompt、ActionSheet 与原生系统分享)
 */

import { MobileElectronClient } from '../client';
import { AlertOptions, ConfirmOptions, PromptOptions, ActionSheetOptions, ActionSheetResult, ShareOptions } from '../types';

export class DialogModule {
    constructor(private readonly client: MobileElectronClient) {}

    /**
     * 弹出原生提示框 (Alert)
     */
    public async alert(message: string, title?: string, buttonText?: string): Promise<boolean>;
    public async alert(options: AlertOptions): Promise<boolean>;
    public async alert(arg1: string | AlertOptions, title?: string, buttonText?: string): Promise<boolean> {
        const options: AlertOptions = typeof arg1 === 'string'
            ? { message: arg1, title: title || '提示', buttonText: buttonText || '确定' }
            : arg1;
        const res = await this.client.invoke<{ confirmed: boolean }>('dialog', 'alert', options as any);
        return res.confirmed;
    }

    /**
     * 弹出原生确认选择框 (Confirm)
     */
    public async confirm(message: string, title?: string): Promise<boolean>;
    public async confirm(options: ConfirmOptions): Promise<boolean>;
    public async confirm(arg1: string | ConfirmOptions, title?: string): Promise<boolean> {
        const options: ConfirmOptions = typeof arg1 === 'string'
            ? { message: arg1, title: title || '请确认', confirmText: '确认', cancelText: '取消' }
            : arg1;
        const res = await this.client.invoke<{ confirmed: boolean }>('dialog', 'confirm', options as any);
        return res.confirmed;
    }

    /**
     * 弹出原生文本输入框 (Prompt)
     */
    public async prompt(message: string, defaultValue?: string, title?: string): Promise<string | null>;
    public async prompt(options: PromptOptions): Promise<string | null>;
    public async prompt(arg1: string | PromptOptions, defaultValue?: string, title?: string): Promise<string | null> {
        const options: PromptOptions = typeof arg1 === 'string'
            ? { message: arg1, defaultValue: defaultValue || '', title: title || '请输入' }
            : arg1;
        const res = await this.client.invoke<{ confirmed: boolean; value: string | null }>('dialog', 'prompt', options as any);
        return res.confirmed ? res.value : null;
    }

    /**
     * 弹出原生操作选择列表 (ActionSheet 底部菜单)
     * @param options 包含 options 选项数组、标题与取消文本
     */
    public async actionSheet(options: ActionSheetOptions): Promise<ActionSheetResult> {
        return this.client.invoke<ActionSheetResult>('dialog', 'actionSheet', options as any);
    }

    /**
     * 唤起系统级原生分享面板 (ShareSheet)
     * 支持分享文本、网页 URL 链接至微信、系统好友等
     */
    public async share(options: ShareOptions): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean }>('dialog', 'share', options as any);
        return res?.success ?? true;
    }
}
