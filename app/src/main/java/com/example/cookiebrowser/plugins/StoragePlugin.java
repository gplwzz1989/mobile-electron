package com.example.cookiebrowser.plugins;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.cookiebrowser.bridge.BridgeCallback;
import com.example.cookiebrowser.bridge.IBridgePlugin;
import com.example.cookiebrowser.security.DomainWhitelistManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Map;
import java.util.Set;

/**
 * 原生持久化键值存储插件 (Native Persistent Key-Value Storage)
 */
public class StoragePlugin implements IBridgePlugin {

    private static final String PREF_NAME = "MobileElectron_Storage";
    private final SharedPreferences prefs;

    public StoragePlugin(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    @Override
    public String getModuleName() {
        return "storage";
    }

    @Override
    public int getRequiredTier(String action) {
        return DomainWhitelistManager.TIER_1_BUSINESS;
    }

    @Override
    public void handleAction(String callingUrl, String action, JSONObject params, BridgeCallback callback) throws Exception {
        switch (action) {
            case "set": {
                String key = params.optString("key", "");
                String value = params.optString("value", "");
                if (key.isEmpty()) {
                    callback.error(400, "Missing key parameter");
                    return;
                }
                prefs.edit().putString(key, value).apply();
                JSONObject res = new JSONObject();
                res.put("success", true);
                callback.success(res);
                break;
            }

            case "get": {
                String key = params.optString("key", "");
                if (key.isEmpty()) {
                    callback.error(400, "Missing key parameter");
                    return;
                }
                String val = prefs.getString(key, null);
                JSONObject res = new JSONObject();
                res.put("value", val != null ? val : JSONObject.NULL);
                res.put("exists", val != null);
                callback.success(res);
                break;
            }

            case "remove": {
                String key = params.optString("key", "");
                prefs.edit().remove(key).apply();
                JSONObject res = new JSONObject();
                res.put("success", true);
                callback.success(res);
                break;
            }

            case "clear": {
                prefs.edit().clear().apply();
                JSONObject res = new JSONObject();
                res.put("success", true);
                callback.success(res);
                break;
            }

            case "keys": {
                Map<String, ?> all = prefs.getAll();
                JSONArray arr = new JSONArray();
                for (String k : all.keySet()) {
                    arr.put(k);
                }
                JSONObject res = new JSONObject();
                res.put("keys", arr);
                callback.success(res);
                break;
            }

            default:
                callback.error(404, "Unknown action '" + action + "' in module 'storage'");
                break;
        }
    }
}
