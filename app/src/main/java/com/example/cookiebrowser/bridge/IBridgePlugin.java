package com.example.cookiebrowser.bridge;

import org.json.JSONObject;

/**
 * 原生插件标准接口 (类似 Capacitor / Electron 模块化插件体系)
 */
public interface IBridgePlugin {

    /**
     * 模块名称，例如 "cookie", "page", "network", "device"
     */
    String getModuleName();

    /**
     * 获取指定动作所需的安全等级 (DomainWhitelistManager.TIER_0/1/2)
     */
    int getRequiredTier(String action);

    /**
     * 执行具体动作
     *
     * @param callingUrl 发起调用的真实页面 URL
     * @param action     动作名称
     * @param params     入参 JSON 对象
     * @param callback   异步完成回调
     * @throws Exception 异常捕获
     */
    void handleAction(String callingUrl, String action, JSONObject params, BridgeCallback callback) throws Exception;
}
