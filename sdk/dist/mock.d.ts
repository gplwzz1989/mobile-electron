/**
 * @file mock.ts
 * @description 浏览器环境开发调试降级 Mock 引擎
 */
export declare class MockEngine {
    static isMockEnabled: boolean;
    static onMockEvent?: (eventName: string, data: any) => void;
    private static memoryStorage;
    private static memorySecureStorage;
    private static mockFloatingWindows;
    private static mockClipboardText;
    static mockInvoke<T = any>(module: string, action: string, params?: Record<string, any>): Promise<T>;
}
