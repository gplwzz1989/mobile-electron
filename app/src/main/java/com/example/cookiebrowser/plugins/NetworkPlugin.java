package com.example.cookiebrowser.plugins;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import com.example.cookiebrowser.bridge.BridgeCallback;
import com.example.cookiebrowser.bridge.IBridgePlugin;
import com.example.cookiebrowser.bridge.JsBridgeEngine;
import com.example.cookiebrowser.security.DomainWhitelistManager;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 原生网络插件
 * 包含：网络穿透请求 (绕过 CORS)、网络连通性状态感知 (getStatus) 与全局网络变更监听 (onNetworkChange)
 */
public class NetworkPlugin implements IBridgePlugin {

    private static final String TAG = "NetworkPlugin";

    private final Context context;
    private JsBridgeEngine bridgeEngine;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    public NetworkPlugin() {
        this(null);
    }

    public NetworkPlugin(Context context) {
        this.context = context != null ? context.getApplicationContext() : null;
        if (this.context != null) {
            initNetworkListener();
        }
    }

    public void setBridgeEngine(JsBridgeEngine engine) {
        this.bridgeEngine = engine;
    }

    private void initNetworkListener() {
        try {
            connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager == null) return;

            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    dispatchNetworkChangeEvent();
                }

                @Override
                public void onLost(Network network) {
                    dispatchNetworkChangeEvent();
                }

                @Override
                public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
                    dispatchNetworkChangeEvent();
                }
            };

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connectivityManager.registerDefaultNetworkCallback(networkCallback);
            } else {
                NetworkRequest request = new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build();
                connectivityManager.registerNetworkCallback(request, networkCallback);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to register network callback: " + e.getMessage());
        }
    }

    private void dispatchNetworkChangeEvent() {
        if (bridgeEngine == null) return;
        try {
            JSONObject status = getCurrentNetworkStatus();
            bridgeEngine.sendEvent("network.onNetworkChange", status);
        } catch (Exception e) {
            Log.e(TAG, "Failed to dispatch network change event: " + e.getMessage());
        }
    }

    public JSONObject getCurrentNetworkStatus() {
        JSONObject json = new JSONObject();
        try {
            if (connectivityManager == null && context != null) {
                connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            }

            if (connectivityManager == null) {
                json.put("isConnected", false);
                json.put("type", "none");
                json.put("isMetered", false);
                return json;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network activeNet = connectivityManager.getActiveNetwork();
                if (activeNet == null) {
                    json.put("isConnected", false);
                    json.put("type", "none");
                    json.put("isMetered", false);
                    return json;
                }

                NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(activeNet);
                if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    json.put("isConnected", false);
                    json.put("type", "none");
                    json.put("isMetered", false);
                    return json;
                }

                json.put("isConnected", true);
                json.put("isMetered", !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED));

                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    json.put("type", "wifi");
                } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    json.put("type", "cellular");
                } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    json.put("type", "ethernet");
                } else {
                    json.put("type", "other");
                }
            } else {
                android.net.NetworkInfo info = connectivityManager.getActiveNetworkInfo();
                boolean connected = info != null && info.isConnected();
                json.put("isConnected", connected);
                json.put("isMetered", connectivityManager.isActiveNetworkMetered());
                if (!connected) {
                    json.put("type", "none");
                } else if (info.getType() == ConnectivityManager.TYPE_WIFI) {
                    json.put("type", "wifi");
                } else if (info.getType() == ConnectivityManager.TYPE_MOBILE) {
                    json.put("type", "cellular");
                } else {
                    json.put("type", "other");
                }
            }
        } catch (Exception e) {
            try {
                json.put("isConnected", false);
                json.put("type", "none");
                json.put("isMetered", false);
            } catch (Exception ignored) {}
        }
        return json;
    }

    @Override
    public String getModuleName() {
        return "network";
    }

    @Override
    public int getRequiredTier(String action) {
        if ("getStatus".equals(action)) {
            return DomainWhitelistManager.TIER_1_BUSINESS;
        }
        return DomainWhitelistManager.TIER_2_CORE;
    }

    @Override
    public void handleAction(String callingUrl, String action, JSONObject params, BridgeCallback callback) throws Exception {
        if ("getStatus".equals(action)) {
            callback.success(getCurrentNetworkStatus());
            return;
        }

        if ("fetch".equals(action)) {
            String urlStr = params.optString("url");
            if (TextUtils.isEmpty(urlStr)) {
                callback.error(400, "URL cannot be empty");
                return;
            }

            String method = params.optString("method", "GET").toUpperCase();
            JSONObject headers = params.optJSONObject("headers");
            String body = params.optString("body", null);
            int timeoutMs = params.optInt("timeoutMs", 15000);

            // 异步后台发起原生 HTTP 请求
            new Thread(() -> {
                HttpURLConnection conn = null;
                try {
                    URL url = new URL(urlStr);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod(method);
                    conn.setConnectTimeout(timeoutMs);
                    conn.setReadTimeout(timeoutMs);
                    conn.setDoInput(true);

                    // 设置自定义请求头
                    if (headers != null) {
                        Iterator<String> keys = headers.keys();
                        while (keys.hasNext()) {
                            String k = keys.next();
                            conn.setRequestProperty(k, headers.optString(k));
                        }
                    }

                    // 写入 Body
                    if (!TextUtils.isEmpty(body) && ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method))) {
                        conn.setDoOutput(true);
                        try (OutputStream os = conn.getOutputStream()) {
                            os.write(body.getBytes("UTF-8"));
                            os.flush();
                        }
                    }

                    int responseCode = conn.getResponseCode();
                    InputStream is = responseCode >= 200 && responseCode < 400 ? conn.getInputStream() : conn.getErrorStream();

                    StringBuilder sb = new StringBuilder();
                    if (is != null) {
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                sb.append(line).append("\n");
                            }
                        }
                    }

                    JSONObject resHeaders = new JSONObject();
                    for (Map.Entry<String, List<String>> entry : conn.getHeaderFields().entrySet()) {
                        if (entry.getKey() != null && entry.getValue() != null && !entry.getValue().isEmpty()) {
                            resHeaders.put(entry.getKey(), entry.getValue().get(0));
                        }
                    }

                    JSONObject result = new JSONObject();
                    result.put("status", responseCode);
                    result.put("headers", resHeaders);
                    result.put("body", sb.toString());

                    callback.success(result);

                } catch (Exception e) {
                    callback.error(500, "Native HTTP request failed: " + e.getMessage());
                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                }
            }).start();

        } else {
            callback.error(404, "Unknown action '" + action + "' in module 'network'");
        }
    }
}
