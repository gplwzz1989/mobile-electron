/**
 * @file cookie.ts
 * @description 底层 Cookie 管理模块（支持全局或针对特定 profile / pageId 独立读写）
 */

import { MobileElectronClient } from '../client';

export class CookieModule {
    constructor(private readonly client: MobileElectronClient) {}

    /**
     * 读取指定 URL 下的 Cookie 字符串
     * @param url 目标网站 URL
     * @param target 可选指定读取哪个 pageId 或 profile 独立分区
     */
    public async get(url: string, target?: { pageId?: string; profile?: string }): Promise<string> {
        const res = await this.client.invoke<{ cookies: string; profile: string }>('cookie', 'get', {
            url,
            pageId: target?.pageId,
            profile: target?.profile
        });
        return res.cookies;
    }

    /**
     * 导入或设置 Cookie
     * @param url 目标网站 URL
     * @param rawInput 支持格式: "key=val; key2=val2" 或 JSON 数组形式
     * @param target 可选指定写入哪个 pageId 或 profile 独立分区
     * @returns 成功设置的 Cookie 键值对数量
     */
    public async set(url: string, rawInput: string, target?: { pageId?: string; profile?: string }): Promise<number> {
        const res = await this.client.invoke<{ count: number; profile: string }>('cookie', 'import', {
            url,
            input: rawInput,
            pageId: target?.pageId,
            profile: target?.profile
        });
        return res.count;
    }

    /**
     * 清空指定分区或全局 Cookie
     */
    public async clear(target?: { pageId?: string; profile?: string }): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean; profile: string }>('cookie', 'clear', {
            pageId: target?.pageId,
            profile: target?.profile
        });
        return res.success;
    }
}
