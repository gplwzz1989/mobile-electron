/**
 * @file storage.ts
 * @description 原生持久化键值存储模块 (突破 Web 5MB 限制，跨页面共享)
 */

import { MobileElectronClient } from '../client';

export class StorageModule {
    constructor(private readonly client: MobileElectronClient) {}

    /**
     * 保存键值数据
     */
    public async set(key: string, value: string): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean }>('storage', 'set', { key, value });
        return res.success;
    }

    /**
     * 读取键值数据（未找到返回 null）
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
     * 删除指定键
     */
    public async remove(key: string): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean }>('storage', 'remove', { key });
        return res.success;
    }

    /**
     * 清空全部持久化键值
     */
    public async clear(): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean }>('storage', 'clear');
        return res.success;
    }

    /**
     * 获取所有键名列表
     */
    public async keys(): Promise<string[]> {
        const res = await this.client.invoke<{ keys: string[] }>('storage', 'keys');
        return res.keys;
    }
}
