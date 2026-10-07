/**
 * @file storage.ts
 * @description 综合存储控制模块 (原生持久化键值存储 + 任意 WebView 实例的 LocalStorage / SessionStorage 读写)
 */
import { MobileElectronClient } from '../client';
import { StorageDump } from '../types';
export declare class StorageModule {
    private readonly client;
    constructor(client: MobileElectronClient);
    /**
     * 保存键值数据至原生持久化层
     */
    set(key: string, value: string): Promise<boolean>;
    /**
     * 读取原生持久化数据（未找到返回 null）
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
     * 删除指定原生持久化键
     */
    remove(key: string): Promise<boolean>;
    /**
     * 清空全部原生持久化数据
     */
    clear(): Promise<boolean>;
    /**
     * 获取所有原生持久化键名列表
     */
    keys(): Promise<string[]>;
    /**
     * 读取指定 WebView 实例 (或当前前台活跃 WebView) 的完整 LocalStorage 键值字典
     * @param options 可选指定 target pageId
     */
    getLocalStorage(options?: {
        pageId?: string;
    }): Promise<Record<string, string>>;
    /**
     * 向指定 WebView 实例批量写入 LocalStorage 键值对
     * @param data 要写入的键值字典
     * @param options 可选指定 target pageId
     */
    setLocalStorage(data: Record<string, string>, options?: {
        pageId?: string;
    }): Promise<boolean>;
    /**
     * 清空指定 WebView 实例的 LocalStorage
     * @param options 可选指定 target pageId
     */
    clearLocalStorage(options?: {
        pageId?: string;
    }): Promise<boolean>;
    /**
     * 读取指定 WebView 实例的 SessionStorage
     */
    getSessionStorage(options?: {
        pageId?: string;
    }): Promise<Record<string, string>>;
    /**
     * 写入 SessionStorage
     */
    setSessionStorage(data: Record<string, string>, options?: {
        pageId?: string;
    }): Promise<boolean>;
    /**
     * 清空 SessionStorage
     */
    clearSessionStorage(options?: {
        pageId?: string;
    }): Promise<boolean>;
    /**
     * 一键导出目标页面的全部存储数据（包含所有带 HttpOnly 的 Cookie、LocalStorage、SessionStorage）
     */
    dumpStorage(options?: {
        pageId?: string;
    }): Promise<StorageDump>;
}
