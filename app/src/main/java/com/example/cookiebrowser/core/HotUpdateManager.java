package com.example.cookiebrowser.core;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebResourceResponse;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Mobile Electron 离线资源包引擎与热更新管理器
 * 负责本地沙箱离线包生命周期维护、增量下载、SHA-256完整性校验、原子替换与白屏容灾回滚
 */
public class HotUpdateManager {

    private static final String TAG = "HotUpdateManager";
    private static volatile HotUpdateManager instance;

    private final ExecutorService workerPool = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Context context;
    private File rootBundlesDir;
    private File activeDir;
    private File stagingDir;
    private File activeVersionFile;

    private JSONObject activeVersionInfo = null;

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(int code, String message);
    }

    public interface ProgressCallback {
        void onProgress(int percent, long downloadedBytes, long totalBytes);
    }

    private HotUpdateManager() {}

    public static HotUpdateManager getInstance() {
        if (instance == null) {
            synchronized (HotUpdateManager.class) {
                if (instance == null) {
                    instance = new HotUpdateManager();
                }
            }
        }
        return instance;
    }

    public void init(Context ctx) {
        this.context = ctx.getApplicationContext();
        this.rootBundlesDir = new File(context.getFilesDir(), "mobile_electron_bundles");
        this.activeDir = new File(rootBundlesDir, "active");
        this.stagingDir = new File(rootBundlesDir, "staging");
        this.activeVersionFile = new File(rootBundlesDir, "active_version.json");

        if (!rootBundlesDir.exists()) {
            rootBundlesDir.mkdirs();
        }

        verifyAndLoadActiveVersion();
    }

    private void verifyAndLoadActiveVersion() {
        if (activeVersionFile.exists() && activeDir.exists()) {
            File indexFile = new File(activeDir, "index.html");
            if (indexFile.exists() && indexFile.length() > 0) {
                try {
                    String jsonStr = readFileToString(activeVersionFile);
                    this.activeVersionInfo = new JSONObject(jsonStr);
                    Log.i(TAG, "已加载生效的沙箱热更新离线包: v" + activeVersionInfo.optString("version", "unknown"));
                    return;
                } catch (Exception e) {
                    Log.e(TAG, "解析 active_version.json 失败，执行自动回滚兜底", e);
                }
            } else {
                Log.w(TAG, "沙箱 index.html 缺失或损坏，执行自愈回滚至内置资源");
            }
            // 发生异常或文件损坏，自愈清理
            rollback();
        } else {
            this.activeVersionInfo = null;
        }
    }

    public boolean hasActiveBundle() {
        return activeDir != null && activeDir.exists() && new File(activeDir, "index.html").exists() && activeVersionInfo != null;
    }

    public File getActiveBundleDir() {
        return hasActiveBundle() ? activeDir : null;
    }

    public JSONObject getActiveVersionInfo() {
        try {
            if (hasActiveBundle() && activeVersionInfo != null) {
                JSONObject copy = new JSONObject(activeVersionInfo.toString());
                copy.put("isHotUpdated", true);
                copy.put("source", "sandbox");
                return copy;
            } else {
                JSONObject info = new JSONObject();
                String defaultVer = AppConfigManager.getInstance().getConfig().getVersion();
                info.put("version", defaultVer);
                info.put("buildNumber", 100);
                info.put("isHotUpdated", false);
                info.put("source", "builtin");
                info.put("description", "原生底座内置静态离线包");
                return info;
            }
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    /**
     * 检查远端是否有新版本离线包
     */
    public void checkForUpdate(String customUrl, Callback<JSONObject> callback) {
        workerPool.execute(() -> {
            try {
                String checkUrl = (customUrl != null && !customUrl.trim().isEmpty())
                        ? customUrl.trim()
                        : "http://10.0.2.2:8088/update-manifest.json";

                Log.i(TAG, "正在检查热更新: " + checkUrl);
                URL url = new URL(checkUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("Accept", "application/json");

                int respCode = conn.getResponseCode();
                if (respCode != 200) {
                    postError(callback, respCode, "检查更新失败，服务器返回 HTTP " + respCode);
                    return;
                }

                String body = readStreamToString(conn.getInputStream());
                JSONObject manifest = new JSONObject(body);

                String latestVersion = manifest.optString("latestVersion", "");
                int latestBuild = manifest.optInt("buildNumber", 0);
                String packageUrl = manifest.optString("packageUrl", "");
                String packageHash = manifest.optString("packageHash", "");
                long packageSize = manifest.optLong("packageSize", 0);
                String desc = manifest.optString("description", "");
                boolean mandatory = manifest.optBoolean("mandatory", false);

                // 解析相对 packageUrl 为绝对路径
                if (!packageUrl.startsWith("http://") && !packageUrl.startsWith("https://")) {
                    packageUrl = new URL(url, packageUrl).toString();
                }

                JSONObject current = getActiveVersionInfo();
                String curVersion = current.optString("version", "2.0.0");
                int curBuild = current.optInt("buildNumber", 100);

                boolean hasUpdate = false;
                if (latestBuild > 0 && curBuild > 0) {
                    hasUpdate = latestBuild > curBuild;
                } else {
                    hasUpdate = compareVersions(latestVersion, curVersion) > 0;
                }

                JSONObject result = new JSONObject();
                result.put("hasUpdate", hasUpdate);
                result.put("currentVersion", curVersion);
                result.put("currentBuild", curBuild);
                result.put("latestVersion", latestVersion);
                result.put("latestBuild", latestBuild);
                result.put("packageUrl", packageUrl);
                result.put("packageHash", packageHash);
                result.put("packageSize", packageSize);
                result.put("description", desc);
                result.put("mandatory", mandatory);

                postSuccess(callback, result);

            } catch (Exception e) {
                Log.e(TAG, "检查热更新异常", e);
                postError(callback, 500, "检查更新失败: " + e.getMessage());
            }
        });
    }

    /**
     * 下载离线包并解压安装到沙箱
     */
    public void downloadAndInstall(String packageUrl, String expectedHash, ProgressCallback progressCb, Callback<JSONObject> callback) {
        workerPool.execute(() -> {
            try {
                if (packageUrl == null || packageUrl.trim().isEmpty()) {
                    postError(callback, 400, "packageUrl 不能为空");
                    return;
                }

                if (!stagingDir.exists()) {
                    stagingDir.mkdirs();
                } else {
                    deleteDir(stagingDir);
                    stagingDir.mkdirs();
                }

                File downloadFile = new File(stagingDir, "update_bundle.mep");
                Log.i(TAG, "开始下载热更新离线包: " + packageUrl);

                URL url = new URL(packageUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);

                int respCode = conn.getResponseCode();
                if (respCode != 200) {
                    postError(callback, respCode, "离线包下载失败，HTTP " + respCode);
                    return;
                }

                long totalBytes = conn.getContentLength();
                InputStream in = new BufferedInputStream(conn.getInputStream());
                OutputStream out = new FileOutputStream(downloadFile);

                byte[] buffer = new byte[8192];
                long downloadedBytes = 0;
                int read;
                int lastReportedPercent = -1;

                MessageDigest digest = MessageDigest.getInstance("SHA-256");

                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                    downloadedBytes += read;

                    if (totalBytes > 0 && progressCb != null) {
                        int percent = (int) ((downloadedBytes * 100) / totalBytes);
                        if (percent != lastReportedPercent) {
                            lastReportedPercent = percent;
                            final int currentPercent = percent;
                            final long currentDownloaded = downloadedBytes;
                            mainHandler.post(() -> progressCb.onProgress(currentPercent, currentDownloaded, totalBytes));
                        }
                    }
                }

                out.flush();
                out.close();
                in.close();

                // 1. 校验 SHA-256
                byte[] hashBytes = digest.digest();
                String actualHashHex = bytesToHex(hashBytes);
                String actualHash = "sha256:" + actualHashHex;

                if (expectedHash != null && !expectedHash.trim().isEmpty()) {
                    String cleanExpected = expectedHash.trim().toLowerCase();
                    if (cleanExpected.startsWith("sha256:")) {
                        cleanExpected = cleanExpected.substring(7);
                    }
                    if (!actualHashHex.equalsIgnoreCase(cleanExpected)) {
                        postError(callback, 403, "离线包完整性校验失败 (Hash 不匹配)! 预期: " + cleanExpected + ", 实际: " + actualHashHex);
                        deleteDir(stagingDir);
                        return;
                    }
                    Log.i(TAG, "✓ 离线包 SHA-256 完整性校验通过");
                }

                // 2. 解压到 staging/unzipped
                File unzippedDir = new File(stagingDir, "unzipped");
                unzippedDir.mkdirs();
                unzip(downloadFile, unzippedDir);

                // 检查解压结构（如果最外层包装了 dist 目录，自动向上一级展平）
                File candidateIndex = new File(unzippedDir, "index.html");
                File finalContentDir = unzippedDir;
                if (!candidateIndex.exists()) {
                    File distSub = new File(unzippedDir, "dist");
                    if (distSub.exists() && new File(distSub, "index.html").exists()) {
                        finalContentDir = distSub;
                    } else {
                        postError(callback, 422, "离线包解压内容损坏，未找到主入口 index.html");
                        deleteDir(stagingDir);
                        return;
                    }
                }

                // 读取内部 manifest.json（若有）
                JSONObject internalManifest = null;
                File manifestFile = new File(finalContentDir, "manifest.json");
                if (manifestFile.exists()) {
                    try {
                        internalManifest = new JSONObject(readFileToString(manifestFile));
                    } catch (Exception ignored) {}
                }

                // 3. 原子替换沙箱 active 目录
                File backupDir = new File(rootBundlesDir, "active_backup");
                if (backupDir.exists()) deleteDir(backupDir);

                if (activeDir.exists()) {
                    activeDir.renameTo(backupDir);
                }

                activeDir.mkdirs();
                copyDir(finalContentDir, activeDir);

                // 4. 写入 active_version.json
                JSONObject newVersionInfo = new JSONObject();
                if (internalManifest != null) {
                    newVersionInfo.put("version", internalManifest.optString("version", "2.1.0"));
                    newVersionInfo.put("buildNumber", internalManifest.optInt("buildNumber", 210));
                    newVersionInfo.put("description", internalManifest.optString("description", ""));
                } else {
                    newVersionInfo.put("version", "2.1.0");
                    newVersionInfo.put("buildNumber", 210);
                    newVersionInfo.put("description", "热更新完成");
                }
                newVersionInfo.put("hash", actualHash);
                newVersionInfo.put("appliedAt", System.currentTimeMillis());
                newVersionInfo.put("fileCount", countFiles(activeDir));

                writeStringToFile(activeVersionFile, newVersionInfo.toString(2));
                this.activeVersionInfo = newVersionInfo;

                // 清理临时文件
                deleteDir(stagingDir);
                if (backupDir.exists()) deleteDir(backupDir);

                Log.i(TAG, "🎉 热更新安装成功！新版本: " + newVersionInfo.optString("version"));
                postSuccess(callback, newVersionInfo);

            } catch (Exception e) {
                Log.e(TAG, "下载安装离线包异常", e);
                postError(callback, 500, "安装离线包异常: " + e.getMessage());
            }
        });
    }

    /**
     * 回滚至内置离线包
     */
    public boolean rollback() {
        try {
            if (activeDir != null && activeDir.exists()) {
                deleteDir(activeDir);
            }
            if (activeVersionFile != null && activeVersionFile.exists()) {
                activeVersionFile.delete();
            }
            if (stagingDir != null && stagingDir.exists()) {
                deleteDir(stagingDir);
            }
            this.activeVersionInfo = null;
            Log.i(TAG, "已安全回滚至内置原始底座离线包");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "回滚失败", e);
            return false;
        }
    }

    /**
     * 给 WebViewPool 提供静态资源本地沙箱拦截
     * @param url 请求的虚拟 HTTPS URL，如 https://appassets.androidplatform.net/assets/dist/index.html
     */
    public WebResourceResponse interceptSandboxResource(Uri url) {
        if (!hasActiveBundle() || url == null) return null;

        String path = url.getPath();
        if (path == null) return null;

        // 规范化路径匹配
        String relPath = "";
        if (path.startsWith("/assets/dist/")) {
            relPath = path.substring("/assets/dist/".length());
        } else if (path.startsWith("/assets/")) {
            relPath = path.substring("/assets/".length());
        } else if (path.startsWith("/dist/")) {
            relPath = path.substring("/dist/".length());
        } else if (path.startsWith("/")) {
            relPath = path.substring(1);
        }

        if (relPath.isEmpty()) {
            relPath = "index.html";
        }

        File targetFile = new File(activeDir, relPath);
        if (targetFile.exists() && targetFile.isFile()) {
            try {
                String mimeType = getMimeType(targetFile.getName());
                InputStream is = new FileInputStream(targetFile);
                return new WebResourceResponse(mimeType, "UTF-8", is);
            } catch (Exception e) {
                Log.e(TAG, "拦截读取沙箱文件失败: " + targetFile.getAbsolutePath(), e);
            }
        }

        return null;
    }

    private String getMimeType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html";
        if (lower.endsWith(".js") || lower.endsWith(".mjs")) return "application/javascript";
        if (lower.endsWith(".css")) return "text/css";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".ico")) return "image/x-icon";
        if (lower.endsWith(".woff")) return "font/woff";
        if (lower.endsWith(".woff2")) return "font/woff2";
        if (lower.endsWith(".ttf")) return "font/ttf";
        return "application/octet-stream";
    }

    private void unzip(File zipFile, File destDir) throws Exception {
        ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile));
        ZipEntry entry;
        byte[] buffer = new byte[8192];

        while ((entry = zis.getNextEntry()) != null) {
            String name = entry.getName();
            File newFile = new File(destDir, name);

            // 防止 Zip Slip 漏洞
            String destDirPath = destDir.getCanonicalPath();
            String newFilePath = newFile.getCanonicalPath();
            if (!newFilePath.startsWith(destDirPath + File.separator) && !newFilePath.equals(destDirPath)) {
                throw new SecurityException("Zip 路径穿越安全异常: " + name);
            }

            if (entry.isDirectory()) {
                newFile.mkdirs();
            } else {
                File parent = newFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                FileOutputStream fos = new FileOutputStream(newFile);
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
                fos.close();
            }
            zis.closeEntry();
        }
        zis.close();
    }

    private int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            int p1 = i < parts1.length ? Integer.parseInt(parts1[i].replaceAll("[^0-9]", "")) : 0;
            int p2 = i < parts2.length ? Integer.parseInt(parts2[i].replaceAll("[^0-9]", "")) : 0;
            if (p1 != p2) return Integer.compare(p1, p2);
        }
        return 0;
    }

    private void copyDir(File src, File dest) throws Exception {
        if (src.isDirectory()) {
            if (!dest.exists()) dest.mkdirs();
            String[] children = src.list();
            if (children != null) {
                for (String child : children) {
                    copyDir(new File(src, child), new File(dest, child));
                }
            }
        } else {
            InputStream in = new FileInputStream(src);
            OutputStream out = new FileOutputStream(dest);
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            in.close();
            out.close();
        }
    }

    private void deleteDir(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteDir(child);
                }
            }
        }
        file.delete();
    }

    private int countFiles(File dir) {
        int count = 0;
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isDirectory()) count += countFiles(f);
                    else count++;
                }
            }
        }
        return count;
    }

    private String readFileToString(File f) throws Exception {
        InputStream is = new FileInputStream(f);
        return readStreamToString(is);
    }

    private String readStreamToString(InputStream is) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int len;
        while ((len = is.read(buf)) != -1) {
            baos.write(buf, 0, len);
        }
        is.close();
        return baos.toString("UTF-8");
    }

    private void writeStringToFile(File f, String content) throws Exception {
        FileOutputStream fos = new FileOutputStream(f);
        fos.write(content.getBytes("UTF-8"));
        fos.close();
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private <T> void postSuccess(Callback<T> cb, T result) {
        if (cb != null) {
            mainHandler.post(() -> cb.onSuccess(result));
        }
    }

    private <T> void postError(Callback<T> cb, int code, String msg) {
        if (cb != null) {
            mainHandler.post(() -> cb.onError(code, msg));
        }
    }
}
