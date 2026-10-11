/**
 * @file dialog.ts
 * @description 原生交互弹窗与对话框模块 (无阻塞 Alert/Confirm/Prompt、ActionSheet 与原生系统分享)
 */
import { MobileElectronClient } from '../client';
import { AlertOptions, ConfirmOptions, PromptOptions, ActionSheetOptions, ActionSheetResult, ShareOptions } from '../types';
export declare class DialogModule {
    private readonly client;
    constructor(client: MobileElectronClient);
    /**
     * 弹出原生提示框 (Alert)
     */
    alert(message: string, title?: string, buttonText?: string): Promise<boolean>;
    alert(options: AlertOptions): Promise<boolean>;
    /**
     * 弹出原生确认选择框 (Confirm)
     */
    confirm(message: string, title?: string): Promise<boolean>;
    confirm(options: ConfirmOptions): Promise<boolean>;
    /**
     * 弹出原生文本输入框 (Prompt)
     */
    prompt(message: string, defaultValue?: string, title?: string): Promise<string | null>;
    prompt(options: PromptOptions): Promise<string | null>;
    /**
     * 弹出原生操作选择列表 (ActionSheet 底部菜单)
     * @param options 包含 options 选项数组、标题与取消文本
     */
    actionSheet(options: ActionSheetOptions): Promise<ActionSheetResult>;
    /**
     * 唤起系统级原生分享面板 (ShareSheet)
     * 支持分享文本、网页 URL 链接至微信、系统好友等
     */
    share(options: ShareOptions): Promise<boolean>;
}
