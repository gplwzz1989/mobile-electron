package com.example.cookiebrowser.plugins;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import com.example.cookiebrowser.bridge.BridgeCallback;
import com.example.cookiebrowser.bridge.IBridgePlugin;
import com.example.cookiebrowser.security.DomainWhitelistManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * 工业级安全加密存储插件 (Secure Storage Plugin)
 * 采用硬件级 AndroidKeyStore (TEE / Secure Enclave) + AES-256-GCM 认证加密
 * 零第三方依赖，安全存储敏感 Token、私钥及账号凭据
 */
public class SecureStoragePlugin implements IBridgePlugin {

    private static final String TAG = "SecureStoragePlugin";
    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "MobileElectron_Vault_Key";
    private static final String PREF_NAME = "MobileElectron_SecureVault";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96-bit IV for AES-GCM
    private static final int GCM_TAG_LENGTH = 128; // 128-bit authentication tag

    private final Context context;
    private final SharedPreferences prefs;

    public SecureStoragePlugin(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        ensureSecretKey();
    }

    @Override
    public String getModuleName() {
        return "secureStorage";
    }

    @Override
    public int getRequiredTier(String action) {
        // 安全存储属于核心机密接口
        return DomainWhitelistManager.TIER_1_BUSINESS;
    }

    private synchronized void ensureSecretKey() {
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
            keyStore.load(null);
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                KeyGenerator keyGenerator = KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE);

                KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256);

                keyGenerator.init(builder.build());
                keyGenerator.generateKey();
                Log.i(TAG, "Hardware AES-256 secret key generated in AndroidKeyStore.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize AndroidKeyStore: " + e.getMessage(), e);
        }
    }

    private SecretKey getSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
        keyStore.load(null);
        return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
    }

    private String encrypt(String plainText) throws Exception {
        if (plainText == null) plainText = "";
        SecretKey secretKey = getSecretKey();
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);

        byte[] iv = cipher.getIV();
        byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

        // 拼接 IV (12 bytes) + CipherText 并转为 Base64 存储
        byte[] combined = new byte[iv.length + cipherText.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

        return Base64.encodeToString(combined, Base64.NO_WRAP);
    }

    private String decrypt(String base64Combined) throws Exception {
        if (base64Combined == null || base64Combined.isEmpty()) return null;
        byte[] combined = Base64.decode(base64Combined, Base64.NO_WRAP);
        if (combined.length <= GCM_IV_LENGTH) {
            throw new IllegalArgumentException("Corrupted encrypted payload");
        }

        byte[] iv = new byte[GCM_IV_LENGTH];
        byte[] cipherText = new byte[combined.length - GCM_IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
        System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.length);

        SecretKey secretKey = getSecretKey();
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

        byte[] plainBytes = cipher.doFinal(cipherText);
        return new String(plainBytes, StandardCharsets.UTF_8);
    }

    @Override
    public void handleAction(String callingUrl, String action, JSONObject params, BridgeCallback callback) throws Exception {
        switch (action) {
            case "setItem": {
                String key = params.optString("key", "");
                String value = params.optString("value", "");
                if (key.isEmpty()) {
                    callback.error(400, "Missing key parameter");
                    return;
                }
                try {
                    String encrypted = encrypt(value);
                    prefs.edit().putString(key, encrypted).apply();
                    JSONObject res = new JSONObject();
                    res.put("success", true);
                    callback.success(res);
                } catch (Exception e) {
                    Log.e(TAG, "Encrypt error: " + e.getMessage(), e);
                    callback.error(500, "Encryption failed: " + e.getMessage());
                }
                break;
            }

            case "getItem": {
                String key = params.optString("key", "");
                if (key.isEmpty()) {
                    callback.error(400, "Missing key parameter");
                    return;
                }
                String encrypted = prefs.getString(key, null);
                JSONObject res = new JSONObject();
                if (encrypted == null) {
                    res.put("value", JSONObject.NULL);
                    res.put("exists", false);
                    callback.success(res);
                    return;
                }
                try {
                    String decrypted = decrypt(encrypted);
                    res.put("value", decrypted);
                    res.put("exists", true);
                    callback.success(res);
                } catch (Exception e) {
                    Log.e(TAG, "Decrypt error: " + e.getMessage(), e);
                    callback.error(500, "Decryption failed: " + e.getMessage());
                }
                break;
            }

            case "removeItem": {
                String key = params.optString("key", "");
                if (key.isEmpty()) {
                    callback.error(400, "Missing key parameter");
                    return;
                }
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

            case "getAllKeys": {
                Map<String, ?> all = prefs.getAll();
                JSONArray keysArray = new JSONArray();
                for (String k : all.keySet()) {
                    keysArray.put(k);
                }
                JSONObject res = new JSONObject();
                res.put("keys", keysArray);
                callback.success(res);
                break;
            }

            default:
                callback.error(404, "Unknown action: " + action);
                break;
        }
    }
}
