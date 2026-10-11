/**
 * @file statusBar.ts
 * @description 移动端原生系统状态栏与沉浸式 UI 控制模块
 */
import { MobileElectronClient } from '../client';
import { StatusBarStyle, StatusBarInfo } from '../types';
export declare class StatusBarModule {
    private client;
    constructor(client: MobileElectronClient);
    /**
     * 设置状态栏文字与图标明暗风格
     * @param style 'light' (浅色白字，适用于深色背景) 或 'dark' (深色黑字，适用于浅色背景)
     */
    setStyle(style: StatusBarStyle): Promise<boolean>;
    /**
     * 设置状态栏背景颜色 (十六进制，例如 '#FFFFFF' 或 '#0F172A')
     */
    setColor(color: string): Promise<boolean>;
    /**
     * 设置是否开启沉浸式全屏透明状态栏 (网页延伸至状态栏下方)
     */
    setTransparent(transparent?: boolean): Promise<boolean>;
    /**
     * 显示状态栏
     */
    show(): Promise<boolean>;
    /**
     * 隐藏状态栏 (进入全屏无干扰体验)
     */
    hide(): Promise<boolean>;
    /**
     * 获取状态栏详细物理尺寸 (例如状态栏高度，用于前端自定义导航栏安全区 paddingTop)
     */
    getInfo(): Promise<StatusBarInfo>;
}
