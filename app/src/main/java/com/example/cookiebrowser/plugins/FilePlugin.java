package com.example.cookiebrowser.plugins;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Base64;

import com.example.cookiebrowser.bridge.BridgeCallback;
import com.example.cookiebrowser.bridge.IBridgePlugin;
import com.example.cookiebrowser.security.DomainWhitelistManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 原生本地安全沙箱文件系统读写插件 (Local File System Plugin)
 */
public class FilePlugin implements IBridgePlugin {

    private final Context context;
    private final ExecutorService ioThreadPool = Executors.newFixedThreadPool(4);

    public FilePlugin(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public String getModuleName() {
        return "file";
    }

    @Override
    public int getRequiredTier(String action) {
        // 文件读写属于业务核心特权，需 Tier 1 或 Tier 2 鉴权
        if ("getPaths".equals(action) || "exists".equals(action) || "stat".equals(action)) {
            return DomainWhitelistManager.TIER_1_BUSINESS;
        }
        return DomainWhitelistManager.TIER_2_CORE;
    }

    @Override
    public void handleAction(String callingUrl, String action, JSONObject params, BridgeCallback callback) throws Exception {
        ioThreadPool.execute(() -> {
            try {
                switch (action) {
                    case "getPaths": {
                        JSONObject res = new JSONObject();
                        res.put("documents", getSafeBaseDir("documents").getAbsolutePath());
                        res.put("cache", getSafeBaseDir("cache").getAbsolutePath());
                        res.put("temp", getSafeBaseDir("temp").getAbsolutePath());
                        callback.success(res);
                        break;
                    }

                    case "write": {
                        String relativePath = params.optString("path", "");
                        String content = params.optString("content", "");
                        String encoding = params.optString("encoding", "utf8");
                        boolean append = params.optBoolean("append", false);
                        String directory = params.optString("directory", "documents");

                        if (TextUtils.isEmpty(relativePath)) {
                            callback.error(400, "Missing path parameter");
                            return;
                        }

                        File targetFile = resolveSafeFile(directory, relativePath);
                        File parent = targetFile.getParentFile();
                        if (parent != null && !parent.exists()) {
                            parent.mkdirs();
                        }

                        byte[] bytesToWrite;
                        if ("base64".equalsIgnoreCase(encoding)) {
                            bytesToWrite = Base64.decode(content, Base64.DEFAULT);
                        } else {
                            bytesToWrite = content.getBytes(StandardCharsets.UTF_8);
                        }

                        try (FileOutputStream fos = new FileOutputStream(targetFile, append)) {
                            fos.write(bytesToWrite);
                            fos.flush();
                        }

                        JSONObject res = new JSONObject();
                        res.put("success", true);
                        res.put("bytesWritten", bytesToWrite.length);
                        res.put("fullPath", targetFile.getAbsolutePath());
                        callback.success(res);
                        break;
                    }

                    case "read": {
                        String relativePath = params.optString("path", "");
                        String encoding = params.optString("encoding", "utf8");
                        String directory = params.optString("directory", "documents");

                        if (TextUtils.isEmpty(relativePath)) {
                            callback.error(400, "Missing path parameter");
                            return;
                        }

                        File targetFile = resolveSafeFile(directory, relativePath);
                        if (!targetFile.exists() || !targetFile.isFile()) {
                            callback.error(404, "File not found: " + relativePath);
                            return;
                        }

                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        try (FileInputStream fis = new FileInputStream(targetFile)) {
                            byte[] buf = new byte[8192];
                            int len;
                            while ((len = fis.read(buf)) != -1) {
                                baos.write(buf, 0, len);
                            }
                        }

                        byte[] data = baos.toByteArray();
                        String contentResult;
                        if ("base64".equalsIgnoreCase(encoding)) {
                            contentResult = Base64.encodeToString(data, Base64.NO_WRAP);
                        } else {
                            contentResult = new String(data, StandardCharsets.UTF_8);
                        }

                        JSONObject res = new JSONObject();
                        res.put("content", contentResult);
                        res.put("size", data.length);
                        res.put("encoding", encoding);
                        callback.success(res);
                        break;
                    }

                    case "exists": {
                        String relativePath = params.optString("path", "");
                        String directory = params.optString("directory", "documents");
                        File targetFile = resolveSafeFile(directory, relativePath);

                        JSONObject res = new JSONObject();
                        res.put("exists", targetFile.exists());
                        res.put("isFile", targetFile.isFile());
                        res.put("isDirectory", targetFile.isDirectory());
                        callback.success(res);
                        break;
                    }

                    case "delete": {
                        String relativePath = params.optString("path", "");
                        String directory = params.optString("directory", "documents");
                        File targetFile = resolveSafeFile(directory, relativePath);

                        boolean deleted = deleteRecursive(targetFile);
                        JSONObject res = new JSONObject();
                        res.put("success", deleted);
                        callback.success(res);
                        break;
                    }

                    case "list": {
                        String relativePath = params.optString("path", "");
                        String directory = params.optString("directory", "documents");
                        File targetDir = resolveSafeFile(directory, relativePath);

                        if (!targetDir.exists() || !targetDir.isDirectory()) {
                            callback.error(404, "Directory not found: " + relativePath);
                            return;
                        }

                        File[] files = targetDir.listFiles();
                        JSONArray arr = new JSONArray();
                        if (files != null) {
                            for (File f : files) {
                                JSONObject item = new JSONObject();
                                item.put("name", f.getName());
                                item.put("isDirectory", f.isDirectory());
                                item.put("size", f.length());
                                item.put("lastModified", f.lastModified());
                                arr.put(item);
                            }
                        }

                        JSONObject res = new JSONObject();
                        res.put("files", arr);
                        callback.success(res);
                        break;
                    }

                    case "mkdir": {
                        String relativePath = params.optString("path", "");
                        String directory = params.optString("directory", "documents");
                        File targetDir = resolveSafeFile(directory, relativePath);

                        boolean ok = targetDir.mkdirs() || targetDir.exists();
                        JSONObject res = new JSONObject();
                        res.put("success", ok);
                        callback.success(res);
                        break;
                    }

                    case "download": {
                        String urlStr = params.optString("url", "");
                        String destPath = params.optString("destinationPath", "");
                        String directory = params.optString("directory", "documents");

                        if (TextUtils.isEmpty(urlStr) || TextUtils.isEmpty(destPath)) {
                            callback.error(400, "Missing url or destinationPath");
                            return;
                        }

                        File destFile = resolveSafeFile(directory, destPath);
                        File parent = destFile.getParentFile();
                        if (parent != null && !parent.exists()) {
                            parent.mkdirs();
                        }

                        URL u = new URL(urlStr);
                        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                        conn.setConnectTimeout(15000);
                        conn.setReadTimeout(30000);
                        conn.connect();

                        int code = conn.getResponseCode();
                        if (code >= 400) {
                            callback.error(code, "HTTP download error: " + code);
                            return;
                        }

                        long totalBytes = 0;
                        try (InputStream in = new BufferedInputStream(conn.getInputStream());
                             FileOutputStream fos = new FileOutputStream(destFile)) {
                            byte[] buffer = new byte[8192];
                            int read;
                            while ((read = in.read(buffer)) != -1) {
                                fos.write(buffer, 0, read);
                                totalBytes += read;
                            }
                            fos.flush();
                        }

                        JSONObject res = new JSONObject();
                        res.put("success", true);
                        res.put("size", totalBytes);
                        res.put("fullPath", destFile.getAbsolutePath());
                        callback.success(res);
                        break;
                    }

                    default:
                        callback.error(404, "Unknown action '" + action + "' in module 'file'");
                        break;
                }
            } catch (Exception e) {
                callback.error(500, "File IO Error: " + e.getMessage());
            }
        });
    }

    private File getSafeBaseDir(String directoryType) {
        if ("cache".equalsIgnoreCase(directoryType)) {
            return context.getCacheDir();
        } else if ("temp".equalsIgnoreCase(directoryType)) {
            File temp = new File(context.getCacheDir(), "temp");
            if (!temp.exists()) temp.mkdirs();
            return temp;
        }
        return context.getFilesDir(); // documents 默认私有数据目录
    }

    private File resolveSafeFile(String directoryType, String relativePath) {
        File base = getSafeBaseDir(directoryType);
        // 防路径遍历攻击 (Path Traversal 防护)
        String sanitized = relativePath.replace("..", "").replace("\\", "/");
        if (sanitized.startsWith("/")) sanitized = sanitized.substring(1);
        return new File(base, sanitized);
    }

    private boolean deleteRecursive(File f) {
        if (f == null || !f.exists()) return true;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File c : children) {
                    deleteRecursive(c);
                }
            }
        }
        return f.delete();
    }
}
