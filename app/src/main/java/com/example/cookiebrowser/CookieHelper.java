package com.example.cookiebrowser;

import android.net.Uri;
import android.text.TextUtils;
import android.webkit.CookieManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CookieHelper {

    public static class CookieItem {
        public String name;
        public String value;
        public String domain;
        public String path = "/";
        public boolean secure = false;
        public boolean httpOnly = false;

        public CookieItem(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public String toCookieString() {
            StringBuilder sb = new StringBuilder();
            sb.append(name).append("=").append(value);
            if (!TextUtils.isEmpty(domain)) {
                sb.append("; Domain=").append(domain);
            }
            if (!TextUtils.isEmpty(path)) {
                sb.append("; Path=").append(path);
            }
            if (secure) {
                sb.append("; Secure");
            }
            if (httpOnly) {
                sb.append("; HttpOnly");
            }
            return sb.toString();
        }
    }

    /**
     * 将当前 URL 的 Cookie 格式化为 a=b;b=c 字符串
     */
    public static String exportCookiesAsStandardString(String url) {
        return exportCookiesAsStandardString(null, url);
    }

    /**
     * 支持传入指定 Profile 的 CookieManager 进行隔离读取
     */
    public static String exportCookiesAsStandardString(CookieManager customManager, String url) {
        if (TextUtils.isEmpty(url)) {
            return "";
        }
        CookieManager cookieManager = customManager != null ? customManager : CookieManager.getInstance();
        String rawCookies = cookieManager.getCookie(url);
        if (TextUtils.isEmpty(rawCookies)) {
            return "";
        }

        // CookieManager.getCookie 返回的是 name1=value1; name2=value2 格式
        // 按照用户要求组装为标准 a=b;b=c 模式
        String[] pairs = rawCookies.split(";");
        StringBuilder sb = new StringBuilder();
        for (String pair : pairs) {
            String trimmed = pair.trim();
            if (!trimmed.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append("; ");
                }
                sb.append(trimmed);
            }
        }
        return sb.toString();
    }

    /**
     * 将当前 URL 的 Cookie 导出为 JSON 数组字符串（EditThisCookie 格式）
     */
    public static String exportCookiesAsJson(String url) {
        return exportCookiesAsJson(null, url);
    }

    /**
     * 支持传入指定 Profile 的 CookieManager 进行隔离读取
     */
    public static String exportCookiesAsJson(CookieManager customManager, String url) {
        if (TextUtils.isEmpty(url)) {
            return "[]";
        }
        CookieManager cookieManager = customManager != null ? customManager : CookieManager.getInstance();
        String rawCookies = cookieManager.getCookie(url);
        if (TextUtils.isEmpty(rawCookies)) {
            return "[]";
        }

        String host = "";
        try {
            Uri uri = Uri.parse(url);
            host = uri.getHost();
        } catch (Exception ignored) {
        }

        JSONArray array = new JSONArray();
        String[] pairs = rawCookies.split(";");
        for (String pair : pairs) {
            String trimmed = pair.trim();
            if (trimmed.isEmpty()) continue;
            int idx = trimmed.indexOf('=');
            if (idx > 0) {
                String name = trimmed.substring(0, idx).trim();
                String value = trimmed.substring(idx + 1).trim();
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("name", name);
                    obj.put("value", value);
                    if (!TextUtils.isEmpty(host)) {
                        obj.put("domain", host);
                    }
                    obj.put("path", "/");
                    array.put(obj);
                } catch (Exception ignored) {
                }
            }
        }
        return array.toString();
    }

    /**
     * 解析多类型输入的 Cookie 并导入到 CookieManager
     * 支持类型：
     * 1. 标准分号分隔: a=b; c=d
     * 2. 多行 key=value 格式
     * 3. JSON 数组 (EditThisCookie/Cookie-Editor 导出格式)
     * 4. JSON 对象 (键值对字典)
     * 5. Set-Cookie 头格式
     * 6. Netscape / cURL cookie file 格式
     * 7. URL 参数格式: a=b&c=d
     *
     * @return 成功导入的 Cookie 数量
     */
    public static int importCookies(String currentUrl, String input) {
        return importCookies(null, currentUrl, input);
    }

    /**
     * 支持传入指定 Profile 的 CookieManager 进行隔离写入
     */
    public static int importCookies(CookieManager customManager, String currentUrl, String input) {
        if (TextUtils.isEmpty(currentUrl) || TextUtils.isEmpty(input)) {
            return 0;
        }

        List<CookieItem> cookieList = parseCookiesFromInput(input, currentUrl);
        if (cookieList.isEmpty()) {
            return 0;
        }

        CookieManager cookieManager = customManager != null ? customManager : CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);

        String host = "";
        try {
            Uri uri = Uri.parse(currentUrl);
            host = uri.getHost();
        } catch (Exception ignored) {
        }

        int successCount = 0;
        for (CookieItem item : cookieList) {
            if (TextUtils.isEmpty(item.name)) continue;

            if (TextUtils.isEmpty(item.domain) && !TextUtils.isEmpty(host)) {
                item.domain = host;
            }

            String cookieHeader = item.toCookieString();
            
            // 写入当前完整 URL
            cookieManager.setCookie(currentUrl, cookieHeader);

            // 如果有 domain，也写入对应 domain 的 base url
            if (!TextUtils.isEmpty(item.domain)) {
                String domain = item.domain.startsWith(".") ? item.domain.substring(1) : item.domain;
                cookieManager.setCookie("https://" + domain + "/", cookieHeader);
                cookieManager.setCookie("http://" + domain + "/", cookieHeader);
            }
            successCount++;
        }

        cookieManager.flush();
        return successCount;
    }

    /**
     * 解析任意格式的 Cookie 输入文本
     */
    public static List<CookieItem> parseCookiesFromInput(String rawInput, String fallbackUrl) {
        List<CookieItem> result = new ArrayList<>();
        if (rawInput == null) return result;
        String text = rawInput.trim();
        if (text.isEmpty()) return result;

        // 尝试解析 JSON 格式
        if (text.startsWith("[") && text.endsWith("]")) {
            try {
                JSONArray array = new JSONArray(text);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    String name = obj.optString("name", "");
                    String value = obj.optString("value", "");
                    if (!TextUtils.isEmpty(name)) {
                        CookieItem item = new CookieItem(name, value);
                        if (obj.has("domain")) item.domain = obj.optString("domain");
                        if (obj.has("path")) item.path = obj.optString("path", "/");
                        if (obj.has("secure")) item.secure = obj.optBoolean("secure", false);
                        if (obj.has("httpOnly")) item.httpOnly = obj.optBoolean("httpOnly", false);
                        result.add(item);
                    }
                }
                if (!result.isEmpty()) return result;
            } catch (Exception ignored) {
            }
        }

        if (text.startsWith("{") && text.endsWith("}")) {
            try {
                JSONObject obj = new JSONObject(text);
                Iterator<String> keys = obj.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    Object val = obj.get(key);
                    result.add(new CookieItem(key, String.valueOf(val)));
                }
                if (!result.isEmpty()) return result;
            } catch (Exception ignored) {
            }
        }

        // 尝试 Netscape / cURL 格式 (通常包含多行制表符分隔)
        if (text.contains("\t")) {
            String[] lines = text.split("\r?\n");
            boolean foundNetscape = false;
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\t");
                if (parts.length >= 7) {
                    foundNetscape = true;
                    CookieItem item = new CookieItem(parts[5].trim(), parts[6].trim());
                    item.domain = parts[0].trim();
                    item.path = parts[2].trim();
                    item.secure = "TRUE".equalsIgnoreCase(parts[3].trim());
                    result.add(item);
                }
            }
            if (foundNetscape && !result.isEmpty()) {
                return result;
            }
        }

        // 处理 Set-Cookie 头格式 (Set-Cookie: name=val; Domain=...; Path=...)
        if (text.toLowerCase().contains("set-cookie:") || text.toLowerCase().contains("cookie:")) {
            String[] lines = text.split("\r?\n");
            for (String line : lines) {
                line = line.trim();
                if (line.toLowerCase().startsWith("set-cookie:")) {
                    line = line.substring("set-cookie:".length()).trim();
                } else if (line.toLowerCase().startsWith("cookie:")) {
                    line = line.substring("cookie:".length()).trim();
                }
                parseKeyValues(line, result);
            }
            if (!result.isEmpty()) return result;
        }

        // 按行或分号拆分通用键值对
        // 允许同时有换行或分号混合
        String[] lines = text.split("\r?\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            parseKeyValues(line, result);
        }

        return result;
    }

    private static void parseKeyValues(String input, List<CookieItem> list) {
        if (TextUtils.isEmpty(input)) return;

        // 如果包含分号，优先按分号拆分
        if (input.contains(";")) {
            String[] tokens = input.split(";");
            for (String token : tokens) {
                addSingleKeyValue(token, list);
            }
        } else if (input.contains("&") && !input.contains(" ")) {
            // 如果像 url query 参数: a=1&b=2
            String[] tokens = input.split("&");
            for (String token : tokens) {
                addSingleKeyValue(token, list);
            }
        } else {
            addSingleKeyValue(input, list);
        }
    }

    private static void addSingleKeyValue(String token, List<CookieItem> list) {
        String trimmed = token.trim();
        if (trimmed.isEmpty()) return;

        // 忽略标准属性如 Domain=, Path=, Expires=, Max-Age=, SameSite= 等单独作为 cookie 名称的情况
        int idx = trimmed.indexOf('=');
        if (idx > 0) {
            String name = trimmed.substring(0, idx).trim();
            String value = trimmed.substring(idx + 1).trim();

            String lower = name.toLowerCase();
            if (lower.equals("domain") || lower.equals("path") || lower.equals("expires")
                    || lower.equals("max-age") || lower.equals("samesite") || lower.equals("priority")) {
                // 如果是属性，看能不能附加到上一个 cookie
                if (!list.isEmpty()) {
                    CookieItem last = list.get(list.size() - 1);
                    if (lower.equals("domain")) last.domain = value;
                    if (lower.equals("path")) last.path = value;
                }
                return;
            }

            list.add(new CookieItem(name, value));
        } else if (trimmed.equalsIgnoreCase("secure") || trimmed.equalsIgnoreCase("httponly")) {
            if (!list.isEmpty()) {
                CookieItem last = list.get(list.size() - 1);
                if (trimmed.equalsIgnoreCase("secure")) last.secure = true;
                if (trimmed.equalsIgnoreCase("httponly")) last.httpOnly = true;
            }
        }
    }
}
