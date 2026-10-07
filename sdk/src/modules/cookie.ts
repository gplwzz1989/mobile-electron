/**
 * @file cookie.ts
 * @description 底层 Cookie 管理模块（支持全量提取包括 HttpOnly 的所有 Cookie、跨域名读取、独立分区 Profile / PageId 隔离）
 */

import { MobileElectronClient } from '../client';
import { CookieDetail } from '../types';

export class CookieModule {
    constructor(private readonly client: MobileElectronClient) {}

    /**
     * 读取指定 URL 下的 Cookie 字符串 (标准格式 a=b; c=d)
     * 无论是否带有 HttpOnly 属性均完整提取
     * @param url 目标网站 URL（为空则自动使用目标 PageId 的当前页面地址）
     * @param target 可选指定读取哪个 pageId 或 profile 独立分区
     */
    public async get(url?: string, target?: { pageId?: string; profile?: string }): Promise<string> {
        const res = await this.client.invoke<{ cookies: string; profile: string }>('cookie', 'get', {
            url,
            pageId: target?.pageId,
            profile: target?.profile
        });
        return res.cookies;
    }

    /**
     * 全量提取指定 WebView 实例或 Profile 分区下的所有 Cookie 结构化对象（包括所有 HttpOnly、Secure、Domain、Path、Expires）
     * @param options 可选配置过滤条件（pageId / profile / url / domain / all）
     */
    public async getAll(options?: {
        pageId?: string;
        profile?: string;
        url?: string;
        domain?: string;
        all?: boolean;
    }): Promise<CookieDetail[]> {
        const res = await this.client.invoke<{ details: CookieDetail[]; cookies: string; count: number; profile: string }>(
            'cookie',
            'getAll',
            {
                pageId: options?.pageId,
                profile: options?.profile,
                url: options?.url,
                domain: options?.domain,
                all: options?.all ?? true
            }
        );
        return res.details || [];
    }

    /**
     * 提取带完整元数据（HttpOnly、Domain、Path、Secure）的 Cookie 结构体列表
     * @param url 目标 URL
     * @param target 目标 pageId 或 profile
     */
    public async getDetails(url?: string, target?: { pageId?: string; profile?: string }): Promise<CookieDetail[]> {
        const res = await this.client.invoke<{ details: CookieDetail[]; cookies: string; profile: string }>('cookie', 'get', {
            url,
            pageId: target?.pageId,
            profile: target?.profile
        });
        return res.details || [];
    }

    /**
     * 导入或设置 Cookie（支持标准字符串、Set-Cookie 格式、或 CookieDetail 结构化对象数组）
     * 支持显式指定 httpOnly: true 属性精准注入
     * @param url 目标网站 URL
     * @param cookies 支持格式: "key=val; key2=val2" 或 CookieDetail[] 结构化数组
     * @param target 可选指定写入哪个 pageId 或 profile 独立分区
     * @returns 成功设置的 Cookie 键值对数量
     */
    public async set(
        url: string,
        cookies: string | CookieDetail[],
        target?: { pageId?: string; profile?: string }
    ): Promise<number> {
        const payload: Record<string, any> = {
            url,
            pageId: target?.pageId,
            profile: target?.profile
        };

        if (Array.isArray(cookies)) {
            payload.cookies = cookies;
        } else {
            payload.input = cookies;
            payload.cookies = cookies;
        }

        const res = await this.client.invoke<{ count: number; profile: string }>('cookie', 'set', payload);
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
