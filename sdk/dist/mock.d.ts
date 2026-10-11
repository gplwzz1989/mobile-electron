/**
 * @file mock.ts
 * @description 浏览器环境开发调试降级 Mock 引擎
 */
export declare class MockEngine {
    static isMockEnabled: boolean;
    private static memoryStorage;
    private static memorySecureStorage;
    private static mockFloatingWindows;
    static mockInvoke<T = any>(module: string, action: string, params?: Record<string, any>): Promise<T>;
}
