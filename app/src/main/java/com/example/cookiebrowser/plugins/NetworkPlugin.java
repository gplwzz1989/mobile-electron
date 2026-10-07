package com.example.cookiebrowser.plugins;

import android.os.AsyncTask;
import android.text.TextUtils;

import com.example.cookiebrowser.bridge.BridgeCallback;
import com.example.cookiebrowser.bridge.IBridgePlugin;
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
 * 原生网络穿透插件 (彻底绕过浏览器前端 CORS 跨域限制)
 */
public class NetworkPlugin implements IBridgePlugin {

    @Override
    public String getModuleName() {
        return "network";
    }

    @Override
    public int getRequiredTier(String action) {
        return DomainWhitelistManager.TIER_2_CORE;
    }

    @Override
    public void handleAction(String callingUrl, String action, JSONObject params, BridgeCallback callback) throws Exception {
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
