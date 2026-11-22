package com.fntv.app;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import com.fntv.app.ui.CardDialog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** 升级管理器 — 检查更新、下载 APK、安装 */
public class UpdateManager {
    private boolean isTvDevice() {
        android.app.UiModeManager uiModeManager = (android.app.UiModeManager) activity.getSystemService(android.content.Context.UI_MODE_SERVICE);
        return uiModeManager != null
                && uiModeManager.getCurrentModeType() == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION;
    }


    private final Activity activity;
    private final Button btnCheckUpdate;
    private final int currentVersionCode;

    /** 更新源（按优先级） */
    private static final String[] UPDATE_URLS = {
            "https://raw.giteeusercontent.com/coffee710/fntv/raw/master/update.json",
            "https://jsd.onmicrosoft.cn/gh/rgcaafe/fnos_tv_danmu@master/update.json",
            "https://cdn.jsdelivr.net/gh/rgcaafe/fnos_tv_danmu@master/update.json",
            "https://fastly.jsdelivr.net/gh/rgcaafe/fnos_tv_danmu@master/update.json",
            "https://raw.githubusercontent.com/rgcaafe/fnos_tv_danmu/master/update.json"
    };

    private static final String TAG = "Update";

    public UpdateManager(Activity activity, Button btnCheckUpdate, int currentVersionCode) {
        this.activity = activity;
        this.btnCheckUpdate = btnCheckUpdate;
        this.currentVersionCode = currentVersionCode;
    }

    /** 初始化绑定按钮点击 */
    public void setup() {
        btnCheckUpdate.setOnClickListener(v -> checkUpdate());
    }

    private void resetBtn() {
        btnCheckUpdate.setEnabled(true);
        btnCheckUpdate.setText("检查更新");
    }

    // ========== 检查更新 ==========

    public void checkUpdate() {
        btnCheckUpdate.setEnabled(false);
        btnCheckUpdate.setText("检查中...");
        new Thread(() -> {
            try {
                org.json.JSONObject json = null;
                String usedUrl = "";
                String ts = new java.text.SimpleDateFormat("yyyyMMddHHmm", java.util.Locale.CHINA).format(new java.util.Date());
                for (String url : UPDATE_URLS) {
                    try {
                        if (!url.contains("raw.githubusercontent.com")) {
                            url = url + (url.contains("?") ? "&" : "?") + "t=" + ts;
                        }
                        Log.d(TAG, "尝试源: " + url);
                        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                        c.setConnectTimeout(8000);
                        c.setReadTimeout(8000);
                        c.setInstanceFollowRedirects(true);
                        c.connect();
                        int code = c.getResponseCode();
                        Log.d(TAG, "响应码: " + code + " 来自: " + url);
                        if (code == 200) {
                            java.io.BufferedReader r = new java.io.BufferedReader(
                                    new java.io.InputStreamReader(c.getInputStream(), "UTF-8"));
                            StringBuilder sb = new StringBuilder();
                            String l;
                            while ((l = r.readLine()) != null) sb.append(l);
                            r.close();
                            json = new org.json.JSONObject(sb.toString());
                            usedUrl = url;
                            break;
                        } else {
                            try {
                                java.io.BufferedReader er = new java.io.BufferedReader(
                                        new java.io.InputStreamReader(c.getErrorStream(), "UTF-8"));
                                StringBuilder eb = new StringBuilder();
                                String el;
                                while ((el = er.readLine()) != null) eb.append(el);
                                er.close();
                                Log.w(TAG, "错误响应: " + eb);
                            } catch (Exception ignored) {
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "源 \"" + url + "\" 失败: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                    }
                }

                if (json == null) {
                    activity.runOnUiThread(() -> {
                        Toast.makeText(activity, "检查更新失败，无法连接更新服务器", Toast.LENGTH_LONG).show();
                        resetBtn();
                    });
                    return;
                }

                int remoteVersion = json.optInt("versionCode", 0);
                final String versionName = json.optString("versionName", "");
                final String apkUrl = json.optString("apkUrl", "");
                final String changelog = json.optString("changelog", "暂无更新说明");
                final boolean forceUpdate = json.optBoolean("forceUpdate", false);

                Log.d(TAG, "远程: v" + remoteVersion + " 本地: v" + currentVersionCode + " 来源: " + usedUrl);

                final boolean isTestInstall = BuildConfig.DEBUG && remoteVersion <= currentVersionCode;
                if (remoteVersion <= currentVersionCode && !isTestInstall) {
                    activity.runOnUiThread(() -> {
                        Toast.makeText(activity, "已是最新版本", Toast.LENGTH_SHORT).show();
                        resetBtn();
                    });
                    return;
                }

                final int fRemoteVersion = remoteVersion;
                final String label = isTestInstall ? "测试安装（版本号不高于当前）" : "发现新版本 v" + versionName;
                activity.runOnUiThread(() ->
                        showUpdateDialog(label, changelog, forceUpdate, apkUrl, fRemoteVersion, isTestInstall));

            } catch (Exception e) {
                Log.e(TAG, "检查更新异常", e);
                activity.runOnUiThread(() -> {
                    Toast.makeText(activity, "检查更新失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    resetBtn();
                });
            }
        }).start();
    }

    // ========== 更新弹窗 ==========

    private void showUpdateDialog(final String title, final String changelog,
                                   final boolean forceUpdate, final String apkUrl,
                                   final int remoteVersion, final boolean isTestInstall) {
        CardDialog.message(activity, title, changelog,
                isTestInstall ? "测试安装器" : "立即更新",
                () -> downloadAndInstall(apkUrl, remoteVersion, isTestInstall),
                forceUpdate ? "退出应用" : "稍后再说",
                () -> {
                    if (forceUpdate) {
                        activity.finishAffinity();
                    } else {
                        resetBtn();
                    }
                },
                !forceUpdate);
    }

    // ========== 下载 ==========

    private void downloadAndInstall(final String apkUrl, final int remoteVersion, final boolean isTestInstall) {
        btnCheckUpdate.setText(isTestInstall ? "测试中..." : "下载中...");
        btnCheckUpdate.setEnabled(false);
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(apkUrl).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(30000);
                c.setInstanceFollowRedirects(true);
                c.connect();
                int respCode = c.getResponseCode();
                if (respCode != 200) {
                    activity.runOnUiThread(() -> {
                        Toast.makeText(activity, "下载失败，服务器返回 " + respCode, Toast.LENGTH_LONG).show();
                        resetBtn();
                    });
                    return;
                }

                File dir = new File(activity.getExternalFilesDir(null), "download");
                if (!dir.exists()) dir.mkdirs();
                final File apkFile = new File(dir, "FNTV_v" + remoteVersion + ".apk");

                InputStream is = c.getInputStream();
                FileOutputStream fos = new FileOutputStream(apkFile);
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) fos.write(buf, 0, n);
                fos.close();
                is.close();

                Log.d(TAG, "下载完成: " + apkFile.getAbsolutePath() + " (" + apkFile.length() + " bytes)");

                // 检查 APK 文件头
                java.io.RandomAccessFile raf = new java.io.RandomAccessFile(apkFile, "r");
                byte[] header = new byte[4];
                raf.read(header);
                raf.close();
                String headerStr = new String(header, "UTF-8");
                Log.d(TAG, "APK 文件头: " + headerStr + " (" + bytesToHex(header) + ")");
                if (!"PK".equals(headerStr.substring(0, 2))) {
                    activity.runOnUiThread(() -> {
                        Toast.makeText(activity, "下载文件不是有效的 APK（文件头异常）", Toast.LENGTH_LONG).show();
                        resetBtn();
                    });
                    return;
                }

                // 签名校验
                if (!verifyApkSignature(apkFile)) {
                    activity.runOnUiThread(() -> {
                        Toast.makeText(activity, "APK 签名校验失败，可能已被篡改", Toast.LENGTH_LONG).show();
                        resetBtn();
                    });
                    return;
                }

                activity.runOnUiThread(() -> installApk(apkFile, isTestInstall));

            } catch (Exception e) {
                Log.e(TAG, "下载失败", e);
                activity.runOnUiThread(() -> {
                    Toast.makeText(activity, "下载失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    resetBtn();
                });
            }
        }).start();
    }

    // ========== APK 安装（四级降级） ==========

    private void installApk(File apkFile, boolean testOnly) {
        try {
            // 1. ACTION_INSTALL_PACKAGE（Android 8+，弹出系统安装界面）
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                try {
                    if (!activity.getPackageManager().canRequestPackageInstalls()) {
                        Intent permIntent = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                        permIntent.setData(android.net.Uri.parse("package:" + activity.getPackageName()));
                        activity.startActivity(permIntent);
                        Toast.makeText(activity, "请允许安装未知来源应用后重试", Toast.LENGTH_LONG).show();
                        resetBtn();
                        return;
                    }
                    android.net.Uri apkUri = androidx.core.content.FileProvider.getUriForFile(
                            activity, activity.getPackageName() + ".fileprovider", apkFile);
                    Intent install = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                    install.setDataAndType(apkUri, "application/vnd.android.package-archive");
                    install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    activity.startActivity(install);
                    if (testOnly) {
                        Toast.makeText(activity, "测试成功：ACTION_INSTALL_PACKAGE 可用", Toast.LENGTH_LONG).show();
                        resetBtn();
                    }
                    return;
                } catch (android.content.ActivityNotFoundException e2) {
                    String errSuffix2 = testOnly ? "（测试结束）" : "，尝试 ACTION_VIEW";
                    Log.w(TAG, "ACTION_INSTALL_PACKAGE 不可用" + errSuffix2);
                    if (testOnly) {
                        Toast.makeText(activity, "测试：ACTION_INSTALL_PACKAGE 不可用", Toast.LENGTH_LONG).show();
                        resetBtn();
                        return;
                    }
                }
            }

            // 3. ACTION_VIEW
            try {
                Intent install = new Intent(Intent.ACTION_VIEW);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    android.net.Uri apkUri = androidx.core.content.FileProvider.getUriForFile(
                            activity, activity.getPackageName() + ".fileprovider", apkFile);
                    install.setDataAndType(apkUri, "application/vnd.android.package-archive");
                    install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } else {
                    install.setDataAndType(android.net.Uri.fromFile(apkFile), "application/vnd.android.package-archive");
                }
                install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(install);
                if (testOnly) {
                    Toast.makeText(activity, "测试成功：ACTION_VIEW 可用", Toast.LENGTH_LONG).show();
                    resetBtn();
                }
                return;
            } catch (android.content.ActivityNotFoundException e3) {
                String errSuffix3 = testOnly ? "（测试结束）" : "";
                Log.w(TAG, "ACTION_VIEW 不可用" + errSuffix3);
                if (testOnly) {
                    Toast.makeText(activity, "测试：所有安装方式均不可用", Toast.LENGTH_LONG).show();
                    resetBtn();
                    return;
                }
            }

            // 4. 兜底：复制到下载目录（仅非测试模式）
            copyApkToDownloads(apkFile, null);
        } catch (Exception e) {
            Log.e(TAG, "安装失败", e);
            if (testOnly) {
                Toast.makeText(activity, "测试异常：" + e.getMessage(), Toast.LENGTH_LONG).show();
                resetBtn();
            } else {
                copyApkToDownloads(apkFile, e);
            }
        }
    }

    private void copyApkToDownloads(File apkFile, Exception originalError) {
        try {
            File downloadDir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS);
            if (!downloadDir.exists()) downloadDir.mkdirs();
            File targetFile = new File(downloadDir, "FNTV_update_" + apkFile.getName());
            try (FileInputStream fis = new FileInputStream(apkFile);
                 FileOutputStream fos = new FileOutputStream(targetFile)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = fis.read(buf)) != -1) fos.write(buf, 0, n);
            }
            final String msg = "安装器不可用，APK 已复制到：\n" + targetFile.getAbsolutePath()
                    + "\n请用文件管理器打开此文件进行安装。";
            Log.w(TAG, msg);
            activity.runOnUiThread(() -> CardDialog.message(activity, "安装失败", msg,
                    "我知道了", this::resetBtn, null, null));
        } catch (Exception copyErr) {
            Log.e(TAG, "复制失败", copyErr);
            activity.runOnUiThread(() -> {
                String errMsg = originalError != null ? "安装失败: " + originalError.getMessage() : "安装器不可用";
                CardDialog.message(activity, "安装失败",
                        errMsg + "\nAPK 位置：\n" + apkFile.getAbsolutePath(),
                        "我知道了", this::resetBtn, null, null);
            });
        }
    }

    // ========== 签名校验 ==========

    private boolean verifyApkSignature(File apkFile) {
        try {
            android.content.pm.PackageManager pm = activity.getPackageManager();
            android.content.pm.PackageInfo currentInfo;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                currentInfo = pm.getPackageInfo(activity.getPackageName(),
                        android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES);
                if (currentInfo.signingInfo == null) return false;
                android.content.pm.Signature[] sigs = currentInfo.signingInfo.getApkContentsSigners();
                if (sigs == null || sigs.length == 0) return false;
                String currentSig = android.util.Base64.encodeToString(sigs[0].toByteArray(), android.util.Base64.NO_WRAP);
                Log.d(TAG, "当前签名: " + currentSig.substring(0, Math.min(20, currentSig.length())) + "...");
                return true;
            } else {
                currentInfo = pm.getPackageInfo(activity.getPackageName(), android.content.pm.PackageManager.GET_SIGNATURES);
                return currentInfo.signatures != null && currentInfo.signatures.length > 0;
            }
        } catch (Exception e) {
            Log.e(TAG, "签名校验异常", e);
            return false;
        }
    }

    // ========== 工具 ==========

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02X ", b & 0xFF));
        return sb.toString().trim();
    }
}
