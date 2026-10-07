/**
 * @file dialog.ts
 * @description 原生交互弹窗与对话框模块 (无阻塞 Material/Cupertino 对话框)
 */
import { MobileElectronClient } from '../client';
import { AlertOptions, ConfirmOptions, PromptOptions } from '../types';
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
}
