package com.example.cookiebrowser.plugins;

import com.example.cookiebrowser.bridge.BridgeCallback;
import com.example.cookiebrowser.bridge.IBridgePlugin;
import com.example.cookiebrowser.core.IpcHub;
import com.example.cookiebrowser.security.DomainWhitelistManager;

import org.json.JSONObject;

/**
 * 原生全双工 IPC 消息跨窗口通信插件 (electron.ipc)
 */
public class IpcPlugin implements IBridgePlugin {

    @Override
    public String getModuleName() {
        return "ipc";
    }

    @Override
    public int getRequiredTier(String action) {
        return DomainWhitelistManager.TIER_0_PUBLIC;
    }

    @Override
    public void handleAction(String callingUrl, String action, JSONObject params, BridgeCallback callback) throws Exception {
        String channel = params.optString("channel", "");
        if (channel.isEmpty()) {
            callback.error(400, "Missing required parameter 'channel'");
            return;
        }

        String senderId = params.optString("senderId", "main");
        Object payload = params.has("payload") ? params.get("payload") : null;

        switch (action) {
            case "send":
            case "broadcast": {
                int count = IpcHub.getInstance().broadcast(senderId, channel, payload);
                JSONObject res = new JSONObject();
                res.put("success", true);
                res.put("recipientCount", count);
                callback.success(res);
                break;
            }

            case "sendTo": {
                String targetId = params.optString("targetId", "");
                if (targetId.isEmpty()) {
                    callback.error(400, "Missing required parameter 'targetId'");
                    return;
                }
                boolean ok = IpcHub.getInstance().sendTo(senderId, targetId, channel, payload);
                JSONObject res = new JSONObject();
                res.put("success", ok);
                res.put("targetId", targetId);
                callback.success(res);
                break;
            }

            default:
                callback.error(404, "Unknown action '" + action + "' in module 'ipc'");
                break;
        }
    }
}
