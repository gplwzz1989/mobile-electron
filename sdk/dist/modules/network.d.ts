/**
 * @file network.ts
 * @description 原生网络增强模块（绕过 CORS 跨域请求与网络抓包监控）
 */
import { MobileElectronClient } from '../client';
import { NativeRequestOptions, NativeResponse, CapturedResponse, UnsubscribeFn, NetworkStatus } from '../types';
export declare class NetworkModule {
    private readonly client;
    constructor(client: MobileElectronClient);
    /**
     * 获取当前设备原生网络连接状态 (Wi-Fi / 蜂窝 / 离线)
     */
    getStatus(): Promise<NetworkStatus>;
    /**
     * 注册原生网络状态实时变更监听
     */
    onNetworkChange(listener: (status: NetworkStatus) => void): UnsubscribeFn;
    /**
     * 通过原生底层 HTTP 客户端发起请求，彻底绕过浏览器 CORS 跨域限制与请求头屏蔽
     */
    fetchNative(options: NativeRequestOptions): Promise<NativeResponse>;
    /**
     * 全局监听拦截到的网络响应（如用于捕获二维码轮询结果或特定业务 API）
     */
    onResponse(urlPattern: string, callback: (res: CapturedResponse) => void): UnsubscribeFn;
}
