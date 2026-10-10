/**
 * @file secureStorage.ts
 * @description 硬件级安全加密存储模块 (AndroidKeyStore / iOS Keychain Services)
 */

import { MobileElectronClient } from '../client';
import { SecureStorageResult, SecureStorageGetResult, SecureStorageKeysResult } from '../types';

export class SecureStorageModule {
    private client: MobileElectronClient;

    constructor(client: MobileElectronClient) {
        this.client = client;
    }

    /**
     * 安全加密保存键值数据
     * @param key 键名
     * @param value 敏感数据字符串 (如 Token, PrivateKey, 密码凭证)
     */
    public async setItem(key: string, value: string): Promise<boolean> {
        const res = await this.client.invoke<SecureStorageResult>('secureStorage', 'setItem', { key, value });
        return !!res.success;
    }

    /**
     * 安全解密获取键值数据
     * @param key 键名
     * @returns 解密后的明文字符串，若不存在返回 null
     */
    public async getItem(key: string): Promise<string | null> {
        const res = await this.client.invoke<SecureStorageGetResult>('secureStorage', 'getItem', { key });
        return res.value ?? null;
    }

    /**
     * 删除指定安全键值数据
     * @param key 键名
     */
    public async removeItem(key: string): Promise<boolean> {
        const res = await this.client.invoke<SecureStorageResult>('secureStorage', 'removeItem', { key });
        return !!res.success;
    }

    /**
     * 清空当前应用所有的安全加密数据
     */
    public async clear(): Promise<boolean> {
        const res = await this.client.invoke<SecureStorageResult>('secureStorage', 'clear', {});
        return !!res.success;
    }

    /**
     * 获取所有受保护的安全存储键名列表
     */
    public async getAllKeys(): Promise<string[]> {
        const res = await this.client.invoke<SecureStorageKeysResult>('secureStorage', 'getAllKeys', {});
        return res.keys || [];
    }
}
