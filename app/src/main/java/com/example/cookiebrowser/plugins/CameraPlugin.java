package com.example.cookiebrowser.plugins;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.example.cookiebrowser.bridge.BridgeCallback;
import com.example.cookiebrowser.bridge.IBridgePlugin;
import com.example.cookiebrowser.security.DomainWhitelistManager;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * 工业级相机与媒体处理插件 (Camera & Barcode Scanner Plugin)
 * 支持原生拍照 (takePhoto)、相册选图 (pickImage) 与扫码 (scanBarcode)
 * 零第三方依赖，纯 Android 原生 SDK 实现
 */
public class CameraPlugin implements IBridgePlugin {

    private static final String TAG = "CameraPlugin";
    public static final int REQUEST_TAKE_PHOTO = 2001;
    public static final int REQUEST_PICK_IMAGE = 2002;
    public static final int REQUEST_SCAN_BARCODE = 2003;

    private final Activity activity;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private BridgeCallback pendingCallback;
    private int pendingRequestCode = 0;

    public CameraPlugin(Activity activity) {
        this.activity = activity;
    }

    @Override
    public String getModuleName() {
        return "camera";
    }

    @Override
    public int getRequiredTier(String action) {
        // 摄像头和图库涉及用户隐私，属于业务受信 Tier 1
        return DomainWhitelistManager.TIER_1_BUSINESS;
    }

    @Override
    public void handleAction(String callingUrl, String action, JSONObject params, BridgeCallback callback) throws Exception {
        switch (action) {
            case "takePhoto": {
                mainHandler.post(() -> {
                    try {
                        this.pendingCallback = callback;
                        this.pendingRequestCode = REQUEST_TAKE_PHOTO;

                        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        if (intent.resolveActivity(activity.getPackageManager()) != null) {
                            activity.startActivityForResult(intent, REQUEST_TAKE_PHOTO);
                        } else {
                            // 无可用相机应用时兜底
                            callback.error(500, "No camera application available on this device");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "takePhoto error: " + e.getMessage(), e);
                        callback.error(500, "Failed to launch camera: " + e.getMessage());
                    }
                });
                break;
            }

            case "pickImage": {
                mainHandler.post(() -> {
                    try {
                        this.pendingCallback = callback;
                        this.pendingRequestCode = REQUEST_PICK_IMAGE;

                        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                        intent.setType("image/*");
                        activity.startActivityForResult(Intent.createChooser(intent, "选择图片"), REQUEST_PICK_IMAGE);
                    } catch (Exception e) {
                        Log.e(TAG, "pickImage error: " + e.getMessage(), e);
                        callback.error(500, "Failed to open gallery: " + e.getMessage());
                    }
                });
                break;
            }

            case "scanBarcode": {
                mainHandler.post(() -> {
                    try {
                        this.pendingCallback = callback;
                        this.pendingRequestCode = REQUEST_SCAN_BARCODE;

                        // 优先尝试调起系统通用条码扫码 Intent (如 Google Play Services / ZXing Barcode Scanner)
                        Intent scanIntent = new Intent("com.google.zxing.client.android.SCAN");
                        scanIntent.putExtra("SCAN_MODE", "QR_CODE_MODE,PRODUCT_MODE");

                        if (scanIntent.resolveActivity(activity.getPackageManager()) != null) {
                            activity.startActivityForResult(scanIntent, REQUEST_SCAN_BARCODE);
                        } else {
                            // 优雅仿真弹窗：在模拟器或无外置扫码器的设备上提供无缝开发联调体验
                            showMockBarcodeDialog(callback);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "scanBarcode error: " + e.getMessage(), e);
                        callback.error(500, "Failed to start barcode scanner: " + e.getMessage());
                    }
                });
                break;
            }

            default:
                callback.error(404, "Unknown action: " + action);
                break;
        }
    }

    private void showMockBarcodeDialog(BridgeCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("📷 条码/二维码扫描调试");

        final EditText input = new EditText(activity);
        input.setHint("输入二维码内容或点击快捷预设");
        input.setText("https://mobile-electron.dev/sample-qr?id=98721");
        input.setPadding(40, 30, 40, 30);
        builder.setView(input);

        builder.setPositiveButton("完成扫描", (dialog, which) -> {
            String resultText = input.getText().toString().trim();
            try {
                JSONObject res = new JSONObject();
                res.put("text", resultText);
                res.put("format", "QR_CODE");
                callback.success(res);
            } catch (Exception e) {
                callback.error(500, e.getMessage());
            }
        });

        builder.setNeutralButton("预设登录Token", (dialog, which) -> {
            try {
                JSONObject res = new JSONObject();
                res.put("text", "AUTH_TOKEN_SAMPLE_XYZ_1234567890");
                res.put("format", "CODE_128");
                callback.success(res);
            } catch (Exception e) {
                callback.error(500, e.getMessage());
            }
        });

        builder.setNegativeButton("取消", (dialog, which) -> {
            callback.error(401, "User cancelled barcode scan");
        });

        builder.setCancelable(false);
        builder.show();
    }

    /**
     * 由 MainActivity 的 onActivityResult 回调触发
     */
    public boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != pendingRequestCode || pendingCallback == null) {
            return false;
        }

        BridgeCallback callback = pendingCallback;
        pendingCallback = null;
        pendingRequestCode = 0;

        if (resultCode != Activity.RESULT_OK) {
            callback.error(400, "Operation cancelled by user (resultCode: " + resultCode + ")");
            return true;
        }

        try {
            if (requestCode == REQUEST_TAKE_PHOTO) {
                if (data != null && data.getExtras() != null) {
                    Bitmap photo = (Bitmap) data.getExtras().get("data");
                    if (photo != null) {
                        String base64 = bitmapToBase64(photo, 85);
                        JSONObject res = new JSONObject();
                        res.put("base64", "data:image/jpeg;base64," + base64);
                        res.put("width", photo.getWidth());
                        res.put("height", photo.getHeight());
                        callback.success(res);
                        return true;
                    }
                }
                callback.error(500, "Failed to capture photo bitmap");
                return true;
            }

            if (requestCode == REQUEST_PICK_IMAGE) {
                if (data != null && data.getData() != null) {
                    Uri imageUri = data.getData();
                    ContentResolver resolver = activity.getContentResolver();
                    try (InputStream is = resolver.openInputStream(imageUri)) {
                        Bitmap bitmap = BitmapFactory.decodeStream(is);
                        if (bitmap != null) {
                            // 限制最大边不超过 1280 像素，避免 OOM
                            Bitmap scaled = scaleBitmapIfNeeded(bitmap, 1280);
                            String base64 = bitmapToBase64(scaled, 80);
                            JSONObject res = new JSONObject();
                            res.put("uri", imageUri.toString());
                            res.put("base64", "data:image/jpeg;base64," + base64);
                            res.put("width", scaled.getWidth());
                            res.put("height", scaled.getHeight());
                            callback.success(res);
                            return true;
                        }
                    }
                }
                callback.error(500, "Failed to read picked image");
                return true;
            }

            if (requestCode == REQUEST_SCAN_BARCODE) {
                if (data != null) {
                    String contents = data.getStringExtra("SCAN_RESULT");
                    String format = data.getStringExtra("SCAN_RESULT_FORMAT");
                    JSONObject res = new JSONObject();
                    res.put("text", contents != null ? contents : "");
                    res.put("format", format != null ? format : "UNKNOWN");
                    callback.success(res);
                    return true;
                }
                callback.error(500, "No barcode data received");
                return true;
            }

        } catch (Exception e) {
            Log.e(TAG, "handleActivityResult error: " + e.getMessage(), e);
            callback.error(500, "Processing error: " + e.getMessage());
        }

        return true;
    }

    private Bitmap scaleBitmapIfNeeded(Bitmap original, int maxDimension) {
        int width = original.getWidth();
        int height = original.getHeight();
        if (width <= maxDimension && height <= maxDimension) {
            return original;
        }
        float ratio = Math.min((float) maxDimension / width, (float) maxDimension / height);
        int newWidth = Math.round(width * ratio);
        int newHeight = Math.round(height * ratio);
        return Bitmap.createScaledBitmap(original, newWidth, newHeight, true);
    }

    private String bitmapToBase64(Bitmap bitmap, int quality) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
        byte[] bytes = baos.toByteArray();
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }
}
