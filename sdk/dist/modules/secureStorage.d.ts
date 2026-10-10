/**
 * @file secureStorage.ts
 * @description 硬件级安全加密存储模块 (AndroidKeyStore / iOS Keychain Services)
 */
import { MobileElectronClient } from '../client';
export declare class SecureStorageModule {
    private client;
    constructor(client: MobileElectronClient);
    /**
     * 安全加密保存键值数据
     * @param key 键名
     * @param value 敏感数据字符串 (如 Token, PrivateKey, 密码凭证)
     */
    setItem(key: string, value: string): Promise<boolean>;
    /**
     * 安全解密获取键值数据
     * @param key 键名
     * @returns 解密后的明文字符串，若不存在返回 null
     */
    getItem(key: string): Promise<string | null>;
    /**
     * 删除指定安全键值数据
     * @param key 键名
     */
    removeItem(key: string): Promise<boolean>;
    /**
     * 清空当前应用所有的安全加密数据
     */
    clear(): Promise<boolean>;
    /**
     * 获取所有受保护的安全存储键名列表
     */
    getAllKeys(): Promise<string[]>;
}
