/**
 * @file tabBar.ts
 * @description Mobile Electron 原生 TabBar 模块
 * 支持动态设置选项卡（标题、图标、角标徽标、背景色）以及监听点击事件
 */
import { MobileElectronClient } from '../client';
import { TabBarItem, TabBarOptions, TabBarState, TabBarClickEvent, UnsubscribeFn } from '../types';
export declare class TabBarModule {
    private readonly client;
    constructor(client: MobileElectronClient);
    /**
     * 动态设置原生 TabBar 的列表项与样式配置
     * @param items TabBar 项数组（每个包含 id, title, icon 等）
     * @param options 可选配置（selectedId, backgroundColor, color, selectedColor, visible 等）
     */
    setItems(items: TabBarItem[], options?: TabBarOptions): Promise<{
        success: boolean;
        count: number;
    }>;
    /**
     * 显示原生 TabBar
     */
    show(): Promise<boolean>;
    /**
     * 隐藏原生 TabBar
     */
    hide(): Promise<boolean>;
    /**
     * 设置原生 TabBar 是否可见
     */
    setVisible(visible: boolean): Promise<boolean>;
    /**
     * 切换原生 TabBar 的显示/隐藏状态
     */
    toggle(): Promise<boolean>;
    /**
     * 动态设置当前选中的 Tab
     * @param idOrIndex 选项卡 ID 或数字索引
     */
    setSelected(idOrIndex: string | number): Promise<boolean>;
    /**
     * 动态设置某个 Tab 的角标徽标内容
     * @param idOrIndex 选项卡 ID 或数字索引
     * @param badge 角标文本（传空字符串隐藏）
     */
    setBadge(idOrIndex: string | number, badge: string): Promise<boolean>;
    /**
     * 获取当前 TabBar 的状态（包含可见性、选项卡列表、当前选中项）
     */
    getState(): Promise<TabBarState>;
    /**
     * 监听原生 TabBar 的点击事件
     * @param listener 点击回调函数，接收点击的 tab 项信息 { id, index, title }
     * @returns 取消监听的函数
     */
    onTabClick(listener: (tab: TabBarClickEvent) => void): UnsubscribeFn;
}
