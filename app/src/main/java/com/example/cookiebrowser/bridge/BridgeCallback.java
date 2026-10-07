package com.example.cookiebrowser.bridge;

import org.json.JSONObject;

/**
 * 原生插件调用完成回调接口
 */
public interface BridgeCallback {
    void success(JSONObject data);
    void error(int code, String message);
}
