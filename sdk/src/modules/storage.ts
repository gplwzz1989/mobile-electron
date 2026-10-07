/**
 * @file storage.ts
 * @description 综合存储控制模块 (原生持久化键值存储 + 任意 WebView 实例的 LocalStorage / SessionStorage 读写)
 */

import { MobileElectronClient } from '../client';
import { StorageDump } from '../types';

export class StorageModule {
    constructor(private readonly client: MobileElectronClient) {}

    // ==================== 1. 原生跨页面持久化存储 (突破 Web 5MB 限制) ====================

    /**
     * 保存键值数据至原生持久化层
     */
    public async set(key: string, value: string): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean }>('storage', 'set', { key, value });
        return res.success;
    }

    /**
     * 读取原生持久化数据（未找到返回 null）
     */
    public async get(key: string): Promise<string | null> {
        const res = await this.client.invoke<{ value: string | null; exists: boolean }>('storage', 'get', { key });
        return res.value;
    }

    /**
     * 快捷保存 JSON 对象
     */
    public async setObject<T = any>(key: string, object: T): Promise<boolean> {
        return this.set(key, JSON.stringify(object));
    }

    /**
     * 快捷读取 JSON 对象
     */
    public async getObject<T = any>(key: string): Promise<T | null> {
        const str = await this.get(key);
        if (!str) return null;
        try {
            return JSON.parse(str) as T;
        } catch {
            return null;
        }
    }

    /**
     * 删除指定原生持久化键
     */
    public async remove(key: string): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean }>('storage', 'remove', { key });
        return res.success;
    }

    /**
     * 清空全部原生持久化数据
     */
    public async clear(): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean }>('storage', 'clear');
        return res.success;
    }

    /**
     * 获取所有原生持久化键名列表
     */
    public async keys(): Promise<string[]> {
        const res = await this.client.invoke<{ keys: string[] }>('storage', 'keys');
        return res.keys;
    }

    // ==================== 2. WebView 实例 LocalStorage 深度读写 ====================

    /**
     * 读取指定 WebView 实例 (或当前前台活跃 WebView) 的完整 LocalStorage 键值字典
     * @param options 可选指定 target pageId
     */
    public async getLocalStorage(options?: { pageId?: string }): Promise<Record<string, string>> {
        const res = await this.client.invoke<{ data: Record<string, string>; count: number }>('storage', 'getLocalStorage', {
            pageId: options?.pageId
        });
        return res.data || {};
    }

    /**
     * 向指定 WebView 实例批量写入 LocalStorage 键值对
     * @param data 要写入的键值字典
     * @param options 可选指定 target pageId
     */
    public async setLocalStorage(data: Record<string, string>, options?: { pageId?: string }): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean }>('storage', 'setLocalStorage', {
            data,
            pageId: options?.pageId
        });
        return res.success;
    }

    /**
     * 清空指定 WebView 实例的 LocalStorage
     * @param options 可选指定 target pageId
     */
    public async clearLocalStorage(options?: { pageId?: string }): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean }>('storage', 'clearLocalStorage', {
            pageId: options?.pageId
        });
        return res.success;
    }

    // ==================== 3. WebView 实例 SessionStorage 读写 ====================

    /**
     * 读取指定 WebView 实例的 SessionStorage
     */
    public async getSessionStorage(options?: { pageId?: string }): Promise<Record<string, string>> {
        const res = await this.client.invoke<{ data: Record<string, string> }>('storage', 'getSessionStorage', {
            pageId: options?.pageId
        });
        return res.data || {};
    }

    /**
     * 写入 SessionStorage
     */
    public async setSessionStorage(data: Record<string, string>, options?: { pageId?: string }): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean }>('storage', 'setSessionStorage', {
            data,
            pageId: options?.pageId
        });
        return res.success;
    }

    /**
     * 清空 SessionStorage
     */
    public async clearSessionStorage(options?: { pageId?: string }): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean }>('storage', 'clearSessionStorage', {
            pageId: options?.pageId
        });
        return res.success;
    }

    // ==================== 4. 一键导出全量存储 (Cookies含HttpOnly + LocalStorage) ====================

    /**
     * 一键导出目标页面的全部存储数据（包含所有带 HttpOnly 的 Cookie、LocalStorage、SessionStorage）
     */
    public async dumpStorage(options?: { pageId?: string }): Promise<StorageDump> {
        return this.client.invoke<StorageDump>('storage', 'dumpStorage', {
            pageId: options?.pageId
        });
    }
}
