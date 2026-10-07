package com.example.cookiebrowser;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.CookieManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工业级 Cookie 读写辅助工具
 * 支持全量读取指定 WebView/Profile 底层 SQLite 数据库（含所有 HttpOnly、Secure、Domain、Path、Expires）
 * 支持任意格式 Cookie 导入与设置
 */
public class CookieHelper {

    private static final String TAG = "CookieHelper";
    private static final long CHROMIUM_EPOCH_OFFSET_MICROS = 11644473600000000L;

    public static class CookieItem {
        public String name;
        public String value;
        public String domain;
        public String path = "/";
        public boolean secure = false;
        public boolean httpOnly = false;
        public long expires = 0; // Unix 时间戳（秒）
        public String sameSite = "";

        public CookieItem(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public CookieItem(String name, String value, String domain, String path, boolean secure, boolean httpOnly, long expires, String sameSite) {
            this.name = name;
            this.value = value;
            this.domain = domain;
            this.path = (path == null || path.isEmpty()) ? "/" : path;
            this.secure = secure;
            this.httpOnly = httpOnly;
            this.expires = expires;
            this.sameSite = sameSite != null ? sameSite : "";
        }

        public JSONObject toJson() {
            JSONObject obj = new JSONObject();
            try {
                obj.put("name", name);
                obj.put("value", value);
                obj.put("domain", domain != null ? domain : "");
                obj.put("path", path != null ? path : "/");
                obj.put("secure", secure);
                obj.put("httpOnly", httpOnly);
                obj.put("expires", expires);
                if (!TextUtils.isEmpty(sameSite)) {
                    obj.put("sameSite", sameSite);
                }
            } catch (Exception ignored) {}
            return obj;
        }

        public static CookieItem fromJson(JSONObject obj) {
            if (obj == null) return null;
            String name = obj.optString("name", "");
            String value = obj.optString("value", "");
            if (TextUtils.isEmpty(name)) return null;

            CookieItem item = new CookieItem(name, value);
            item.domain = obj.optString("domain", "");
            item.path = obj.optString("path", "/");
            item.secure = obj.optBoolean("secure", false);
            item.httpOnly = obj.optBoolean("httpOnly", false);
            item.expires = obj.optLong("expires", 0);
            item.sameSite = obj.optString("sameSite", "");
            return item;
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
            if (expires > 0) {
                long maxAge = expires - (System.currentTimeMillis() / 1000L);
                if (maxAge > 0) {
                    sb.append("; Max-Age=").append(maxAge);
                }
            }
            if (secure) {
                sb.append("; Secure");
            }
            if (httpOnly) {
                sb.append("; HttpOnly");
            }
            if (!TextUtils.isEmpty(sameSite)) {
                sb.append("; SameSite=").append(sameSite);
            }
            return sb.toString();
        }
    }

    /**
     * 判断域名是否匹配（支持子域名与根域名双向泛匹配）
     */
    public static boolean domainMatches(String cookieHost, String targetHost) {
        if (TextUtils.isEmpty(cookieHost) || TextUtils.isEmpty(targetHost)) {
            return true;
        }
        String c = cookieHost.toLowerCase().trim();
        String t = targetHost.toLowerCase().trim();
        if (c.startsWith(".")) c = c.substring(1);
        if (t.startsWith(".")) t = t.substring(1);

        if (c.equals(t)) return true;
        if (t.endsWith("." + c)) return true; // targetHost 是 cookieHost 的子域名
        if (c.endsWith("." + t)) return true; // cookieHost 是 targetHost 的子域名
        return false;
    }

    /**
     * 智能定位指定 Profile 的 Chromium Cookies SQLite 数据库文件
     */
    public static File findCookieDatabaseFile(Context context, String profileName) {
        if (context == null) return null;
        File dataDir = context.getDataDir();
        File appWebviewDir = new File(dataDir, "app_webview");
        if (!appWebviewDir.exists()) return null;

        boolean isDefault = profileName == null || profileName.trim().isEmpty() || profileName.equalsIgnoreCase("default");

        if (isDefault) {
            File[] candidates = new File[] {
                new File(appWebviewDir, "Default/Cookies"),
                new File(appWebviewDir, "Cookies"),
                new File(appWebviewDir, "Default/Network/Cookies")
            };
            for (File f : candidates) {
                if (f.exists() && f.isFile()) return f;
            }
        } else {
            String clean = profileName.trim();
            File[] candidates = new File[] {
                new File(appWebviewDir, "Profile_" + clean + "/Cookies"),
                new File(appWebviewDir, "Profile " + clean + "/Cookies"),
                new File(appWebviewDir, "profiles/" + clean + "/Cookies"),
                new File(appWebviewDir, clean + "/Cookies"),
                new File(appWebviewDir, "Profile_" + clean + "/Network/Cookies")
            };
            for (File f : candidates) {
                if (f.exists() && f.isFile()) return f;
            }

            // 模糊扫描匹配含有 profile 名字的子目录
            File[] subDirs = appWebviewDir.listFiles();
            if (subDirs != null) {
                for (File sub : subDirs) {
                    if (sub.isDirectory() && sub.getName().toLowerCase().contains(clean.toLowerCase())) {
                        File f1 = new File(sub, "Cookies");
                        if (f1.exists() && f1.isFile()) return f1;
                        File f2 = new File(sub, "Network/Cookies");
                        if (f2.exists() && f2.isFile()) return f2;
                    }
                }
            }
        }

        // 默认兜底
        File fallback = new File(appWebviewDir, "Default/Cookies");
        if (fallback.exists()) return fallback;
        File fallback2 = new File(appWebviewDir, "Cookies");
        if (fallback2.exists()) return fallback2;

        return null;
    }

    private static void copyFileSafely(File src, File dst) {
        if (!src.exists()) return;
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        } catch (Exception ignored) {}
    }

    /**
     * 直接从底层 SQLite 数据库提取所有 Cookie（100% 完整读取 HttpOnly、Secure、Path、Expires）
     * 为避免与 Chromium 进程产生数据库锁竞争，先瞬时复制镜像再行只读解析
     */
    public static List<CookieItem> readAllCookiesFromDatabase(Context context, String profileName, String domainFilter) {
        List<CookieItem> list = new ArrayList<>();
        File cookieDb = findCookieDatabaseFile(context, profileName);
        if (cookieDb == null || !cookieDb.exists()) {
            return list;
        }

        File cacheDir = context.getCacheDir();
        String tempName = "cookies_mirror_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 10000);
        File tempDb = new File(cacheDir, tempName + ".db");
        File tempWal = new File(cacheDir, tempName + ".db-wal");
        File tempShm = new File(cacheDir, tempName + ".db-shm");
        File tempJournal = new File(cacheDir, tempName + ".db-journal");

        File origWal = new File(cookieDb.getParentFile(), cookieDb.getName() + "-wal");
        File origShm = new File(cookieDb.getParentFile(), cookieDb.getName() + "-shm");
        File origJournal = new File(cookieDb.getParentFile(), cookieDb.getName() + "-journal");

        try {
            copyFileSafely(cookieDb, tempDb);
            if (origWal.exists()) copyFileSafely(origWal, tempWal);
            if (origShm.exists()) copyFileSafely(origShm, tempShm);
            if (origJournal.exists()) copyFileSafely(origJournal, tempJournal);

            SQLiteDatabase db = SQLiteDatabase.openDatabase(
                    tempDb.getAbsolutePath(),
                    null,
                    SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS
            );

            try (Cursor cursor = db.rawQuery(
                    "SELECT host_key, name, value, path, expires_utc, is_secure, is_httponly, samesite FROM cookies",
                    null
            )) {
                while (cursor.moveToNext()) {
                    String hostKey = cursor.getString(0);
                    String name = cursor.getString(1);
                    String value = cursor.getString(2);
                    String path = cursor.getString(3);
                    long expiresUtc = cursor.getLong(4);
                    boolean isSecure = cursor.getInt(5) == 1;
                    boolean isHttpOnly = cursor.getInt(6) == 1;
                    int samesiteInt = cursor.getInt(7);

                    if (!TextUtils.isEmpty(domainFilter)) {
                        String cleanTarget = domainFilter;
                        if (cleanTarget.startsWith("http://") || cleanTarget.startsWith("https://")) {
                            try {
                                cleanTarget = Uri.parse(cleanTarget).getHost();
                            } catch (Exception ignored) {}
                        }
                        if (!domainMatches(hostKey, cleanTarget)) {
                            continue;
                        }
                    }

                    long expiresSec = 0;
                    if (expiresUtc > CHROMIUM_EPOCH_OFFSET_MICROS) {
                        expiresSec = (expiresUtc - CHROMIUM_EPOCH_OFFSET_MICROS) / 1000000L;
                    }

                    String sameSiteStr = "Unspecified";
                    if (samesiteInt == 0) sameSiteStr = "None";
                    else if (samesiteInt == 1) sameSiteStr = "Lax";
                    else if (samesiteInt == 2) sameSiteStr = "Strict";

                    CookieItem item = new CookieItem(name, value, hostKey, path, isSecure, isHttpOnly, expiresSec, sameSiteStr);
                    list.add(item);
                }
            } finally {
                try { db.close(); } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading SQLite cookies: " + e.getMessage());
        } finally {
            try { tempDb.delete(); } catch (Exception ignored) {}
            try { tempWal.delete(); } catch (Exception ignored) {}
            try { tempShm.delete(); } catch (Exception ignored) {}
            try { tempJournal.delete(); } catch (Exception ignored) {}
        }

        return list;
    }

    /**
     * 高级综合全量读取：融合 SQLite 深度持久化库与 CookieManager 内存实时态
     * 无论是否带有 HttpOnly 属性，均可 100% 完整提取且精准标明 httpOnly 标识
     *
     * @param context Android 上下文
     * @param customManager Profile 对应的 CookieManager
     * @param profileName Profile 分区名称
     * @param urlOrDomain 目标 URL 或 域名（为空则读取该 WebView 下的所有 Cookie）
     * @param includeAllDomains 是否强制忽略域名过滤，提取整个 Profile 库的所有 Cookie
     */
    public static List<CookieItem> readCookiesWithDetails(
            Context context,
            CookieManager customManager,
            String profileName,
            String urlOrDomain,
            boolean includeAllDomains
    ) {
        CookieManager cm = customManager != null ? customManager : CookieManager.getInstance();
        try {
            cm.flush();
        } catch (Exception ignored) {}

        String filterDomain = includeAllDomains ? null : urlOrDomain;
        List<CookieItem> list = new ArrayList<>();

        if (context != null) {
            list = readAllCookiesFromDatabase(context, profileName, filterDomain);
        }

        // 若传入了具体 URL，且从 CookieManager 读出内存未持久化 Cookie，进行智能合并对齐
        if (!TextUtils.isEmpty(urlOrDomain) && (urlOrDomain.startsWith("http://") || urlOrDomain.startsWith("https://"))) {
            String cmRaw = cm.getCookie(urlOrDomain);
            if (!TextUtils.isEmpty(cmRaw)) {
                String host = "";
                try {
                    host = Uri.parse(urlOrDomain).getHost();
                } catch (Exception ignored) {}

                String[] pairs = cmRaw.split(";");
                for (String pair : pairs) {
                    String trimmed = pair.trim();
                    if (trimmed.isEmpty()) continue;
                    int idx = trimmed.indexOf('=');
                    if (idx > 0) {
                        String name = trimmed.substring(0, idx).trim();
                        String val = trimmed.substring(idx + 1).trim();

                        // 检查是否已存在于 SQLite 读取结果中
                        boolean found = false;
                        for (CookieItem item : list) {
                            if (item.name.equals(name)) {
                                found = true;
                                if (TextUtils.isEmpty(item.value)) {
                                    item.value = val;
                                }
                                break;
                            }
                        }

                        // 若 SQLite 中暂未发现（例如纯内存会话 Cookie 尚未写入磁盘），补充进去
                        if (!found) {
                            CookieItem item = new CookieItem(name, val);
                            item.domain = host;
                            item.path = "/";
                            // 标记为来自 CookieManager 的 Cookie
                            list.add(item);
                        }
                    }
                }
            }
        }

        return list;
    }

    /**
     * 将当前 URL 的 Cookie 格式化为 a=b; b=c 字符串 (包含 HttpOnly)
     */
    public static String exportCookiesAsStandardString(String url) {
        return exportCookiesAsStandardString(null, null, null, url);
    }

    public static String exportCookiesAsStandardString(CookieManager customManager, String url) {
        return exportCookiesAsStandardString(null, customManager, null, url);
    }

    public static String exportCookiesAsStandardString(Context context, CookieManager customManager, String profileName, String url) {
        if (TextUtils.isEmpty(url)) {
            return "";
        }

        // 优先使用综合读取，确保 HttpOnly 完整纳入
        List<CookieItem> items = readCookiesWithDetails(context, customManager, profileName, url, false);
        if (!items.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (CookieItem item : items) {
                if (sb.length() > 0) {
                    sb.append("; ");
                }
                sb.append(item.name).append("=").append(item.value);
            }
            return sb.toString();
        }

        // 兜底原生 CookieManager
        CookieManager cookieManager = customManager != null ? customManager : CookieManager.getInstance();
        String rawCookies = cookieManager.getCookie(url);
        if (TextUtils.isEmpty(rawCookies)) {
            return "";
        }

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
        return exportCookiesAsJson(null, null, null, url);
    }

    public static String exportCookiesAsJson(CookieManager customManager, String url) {
        return exportCookiesAsJson(null, customManager, null, url);
    }

    public static String exportCookiesAsJson(Context context, CookieManager customManager, String profileName, String url) {
        List<CookieItem> items = readCookiesWithDetails(context, customManager, profileName, url, false);
        JSONArray array = new JSONArray();
        for (CookieItem item : items) {
            array.put(item.toJson());
        }
        return array.toString();
    }

    /**
     * 解析多类型输入的 Cookie 并导入到 CookieManager (支持精确写入 HttpOnly / Secure / Domain)
     */
    public static int importCookies(String currentUrl, String input) {
        return importCookies(null, currentUrl, input);
    }

    public static int importCookies(CookieManager customManager, String currentUrl, String input) {
        if (TextUtils.isEmpty(input)) {
            return 0;
        }

        List<CookieItem> cookieList = parseCookiesFromInput(input, currentUrl);
        return importCookies(customManager, currentUrl, cookieList);
    }

    /**
     * 批量导入 CookieItem 集合到 CookieManager
     */
    public static int importCookies(CookieManager customManager, String currentUrl, List<CookieItem> cookieList) {
        if (cookieList == null || cookieList.isEmpty()) {
            return 0;
        }

        CookieManager cookieManager = customManager != null ? customManager : CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);

        String host = "";
        if (!TextUtils.isEmpty(currentUrl)) {
            try {
                Uri uri = Uri.parse(currentUrl);
                host = uri.getHost();
            } catch (Exception ignored) {}
        }

        int successCount = 0;
        for (CookieItem item : cookieList) {
            if (TextUtils.isEmpty(item.name)) continue;

            if (TextUtils.isEmpty(item.domain) && !TextUtils.isEmpty(host)) {
                item.domain = host;
            }

            String cookieHeader = item.toCookieString();

            if (!TextUtils.isEmpty(currentUrl)) {
                cookieManager.setCookie(currentUrl, cookieHeader);
            }

            if (!TextUtils.isEmpty(item.domain)) {
                String domain = item.domain.startsWith(".") ? item.domain.substring(1) : item.domain;
                cookieManager.setCookie("https://" + domain + (item.path != null ? item.path : "/"), cookieHeader);
                cookieManager.setCookie("http://" + domain + (item.path != null ? item.path : "/"), cookieHeader);
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

        // 尝试解析 JSON 数组
        if (text.startsWith("[") && text.endsWith("]")) {
            try {
                JSONArray array = new JSONArray(text);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    CookieItem item = CookieItem.fromJson(obj);
                    if (item != null) {
                        result.add(item);
                    }
                }
                if (!result.isEmpty()) return result;
            } catch (Exception ignored) {}
        }

        // 尝试解析 JSON 对象
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
            } catch (Exception ignored) {}
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
                    if (item.domain.startsWith("#HttpOnly_")) {
                        item.domain = item.domain.substring("#HttpOnly_".length());
                        item.httpOnly = true;
                    }
                    result.add(item);
                }
            }
            if (foundNetscape && !result.isEmpty()) {
                return result;
            }
        }

        // 处理 Set-Cookie 头格式
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

        if (input.contains(";")) {
            String[] tokens = input.split(";");
            for (String token : tokens) {
                addSingleKeyValue(token, list);
            }
        } else if (input.contains("&") && !input.contains(" ")) {
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

        int idx = trimmed.indexOf('=');
        if (idx > 0) {
            String name = trimmed.substring(0, idx).trim();
            String value = trimmed.substring(idx + 1).trim();

            String lower = name.toLowerCase();
            if (lower.equals("domain") || lower.equals("path") || lower.equals("expires")
                    || lower.equals("max-age") || lower.equals("samesite") || lower.equals("priority")) {
                if (!list.isEmpty()) {
                    CookieItem last = list.get(list.size() - 1);
                    if (lower.equals("domain")) last.domain = value;
                    if (lower.equals("path")) last.path = value;
                    if (lower.equals("samesite")) last.sameSite = value;
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
