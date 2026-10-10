/**
 * @file camera.ts
 * @description 移动端相机拍照、相册选图与二维码/条码扫描模块
 */

import { MobileElectronClient } from '../client';
import { CameraPhotoOptions, CameraPhotoResult, BarcodeScanResult } from '../types';

export class CameraModule {
    private client: MobileElectronClient;

    constructor(client: MobileElectronClient) {
        this.client = client;
    }

    /**
     * 调起原生摄像头拍照
     * @param options 拍照参数（质量、最大限制宽度）
     */
    public async takePhoto(options: CameraPhotoOptions = {}): Promise<CameraPhotoResult> {
        return this.client.invoke<CameraPhotoResult>('camera', 'takePhoto', options);
    }

    /**
     * 调起原生系统相册选取图片
     * @param options 选图参数
     */
    public async pickImage(options: CameraPhotoOptions = {}): Promise<CameraPhotoResult> {
        return this.client.invoke<CameraPhotoResult>('camera', 'pickImage', options);
    }

    /**
     * 调起原生摄像头扫描二维码/条码 (QR Code / EAN-13 / Code-128 等)
     * @returns 扫码识别出的文本及编码格式
     */
    public async scanBarcode(): Promise<BarcodeScanResult> {
        return this.client.invoke<BarcodeScanResult>('camera', 'scanBarcode', {});
    }
}
