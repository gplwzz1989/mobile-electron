/**
 * @file cookie.ts
 * @description 底层 Cookie 管理模块（支持全局或针对特定 profile / pageId 独立读写）
 */
import { MobileElectronClient } from '../client';
export declare class CookieModule {
    private readonly client;
    constructor(client: MobileElectronClient);
    /**
     * 读取指定 URL 下的 Cookie 字符串
     * @param url 目标网站 URL
     * @param target 可选指定读取哪个 pageId 或 profile 独立分区
     */
    get(url: string, target?: {
        pageId?: string;
        profile?: string;
    }): Promise<string>;
    /**
     * 导入或设置 Cookie
     * @param url 目标网站 URL
     * @param rawInput 支持格式: "key=val; key2=val2" 或 JSON 数组形式
     * @param target 可选指定写入哪个 pageId 或 profile 独立分区
     * @returns 成功设置的 Cookie 键值对数量
     */
    set(url: string, rawInput: string, target?: {
        pageId?: string;
        profile?: string;
    }): Promise<number>;
    /**
     * 清空指定分区或全局 Cookie
     */
    clear(target?: {
        pageId?: string;
        profile?: string;
    }): Promise<boolean>;
}
