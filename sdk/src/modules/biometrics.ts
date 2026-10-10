/**
 * @file biometrics.ts
 * @description 生物特征身份识别模块 (Face ID / Touch ID / 指纹认证)
 */

import { MobileElectronClient } from '../client';
import { BiometricsAuthOptions, BiometricsAuthResult, BiometricsAvailability } from '../types';

export class BiometricsModule {
    constructor(private readonly client: MobileElectronClient) {}

    /**
     * 检查当前设备生物识别可用状态 (是否配备硬件、是否已录入特征、支持的类型等)
     */
    public async isAvailable(): Promise<BiometricsAvailability> {
        return this.client.invoke<BiometricsAvailability>('biometrics', 'isAvailable');
    }

    /**
     * 调起系统原生生物特征认证弹窗 (指纹/面容/凭据验证)
     * @param options 弹窗标题、副标题、提示说明与取消按钮文案
     */
    public async authenticate(options?: BiometricsAuthOptions): Promise<BiometricsAuthResult> {
        return this.client.invoke<BiometricsAuthResult>('biometrics', 'authenticate', options || {});
    }
}
