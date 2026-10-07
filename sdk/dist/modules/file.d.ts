/**
 * @file file.ts
 * @description 本地安全沙箱文件系统读写模块 (Local File System Module)
 */
import { MobileElectronClient } from '../client';
import { FileWriteOptions, FileReadOptions, FileDownloadOptions, FileInfo, DirectoryType } from '../types';
export declare class FileModule {
    private readonly client;
    constructor(client: MobileElectronClient);
    /**
     * 获取应用安全沙箱的基础路径（documents, cache, temp）
     */
    getPaths(): Promise<{
        documents: string;
        cache: string;
        temp: string;
    }>;
    /**
     * 写入文件（支持 UTF-8 文本或 Base64 二进制数据）
     */
    write(options: FileWriteOptions): Promise<{
        success: boolean;
        bytesWritten: number;
        fullPath: string;
    }>;
    /**
     * 读取文件内容（支持返回文本字符串或 Base64）
     */
    read(options: FileReadOptions): Promise<{
        content: string;
        size: number;
        encoding: string;
    }>;
    /**
     * 快捷写入 UTF-8 文本文件
     */
    writeText(path: string, text: string, directory?: DirectoryType): Promise<string>;
    /**
     * 快捷读取 UTF-8 文本文件
     */
    readText(path: string, directory?: DirectoryType): Promise<string>;
    /**
     * 检查文件或目录是否存在
     */
    exists(path: string, directory?: DirectoryType): Promise<{
        exists: boolean;
        isFile: boolean;
        isDirectory: boolean;
    }>;
    /**
     * 删除文件或递归删除目录
     */
    delete(path: string, directory?: DirectoryType): Promise<boolean>;
    /**
     * 列出指定目录下的文件与子目录列表
     */
    list(path?: string, directory?: DirectoryType): Promise<FileInfo[]>;
    /**
     * 创建目录
     */
    mkdir(path: string, directory?: DirectoryType): Promise<boolean>;
    /**
     * 原生后台下载网络文件并保存至指定沙箱路径
     */
    download(options: FileDownloadOptions): Promise<{
        success: boolean;
        size: number;
        fullPath: string;
    }>;
}
