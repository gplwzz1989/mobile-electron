/**
 * @file storage.ts
 * @description 原生持久化键值存储模块 (突破 Web 5MB 限制，跨页面共享)
 */
import { MobileElectronClient } from '../client';
export declare class StorageModule {
    private readonly client;
    constructor(client: MobileElectronClient);
    /**
     * 保存键值数据
     */
    set(key: string, value: string): Promise<boolean>;
    /**
     * 读取键值数据（未找到返回 null）
     */
    get(key: string): Promise<string | null>;
    /**
     * 快捷保存 JSON 对象
     */
    setObject<T = any>(key: string, object: T): Promise<boolean>;
    /**
     * 快捷读取 JSON 对象
     */
    getObject<T = any>(key: string): Promise<T | null>;
    /**
     * 删除指定键
     */
    remove(key: string): Promise<boolean>;
    /**
     * 清空全部持久化键值
     */
    clear(): Promise<boolean>;
    /**
     * 获取所有键名列表
     */
    keys(): Promise<string[]>;
}
