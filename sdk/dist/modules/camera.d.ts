/**
 * @file camera.ts
 * @description 移动端相机拍照、相册选图与二维码/条码扫描模块
 */
import { MobileElectronClient } from '../client';
import { CameraPhotoOptions, CameraPhotoResult, BarcodeScanResult } from '../types';
export declare class CameraModule {
    private client;
    constructor(client: MobileElectronClient);
    /**
     * 调起原生摄像头拍照
     * @param options 拍照参数（质量、最大限制宽度）
     */
    takePhoto(options?: CameraPhotoOptions): Promise<CameraPhotoResult>;
    /**
     * 调起原生系统相册选取图片
     * @param options 选图参数
     */
    pickImage(options?: CameraPhotoOptions): Promise<CameraPhotoResult>;
    /**
     * 调起原生摄像头扫描二维码/条码 (QR Code / EAN-13 / Code-128 等)
     * @returns 扫码识别出的文本及编码格式
     */
    scanBarcode(): Promise<BarcodeScanResult>;
}
