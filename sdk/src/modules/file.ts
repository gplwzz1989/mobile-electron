/**
 * @file file.ts
 * @description 本地安全沙箱文件系统读写模块 (Local File System Module)
 */

import { MobileElectronClient } from '../client';
import { FileWriteOptions, FileReadOptions, FileDownloadOptions, FileInfo, DirectoryType } from '../types';

export class FileModule {
    constructor(private readonly client: MobileElectronClient) {}

    /**
     * 获取应用安全沙箱的基础路径（documents, cache, temp）
     */
    public async getPaths(): Promise<{ documents: string; cache: string; temp: string }> {
        return this.client.invoke<{ documents: string; cache: string; temp: string }>('file', 'getPaths');
    }

    /**
     * 写入文件（支持 UTF-8 文本或 Base64 二进制数据）
     */
    public async write(options: FileWriteOptions): Promise<{ success: boolean; bytesWritten: number; fullPath: string }> {
        return this.client.invoke<{ success: boolean; bytesWritten: number; fullPath: string }>('file', 'write', options);
    }

    /**
     * 读取文件内容（支持返回文本字符串或 Base64）
     */
    public async read(options: FileReadOptions): Promise<{ content: string; size: number; encoding: string }> {
        return this.client.invoke<{ content: string; size: number; encoding: string }>('file', 'read', options);
    }

    /**
     * 快捷写入 UTF-8 文本文件
     */
    public async writeText(path: string, text: string, directory: DirectoryType = 'documents'): Promise<string> {
        const res = await this.write({ path, content: text, encoding: 'utf8', directory });
        return res.fullPath;
    }

    /**
     * 快捷读取 UTF-8 文本文件
     */
    public async readText(path: string, directory: DirectoryType = 'documents'): Promise<string> {
        const res = await this.read({ path, encoding: 'utf8', directory });
        return res.content;
    }

    /**
     * 检查文件或目录是否存在
     */
    public async exists(path: string, directory: DirectoryType = 'documents'): Promise<{ exists: boolean; isFile: boolean; isDirectory: boolean }> {
        return this.client.invoke<{ exists: boolean; isFile: boolean; isDirectory: boolean }>('file', 'exists', { path, directory });
    }

    /**
     * 删除文件或递归删除目录
     */
    public async delete(path: string, directory: DirectoryType = 'documents'): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean }>('file', 'delete', { path, directory });
        return res.success;
    }

    /**
     * 列出指定目录下的文件与子目录列表
     */
    public async list(path: string = '', directory: DirectoryType = 'documents'): Promise<FileInfo[]> {
        const res = await this.client.invoke<{ files: FileInfo[] }>('file', 'list', { path, directory });
        return res.files;
    }

    /**
     * 创建目录
     */
    public async mkdir(path: string, directory: DirectoryType = 'documents'): Promise<boolean> {
        const res = await this.client.invoke<{ success: boolean }>('file', 'mkdir', { path, directory });
        return res.success;
    }

    /**
     * 原生后台下载网络文件并保存至指定沙箱路径
     */
    public async download(options: FileDownloadOptions): Promise<{ success: boolean; size: number; fullPath: string }> {
        return this.client.invoke<{ success: boolean; size: number; fullPath: string }>('file', 'download', options);
    }
}
