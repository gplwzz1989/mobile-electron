package com.example.cookiebrowser.security;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 商业级域名白名单与分级权限安全管理器
 */
public class DomainWhitelistManager {

    private static final String TAG = "DomainWhitelist";

    public static final int TIER_0_PUBLIC = 0;    // 公开基础接口（任何页面可用）
    public static final int TIER_1_BUSINESS = 1;  // 业务受信任接口（白名单匹配域名）
    public static final int TIER_2_CORE = 2;      // 核心高危接口（跨域Cookie、无头页面、网络穿透）

    private static volatile DomainWhitelistManager instance;

    // 白名单域名集合（支持精确域名如 "login.corp.com" 或通配符 "*.corp.com"）
    private final Set<String> tier1Whitelist = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> tier2Whitelist = Collections.synchronizedSet(new HashSet<>());

    // 是否处于严格模式（默认开启，严格检查 HTTPS 协议）
    private boolean strictHttps = true;

    private DomainWhitelistManager() {
        // 默认预置信任的白名单范例（可动态配置或从云端拉取）
        addTier1Domain("*.baidu.com");
        addTier1Domain("*.douyin.com");
        addTier1Domain("*.github.com");
        addTier1Domain("localhost");
        addTier1Domain("127.0.0.1");
        addTier1Domain("10.0.2.2");
        addTier1Domain("appassets.androidplatform.net");

        addTier2Domain("*.baidu.com");
        addTier2Domain("*.douyin.com");
        addTier2Domain("localhost");
        addTier2Domain("127.0.0.1");
        addTier2Domain("10.0.2.2");
        addTier2Domain("appassets.androidplatform.net");
    }

    public static DomainWhitelistManager getInstance() {
        if (instance == null) {
            synchronized (DomainWhitelistManager.class) {
                if (instance == null) {
                    instance = new DomainWhitelistManager();
                }
            }
        }
        return instance;
    }

    private String normalizePattern(String pattern) {
        if (pattern == null) return "";
        String p = pattern.trim().toLowerCase();
        if (p.startsWith("http://")) p = p.substring(7);
        if (p.startsWith("https://")) p = p.substring(8);
        if (p.startsWith("file://")) return "file";
        int slashIdx = p.indexOf('/');
        if (slashIdx != -1) p = p.substring(0, slashIdx);
        int colonIdx = p.indexOf(':');
        if (colonIdx != -1) p = p.substring(0, colonIdx);
        return p;
    }

    public void addTier1Domain(String pattern) {
        if (!TextUtils.isEmpty(pattern)) {
            tier1Whitelist.add(pattern.trim().toLowerCase());
            String norm = normalizePattern(pattern);
            if (!norm.isEmpty()) {
                tier1Whitelist.add(norm);
            }
        }
    }

    public void addTier2Domain(String pattern) {
        if (!TextUtils.isEmpty(pattern)) {
            tier2Whitelist.add(pattern.trim().toLowerCase());
            String norm = normalizePattern(pattern);
            if (!norm.isEmpty()) {
                tier2Whitelist.add(norm);
            }
        }
    }

    public void setStrictHttps(boolean strict) {
        this.strictHttps = strict;
    }

    private boolean isLocalOrLanHost(String host) {
        if (host == null) return false;
        return "localhost".equals(host)
                || "127.0.0.1".equals(host)
                || "10.0.2.2".equals(host)
                || host.startsWith("192.168.")
                || host.startsWith("10.")
                || host.startsWith("172.");
    }

    /**
     * 校验指定 URL 是否具备调用对应安全等级的权限
     *
     * @param url          发起调用的页面当前 URL
     * @param requiredTier 所需权限级别 (TIER_0, TIER_1, TIER_2)
     * @return 是否放行
     */
    public boolean checkPermission(String url, int requiredTier) {
        if (requiredTier == TIER_0_PUBLIC) {
            return true;
        }

        if (TextUtils.isEmpty(url)) {
            Log.w(TAG, "Access denied: URL is empty");
            return false;
        }

        try {
            Uri uri = Uri.parse(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();

            if (TextUtils.isEmpty(host)) {
                // 支持本地内置 file:/// 协议资产包
                if ("file".equalsIgnoreCase(scheme)) {
                    return true;
                }
                return false;
            }

            // 支持 AndroidX WebViewAssetLoader 虚拟域名
            if ("appassets.androidplatform.net".equalsIgnoreCase(host)) {
                return true;
            }

            host = host.toLowerCase();

            // 严格 HTTPS 检查 (非严格模式、内网或本地地址放行 HTTP)
            boolean isLocalOrLan = isLocalOrLanHost(host);
            if (strictHttps && !isLocalOrLan) {
                if (!"https".equalsIgnoreCase(scheme)) {
                    Log.w(TAG, "Access denied: HTTP is blocked in strict mode for " + host);
                    return false;
                }
            }

            if (requiredTier == TIER_1_BUSINESS) {
                return matchesAny(host, tier1Whitelist) || matchesAny(host, tier2Whitelist);
            }

            if (requiredTier == TIER_2_CORE) {
                return matchesAny(host, tier2Whitelist);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error checking domain whitelist: " + e.getMessage());
            return false;
        }

        return false;
    }

    /**
     * 支持通配符匹配（例如 *.example.com 匹配 auth.example.com，* 匹配所有）
     */
    private boolean matchesAny(String host, Set<String> patternSet) {
        if (patternSet.contains("*") || patternSet.contains("*.*")) {
            return true;
        }
        for (String rule : patternSet) {
            if ("*".equals(rule) || "*.*".equals(rule)) {
                return true;
            }
            if (rule.equals(host)) {
                return true;
            }
            if (rule.startsWith("*.")) {
                String rootDomain = rule.substring(2);
                if (host.equals(rootDomain) || host.endsWith("." + rootDomain)) {
                    return true;
                }
            }
        }
        return false;
    }

    public Set<String> getTier1Whitelist() {
        return new HashSet<>(tier1Whitelist);
    }

    public Set<String> getTier2Whitelist() {
        return new HashSet<>(tier2Whitelist);
    }
}
