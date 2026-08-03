package com.fntv.app;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.view.ViewGroup;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.fntv.app.api.FnApiManager;
import com.fntv.app.api.model.ApiResponse;
import com.fntv.app.api.model.FnAuthResponse;
import com.fntv.app.api.model.LoginRequest;
import com.fntv.app.api.model.LoginResponseData;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** FN ID OAuth 登录辅助类 — 通过 WebView 走 5ddd.com OAuth 流程 */
public class FnIdLoginHelper {

    private static final String TAG = "FnIdLogin";
    private static final String FN_CONNECT_HOST = "5ddd.com";
    private static final long TIMEOUT_MS = 120000; // 2 分钟超时

    private final Activity activity;
    private final String fnId;
    private final String username;
    private final String password;
    private final FnIdCallback callback;
    private WebView webView;
    private android.app.Dialog dialog;
    private Handler handler;
    private boolean codeCaptured = false;
    private boolean timeoutReached = false;
    private String capturedCookie = "";
    private String baseUrl = "";
    private boolean sysConfigLoaded = false;

    public interface FnIdCallback {
        void onSuccess(String token, String domain);
        void onError(String message);
    }

    public FnIdLoginHelper(Activity activity, String fnId, String username, String password, FnIdCallback callback) {
        this.activity = activity;
        this.fnId = fnId;
        this.username = username;
        this.password = password;
        this.callback = callback;
        this.handler = new Handler(Looper.getMainLooper());
    }

    /** 检测输入是否为 FN ID 格式 */
    public static boolean isFnId(String input) {
        if (input == null || input.isEmpty()) return false;
        String trimmed = input.trim();
        return !trimmed.contains(".") && !trimmed.contains(":") && !trimmed.contains("/")
                && trimmed.length() >= 6 && trimmed.length() <= 30;
    }

    /** 启动 FN ID OAuth 登录 */
    public void start() {
        activity.runOnUiThread(() -> {
            createWebView();
            showDialog();
            setupCookie();
            loadFnConnectUrl();
            startTimeout();
        });
    }

    private void createWebView() {
        webView = new WebView(activity);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setUserAgentString("Mozilla/5.0 (Linux; Android 4.4.2; Android TV) AppleWebKit/537.36"
                + " (KHTML, like Gecko) Version/4.0 Chrome/87.0.4280.141 Safari/537.36");
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setUseWideViewPort(true);
        webView.addJavascriptInterface(new Bridge(), "fntvBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(android.webkit.WebView view, String url, android.graphics.Bitmap favicon) {
                Log.d(TAG, "加载: " + url);
                // 检测用户已登录到 NAS 首页，尝试从 Cookie 提取 token
                if (!codeCaptured && !url.contains("5ddd") && !url.contains(FN_CONNECT_HOST)
                        && (url.endsWith("/") || url.contains("/v/") || url.endsWith("/login"))) {
                    String nasBase = extractBaseUrl(url);
                    if (!nasBase.isEmpty() && !nasBase.contains(FN_CONNECT_HOST) && baseUrl.isEmpty()) {
                        baseUrl = nasBase;
                        String cookies = CookieManager.getInstance().getCookie(nasBase);
                        Log.d(TAG, "NAS cookies: " + (cookies != null ? cookies.substring(0, Math.min(100, cookies.length())) : "null"));
                        // 有 Cookie 说明用户已登录网页，直接用 API 登录获取专用 token
                        codeCaptured = true;
                        cancelTimeout();
                        view.stopLoading();
                        Log.d(TAG, "检测到已登录 NAS: " + nasBase + "，调 login API");
                        dismissDialog();
                        doApiLogin(nasBase);
                        return;
                    }
                }
                // 检测 OAuth 回调 URL 中的授权码
                if (!codeCaptured && url.contains("code=")) {
                    try {
                        String code = extractQueryParam(url, "code");
                        if (code != null && !code.isEmpty()) {
                            Log.d(TAG, "从 URL 捕获授权码: " + code);
                            // 从 URL 提取 baseUrl
                            String cbUrl = extractBaseUrl(url);
                            if (!cbUrl.isEmpty()) baseUrl = cbUrl;
                            handleOAuthCode(code);
                            view.stopLoading();
                            return;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "解析授权码失败", e);
                    }
                }
                // 检测 XHR 拦截未捕获到时的备用方案：从任何包含 code 的页面 URL 尝试
                if (!codeCaptured && url.contains("/oauthapi/authorize")) {
                    try {
                        // 可能 code 在 POST body 里，但可以通过页面标题或 JS 获取
                        // 先记录 URL 备用
                        Log.d(TAG, "检测到 oauth 授权页: " + url);
                    } catch (Exception ignored) {}
                }
            }

            @Override
            public void onPageFinished(android.webkit.WebView view, String url) {
                Log.d(TAG, "完成: " + url);
                injectScript();
            }
        });
    }

    /** 设置 mode=relay Cookie */
    private void setupCookie() {
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setCookie("https://" + FN_CONNECT_HOST, "mode=relay; path=/");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            cm.flush();
        }
    }

    private void loadFnConnectUrl() {
        String url = "https://" + FN_CONNECT_HOST + "/" + fnId.trim();
        Log.d(TAG, "加载 FN Connect URL: " + url);
        webView.loadUrl(url);
    }

    /** 注入 JS 拦截脚本（同桌面版逻辑） */
    private void injectScript() {
        String escapedUser = JSONString(username);
        String escapedPass = JSONString(password);
        String js = getInjectionScript(escapedUser, escapedPass);
        webView.evaluateJavascript(js, null);
    }

    private String getInjectionScript(String jsonUser, String jsonPass) {
        return "(function() {"
                + "var AUTO_LOGIN_USER = " + jsonUser + ";"
                + "var AUTO_LOGIN_PASS = " + jsonPass + ";"

                + "function postMessage(payload) {"
                + "  payload.cookie = document.cookie;"
                + "  try { fntvBridge.onMessage(JSON.stringify(payload)); } catch(e) {}"
                + "}"

                + "function triggerInput(input, value) {"
                + "  var setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;"
                + "  setter.call(input, value);"
                + "  input.dispatchEvent(new Event('input', { bubbles: true }));"
                + "  input.dispatchEvent(new Event('change', { bubbles: true }));"
                + "}"

                // 自动填充登录
                + "if (window.location.href.indexOf('/login') !== -1) {"
                + "  setTimeout(function() {"
                + "    var u = document.getElementById('username');"
                + "    var p = document.getElementById('password');"
                + "    if (u && AUTO_LOGIN_USER) { triggerInput(u, AUTO_LOGIN_USER); }"
                + "    if (p && AUTO_LOGIN_PASS) { triggerInput(p, AUTO_LOGIN_PASS); }"
                + "    setTimeout(function() {"
                + "      var btn = document.querySelector('button[type=\"submit\"]');"
                + "      if (btn) btn.click();"
                + "    }, 200);"
                + "  }, 200);"
                + "}"

                // 自动授权
                + "if (window.location.href.indexOf('/signin') !== -1) {"
                + "  setTimeout(function() {"
                + "    var btns = document.querySelectorAll('button');"
                + "    for (var i = 0; i < btns.length; i++) {"
                + "      if (btns[i].innerText.indexOf('授权') !== -1) { btns[i].click(); break; }"
                + "    }"
                + "  }, 200);"
                + "}"

                // Hook XHR 拦截 /oauthapi/authorize 响应获取 code
                + "var origOpen = XMLHttpRequest.prototype.open;"
                + "XMLHttpRequest.prototype.open = function(m, u) { this._url = u; return origOpen.apply(this, arguments); };"
                + "var origSend = XMLHttpRequest.prototype.send;"
                + "XMLHttpRequest.prototype.send = function(b) {"
                + "  var self = this;"
                + "  var origReady = self.onreadystatechange;"
                + "  self.onreadystatechange = function() {"
                + "    if (self.readyState === 4 && self._url && self._url.indexOf('/oauthapi/authorize') !== -1) {"
                + "      try {"
                + "        var json = JSON.parse(self.responseText || '{}');"
                + "        var code = json && json.data ? json.data.code : null;"
                + "        if (code) { postMessage({ type:'Response', url:self._url, code:String(code) }); }"
                + "      } catch(e) {}"
                + "    }"
                + "    if (origReady) origReady.apply(self, arguments);"
                + "  };"
                + "  return origSend.apply(this, arguments);"
                + "};"

                // Hook Fetch 拦截 /oauthapi/authorize
                + "var origFetch = window.fetch;"
                + "window.fetch = function(input, init) {"
                + "  var url = (typeof input === 'object' && input.url) ? input.url : input;"
                + "  return origFetch.apply(this, arguments).then(function(r) {"
                + "    if (url && url.indexOf('/oauthapi/authorize') !== -1) {"
                + "      r.clone().text().then(function(text) {"
                + "        try { var json = JSON.parse(text); var code = json && json.data ? json.data.code : null;"
                + "          if (code) { postMessage({ type:'Response', url:url, code:String(code) }); }"
                + "        } catch(e) {}"
                + "      });"
                + "    }"
                + "    return r;"
                + "  });"
                + "};"

                // 获取 sys_config（非 /login 页面）
                + "setTimeout(function() {"
                + "  if (window.location.href.indexOf('/login') === -1) {"
                + "    fetch('/v/api/v1/sys/config', { credentials:'include' })"
                + "      .then(function(r) { return r.text(); })"
                + "      .then(function(text) {"
                + "        postMessage({ type:'SysConfig', url:'/v/api/v1/sys/config', body:text, pageUrl:String(window.location.href) });"
                + "      }).catch(function() {});"
                + "  }"
                + "}, 800);"

                + "})();";
    }

    /** 转义字符串为 JS 安全格式 */
    private static String JSONString(String s) {
        if (s == null) return "\"\"";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }

    /** JS ↔ Java 桥接 */
    public class Bridge {
        @JavascriptInterface
        public void onMessage(final String json) {
            // @JavascriptInterface 运行在 JavaBridge 线程，WebView 操作必须切到主线程
            handler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        org.json.JSONObject msg = new org.json.JSONObject(json);
                        final String type = msg.optString("type", "");
                        final String url = msg.optString("url", "");

                        Log.d(TAG, "Bridge 消息: type=" + type + " url=" + url);

                        if ("Response".equals(type) && url.contains("/oauthapi/authorize")) {
                            String code = msg.optString("code", "");
                            if (code.isEmpty()) {
                                try {
                                    String body = msg.optString("body", "{}");
                                    code = new org.json.JSONObject(body).optJSONObject("data").optString("code", "");
                                } catch (Exception ignored) {}
                            }
                            if (!code.isEmpty()) handleOAuthCode(code);
                        } else if ("SysConfig".equals(type)) {
                            handleSysConfig(msg);
                        } else if (url.contains("/sac/rpcproxy/v1/new-user-guide/status")) {
                            String cookie = msg.optString("cookie", "");
                            if (!cookie.isEmpty()) {
                                capturedCookie = cookie;
                                fetchSysConfigViaApi();
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Bridge 消息解析失败", e);
                    }
                }
            });
        }
    }

    /** 从 Cookie 字符串提取指定名称的值 */
    private String extractCookie(String cookies, String name) {
        if (cookies == null || name == null) return null;
        String[] parts = cookies.split(";");
        for (String part : parts) {
            part = part.trim();
            if (part.startsWith(name + "=")) {
                return part.substring((name + "=").length());
            }
        }
        return null;
    }

    /** 从 URL 查询参数取值 */
    private String extractQueryParam(String url, String param) {
        try {
            String query = new java.net.URL(url).getQuery();
            if (query == null) return null;
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2 && kv[0].equals(param)) {
                    return java.net.URLDecoder.decode(kv[1], "UTF-8");
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** 处理 OAuth 授权码（来自 URL 拦截或 JS Bridge） */
    private void handleOAuthCode(String code) {
        if (codeCaptured) return;
        codeCaptured = true;
        Log.d(TAG, "获取到授权码: " + code);
        exchangeCodeForToken(code);
    }

    /** 通过 code 换取 token */
    private void exchangeCodeForToken(final String code) {
        if (baseUrl.isEmpty()) {
            Log.e(TAG, "baseUrl 为空，无法换取 token");
            return;
        }

        Map<String, String> body = new HashMap<>();
        body.put("source", "Trim-NAS");
        body.put("code", code);

        Log.d(TAG, "换取 token: " + baseUrl + "/v/api/v1/auth");

        FnApiManager.getInstance().updateBaseUrl(baseUrl);
        FnApiManager.getInstance().getApi().auth(body).enqueue(new Callback<ApiResponse<FnAuthResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<FnAuthResponse>> call,
                                   Response<ApiResponse<FnAuthResponse>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().code == 0
                        && response.body().data != null && response.body().data.token != null) {
                    final String token = response.body().data.token;
                    Log.d(TAG, "获取 token 成功");
                    handler.post(() -> {
                        dismissDialog();
                        cancelTimeout();
                        callback.onSuccess(token, baseUrl);
                    });
                } else {
                    String msg = response.body() != null ? response.body().msg : "未知错误";
                    Log.e(TAG, "换取 token 失败: " + msg);
                    handler.post(() -> {
                        dismissDialog();
                        callback.onError("授权失败: " + msg);
                    });
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<FnAuthResponse>> call, Throwable t) {
                Log.e(TAG, "换取 token 网络失败", t);
                handler.post(() -> {
                    dismissDialog();
                    callback.onError("网络错误: " + t.getMessage());
                });
            }
        });
    }

    /** 处理 SysConfig 响应（从 WebView fetch） */
    private void handleSysConfig(org.json.JSONObject msg) {
        if (sysConfigLoaded) return;
        try {
            String body = msg.optString("body", "");
            if (body.isEmpty()) return;
            org.json.JSONObject json = new org.json.JSONObject(body);
            org.json.JSONObject data = json.optJSONObject("data");
            if (data == null) return;
            org.json.JSONObject oauth = data.optJSONObject("nas_oauth");
            if (oauth == null) return;
            String appId = oauth.optString("app_id", "");
            String oauthUrl = oauth.optString("url", "");

            if (appId.isEmpty()) return;

            String targetBaseUrl = (oauthUrl != null && !oauthUrl.isEmpty() && !oauthUrl.equals("://"))
                    ? oauthUrl : extractBaseUrl(msg.optString("pageUrl", ""));
            if (targetBaseUrl.isEmpty()) return;

            sysConfigLoaded = true;
            baseUrl = targetBaseUrl;

            String redirectUri = targetBaseUrl + "/v/oauth/result";
            String targetUrl = targetBaseUrl + "/signin?client_id=" + appId
                    + "&redirect_uri=" + android.net.Uri.encode(redirectUri);

            Log.d(TAG, "跳转到 OAuth 授权页: " + targetUrl);
            webView.loadUrl(targetUrl);

        } catch (Exception e) {
            Log.e(TAG, "处理 SysConfig 失败", e);
        }
    }

    /** 通过 API 获取 SysConfig（备用方案） */
    private void fetchSysConfigViaApi() {
        if (sysConfigLoaded || baseUrl.isEmpty()) return;

        final String currentUrl = webView.getUrl();
        if (currentUrl == null) return;

        final String apiBaseUrl = extractBaseUrl(currentUrl);
        if (apiBaseUrl.isEmpty()) return;

        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL(apiBaseUrl + "/v/api/v1/sys/config");
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) url.openConnection();
                c.setRequestMethod("GET");
                c.setConnectTimeout(10000);
                c.setReadTimeout(10000);
                c.setRequestProperty("Cookie", capturedCookie + "; mode=relay");
                c.connect();

                if (c.getResponseCode() == 200) {
                    java.io.BufferedReader r = new java.io.BufferedReader(
                            new java.io.InputStreamReader(c.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String l;
                    while ((l = r.readLine()) != null) sb.append(l);
                    r.close();

                    final String respBody = sb.toString();
                    handler.post(() -> {
                        try {
                            org.json.JSONObject json = new org.json.JSONObject(respBody);
                            org.json.JSONObject data = json.optJSONObject("data");
                            if (data == null) return;
                            org.json.JSONObject oauth = data.optJSONObject("nas_oauth");
                            if (oauth == null) return;
                            String appId = oauth.optString("app_id", "");
                            String oauthUrl = oauth.optString("url", "");

                            if (appId.isEmpty()) return;

                            String targetBaseUrl = (oauthUrl != null && !oauthUrl.isEmpty() && !oauthUrl.equals("://"))
                                    ? oauthUrl : apiBaseUrl;
                            sysConfigLoaded = true;
                            baseUrl = targetBaseUrl;

                            String redirectUri = targetBaseUrl + "/v/oauth/result";
                            String targetUrl = targetBaseUrl + "/signin?client_id=" + appId
                                    + "&redirect_uri=" + android.net.Uri.encode(redirectUri);

                            // 转发 Cookie
                            if (!capturedCookie.isEmpty()) {
                                String domain = targetBaseUrl.replaceFirst("^https?://", "").split("[:/]")[0];
                                android.webkit.CookieManager cm = android.webkit.CookieManager.getInstance();
                                for (String ck : capturedCookie.split(";")) {
                                    String[] parts = ck.trim().split("=", 2);
                                    if (parts.length >= 2) {
                                        cm.setCookie(targetBaseUrl, parts[0].trim() + "=" + parts[1].trim() + "; path=/");
                                    }
                                }
                                cm.setCookie(targetBaseUrl, "mode=relay; path=/");
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) cm.flush();
                            }

                            webView.loadUrl(targetUrl);
                        } catch (Exception e) {
                            Log.e(TAG, "API SysConfig 解析失败", e);
                        }
                    });
                }
                c.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "API SysConfig 请求失败", e);
            }
        }).start();
    }

    /** 从 URL 提取 baseUrl（protocol + host） */
    private static String extractBaseUrl(String url) {
        if (url == null || url.isEmpty()) return "";
        try {
            java.net.URL parsed = new java.net.URL(url);
            return parsed.getProtocol() + "://" + parsed.getHost()
                    + (parsed.getPort() > 0 && parsed.getPort() != 80 && parsed.getPort() != 443
                    ? ":" + parsed.getPort() : "");
        } catch (Exception e) {
            return "";
        }
    }

    /** 显示 WebView 对话框 */
    private void showDialog() {
        FrameLayout container = new FrameLayout(activity);
        container.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        container.addView(webView);

        // 全屏 Dialog
        dialog = new android.app.Dialog(activity, android.R.style.Theme_DeviceDefault_NoActionBar);
        dialog.setContentView(container);
        dialog.setCancelable(false);
        // 按 BACK 取消登录
        dialog.setOnKeyListener(new android.content.DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(android.content.DialogInterface d, int keyCode, android.view.KeyEvent event) {
                if (keyCode == android.view.KeyEvent.KEYCODE_BACK && event.getAction() == android.view.KeyEvent.ACTION_UP) {
                    cancelTimeout();
                    if (webView != null) webView.destroy();
                    callback.onError("用户取消了登录");
                    dialog.dismiss();
                    return true;
                }
                return false;
            }
        });
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            android.view.WindowManager.LayoutParams wlp = window.getAttributes();
            wlp.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
            wlp.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
            window.setAttributes(wlp);
        }
        dialog.show();
    }

    private void dismissDialog() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
    }

    /** 获取到 NAS 地址后，调标准 login API */
    private void doApiLogin(final String nasBase) {
        FnApiManager.getInstance().updateBaseUrl(nasBase);
        FnApiManager.getInstance().getApi().login(new LoginRequest(username, password))
                .enqueue(new Callback<ApiResponse<LoginResponseData>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<LoginResponseData>> call,
                                           Response<ApiResponse<LoginResponseData>> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().code == 0
                                && response.body().data != null && response.body().data.token != null) {
                            final String token = response.body().data.token;
                            Log.d(TAG, "API 登录成功！");
                            handler.post(() -> callback.onSuccess(token, nasBase));
                        } else {
                            String msg = response.body() != null ? response.body().msg : "未知错误";
                            Log.e(TAG, "API 登录失败: " + msg);
                            handler.post(() -> callback.onError("登录失败: " + msg));
                        }
                    }
                    @Override
                    public void onFailure(Call<ApiResponse<LoginResponseData>> call, Throwable t) {
                        Log.e(TAG, "API 登录网络失败", t);
                        handler.post(() -> callback.onError("网络错误: " + t.getMessage()));
                    }
                });
    }

    /** 超时处理 */
    private void startTimeout() {
        handler.postDelayed(() -> {
            if (!codeCaptured) {
                timeoutReached = true;
                dismissDialog();
                callback.onError("FN ID 登录超时，请检查网络和 FN ID 是否正确");
            }
        }, TIMEOUT_MS);
    }

    private void cancelTimeout() {
        handler.removeCallbacksAndMessages(null);
    }
}
