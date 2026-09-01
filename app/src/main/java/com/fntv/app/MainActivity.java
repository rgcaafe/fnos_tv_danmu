package com.fntv.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import com.fntv.app.api.FnApiManager;
import com.fntv.app.api.model.ApiResponse;
import com.fntv.app.api.model.LoginRequest;
import com.fntv.app.api.model.LoginResponseData;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    static {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
    }

    // HTTP 登录卡片
    private LinearLayout httpCard;
    private EditText httpHostEt, httpUserEt, httpPassEt;
    private CheckBox httpRememberCb;

    // FN ID 登录卡片
    private LinearLayout fnidCard;
    private EditText fnidHostEt, fnidUserEt, fnidPassEt;
    private CheckBox fnidRememberCb;

    private FrameLayout cardContainer;
    private Button btnLogin;
    private Button btnHttpMode, btnFnIdMode;
    private SharedPreferences prefs;
    private boolean isLoggingIn = false;
    private boolean isFnIdMode = false;
    private long loginStartTime;
    private float density;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(0xFF1A1A1A);
        }

        prefs = getSharedPreferences("fntv_prefs", MODE_PRIVATE);
        density = getResources().getDisplayMetrics().density;
        btnLogin = findViewById(R.id.btnLogin);
        LinearLayout loginCard = findViewById(R.id.loginCard);

        // 移除旧布局中的输入字段（从"服务器地址"标签到记住密码）
        int[] removeIds = {R.id.etHost, R.id.etUser, R.id.etPass, R.id.cbRemember};
        for (int i = loginCard.getChildCount() - 1; i >= 0; i--) {
            View v = loginCard.getChildAt(i);
            boolean shouldRemove = false;
            if (v instanceof EditText) {
                for (int rid : removeIds) {
                    if (v.getId() == rid) { shouldRemove = true; break; }
                }
            } else if (v instanceof RelativeLayout && v.getId() == R.id.cbRemember) {
                shouldRemove = true;
            } else if (v instanceof TextView) {
                String t = ((TextView) v).getText().toString();
                if ("服务器地址".equals(t) || "用户名".equals(t) || "密码".equals(t)) shouldRemove = true;
            }
            if (shouldRemove) loginCard.removeViewAt(i);
        }

        // ====== 创建模式切换按钮 ======
        LinearLayout modeRow = new LinearLayout(this);
        LinearLayout.LayoutParams modeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        modeLp.leftMargin = (int)(24 * density);
        modeLp.rightMargin = (int)(24 * density);
        modeRow.setLayoutParams(modeLp);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        modeRow.setGravity(android.view.Gravity.CENTER);
        modeRow.setPadding(0, 0, 0, (int)(8 * density));

        btnHttpMode = makeToggleBtn("HTTP 登录");
        btnFnIdMode = makeToggleBtn("FN ID 登录");
        modeRow.addView(btnHttpMode);
        View btnSpacer = new View(this);
        btnSpacer.setLayoutParams(new LinearLayout.LayoutParams((int)(12 * density), 1));
        modeRow.addView(btnSpacer);
        modeRow.addView(btnFnIdMode);

        // 把横线移到模式行上面
        View divider = null;
        for (int i = 0; i < loginCard.getChildCount(); i++) {
            View v = loginCard.getChildAt(i);
            if (v instanceof View && !(v instanceof TextView) && !(v instanceof EditText)
                    && !(v instanceof RelativeLayout) && !(v instanceof Button)
                    && v.getId() != android.R.id.content) {
                divider = v;
                break;
            }
        }
        if (divider != null) {
            loginCard.removeView(divider);
            // 去掉横线的上边距，紧贴 FN TV
            android.view.ViewGroup.MarginLayoutParams dlp = (android.view.ViewGroup.MarginLayoutParams) divider.getLayoutParams();
            dlp.topMargin = 0;
            divider.setLayoutParams(dlp);
            // 在 app name 后插入横线（index 2）
            loginCard.addView(divider, 2);
        }
        // 在登录按钮前插入模式行
        int btnIdx = loginCard.indexOfChild(btnLogin);
        if (btnIdx > 0) {
            loginCard.addView(modeRow, btnIdx);
        }

        // ====== 创建卡片容器 ======
        cardContainer = new FrameLayout(this);
        LinearLayout.LayoutParams ccLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ccLp.bottomMargin = (int)(16 * density);
        cardContainer.setLayoutParams(ccLp);

        // HTTP 登录卡片
        httpCard = buildInputCard(false);
        cardContainer.addView(httpCard);

        // FN ID 登录卡片（初始隐藏）
        fnidCard = buildInputCard(true);
        fnidCard.setVisibility(View.GONE);
        cardContainer.addView(fnidCard);

        // 模式按钮 ↓ 第一个输入框
        if (httpHostEt != null) btnHttpMode.setNextFocusDownId(httpHostEt.getId());
        if (fnidHostEt != null) btnFnIdMode.setNextFocusDownId(fnidHostEt.getId());

        // 把卡片容器插入到 modeRow 后面
        int modeIdx = loginCard.indexOfChild(modeRow);
        if (modeIdx >= 0) {
            loginCard.addView(cardContainer, modeIdx + 1);
        }

        // ====== 模式切换 ======
        View.OnClickListener modeSwitch = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean newMode = (v == btnFnIdMode);
                if (newMode == isFnIdMode) return;
                saveCardInput();
                isFnIdMode = newMode;
                httpCard.setVisibility(isFnIdMode ? View.GONE : View.VISIBLE);
                fnidCard.setVisibility(isFnIdMode ? View.VISIBLE : View.GONE);
                loadCardInput();
                updateModeColor();
            }
        };
        btnHttpMode.setOnClickListener(modeSwitch);
        btnFnIdMode.setOnClickListener(modeSwitch);

        // ====== 恢复数据 ======
        isFnIdMode = "fnid".equals(prefs.getString("login_mode", ""));
        loadCardInput();
        updateModeColor();
        if (isFnIdMode) {
            httpCard.setVisibility(View.GONE);
            fnidCard.setVisibility(View.VISIBLE);
        }

        // 登录按钮
        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { doLogin(); }
        });

        // 自动登录（仅冷启动时触发，手动退出登录不触发）
        boolean skipAuto = getIntent().getBooleanExtra("skip_auto_login", false);
        if (!skipAuto) {
            btnLogin.postDelayed(() -> {
                if (isFnIdMode) {
                    String id = prefs.getString("fnid_host", "");
                    String u = prefs.getString("fnid_user", "");
                    String p = prefs.getString("fnid_pass", "");
                    if (!id.isEmpty() && !u.isEmpty() && !p.isEmpty() && prefs.getBoolean("fnid_remember", false)) {
                        btnLogin.setText("自动登录中...");
                        doLogin();
                    }
                } else {
                    String h = prefs.getString("host", "");
                    String u = prefs.getString("user", "");
                    String p = prefs.getString("pass", "");
                    if (!h.isEmpty() && !u.isEmpty() && !p.isEmpty() && prefs.getBoolean("remember", false)) {
                        btnLogin.setText("自动登录中...");
                        doLoginHttp(h, u, p);
                    }
                }
            }, 300);
        }
    }

    // ==================== 卡片构建 ====================

    private Button makeToggleBtn(String text) {
        Button btn = new Button(this);
        btn.setLayoutParams(new LinearLayout.LayoutParams(0, (int)(27 * density), 1));
        btn.setBackgroundResource(R.drawable.bg_input);
        btn.setText(text);
        btn.setTextColor(0xFFEEEEEE);
        btn.setTextSize(13);
        btn.setFocusable(true);
        btn.setPadding(8, 0, 8, 0);
        return btn;
    }

    private View makeSpacer(int w) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(w, 1));
        return v;
    }

    /** 构建一个输入卡片（含标签、输入框、记住密码） */
    private LinearLayout buildInputCard(boolean isFnId) {
        LinearLayout card = new LinearLayout(this);
        card.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(0, 4, 0, 0);

        String hostHint = isFnId ? "输入 FN ID（如 a123456789）" : "例如 http://192.168.1.1:5666";
        int hostInputType = isFnId
                ? android.text.InputType.TYPE_CLASS_TEXT
                : android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI;

        int nextId = View.generateViewId();

        // FN ID / 服务器地址
        card.addView(makeLabel(isFnId ? "FN ID" : "服务器地址"));
        EditText etHost = new EditText(this);
        LinearLayout.LayoutParams hostLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int)(48 * density));
        hostLp.bottomMargin = 16;
        etHost.setLayoutParams(hostLp);
        etHost.setBackgroundResource(R.drawable.bg_input);
        etHost.setHint(hostHint);
        etHost.setInputType(hostInputType);
        etHost.setPadding((int)(12 * density), 0, (int)(12 * density), 0);
        etHost.setTextColor(0xFFEEEEEE);
        etHost.setTextSize(15);
        int hostId = View.generateViewId();
        etHost.setId(hostId);
        card.addView(etHost);

        // 用户名
        card.addView(makeLabel("用户名"));
        EditText etUser = new EditText(this);
        LinearLayout.LayoutParams userLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int)(48 * density));
        userLp.bottomMargin = (int)(16 * density);
        etUser.setLayoutParams(userLp);
        etUser.setBackgroundResource(R.drawable.bg_input);
        etUser.setHint("输入用户名");
        etUser.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        etUser.setPadding((int)(12 * density), 0, (int)(12 * density), 0);
        etUser.setTextColor(0xFFEEEEEE);
        etUser.setTextSize(15);
        int userId = View.generateViewId();
        etUser.setId(userId);
        etUser.setNextFocusUpId(hostId);
        card.addView(etUser);

        // 密码
        card.addView(makeLabel("密码"));
        EditText etPass = new EditText(this);
        LinearLayout.LayoutParams passLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int)(48 * density));
        passLp.bottomMargin = (int)(16 * density);
        etPass.setLayoutParams(passLp);
        etPass.setBackgroundResource(R.drawable.bg_input);
        etPass.setHint("输入密码");
        etPass.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etPass.setPadding((int)(12 * density), 0, (int)(12 * density), 0);
        etPass.setTextColor(0xFFEEEEEE);
        etPass.setTextSize(15);
        int passId = View.generateViewId();
        etPass.setId(passId);
        etPass.setNextFocusUpId(userId);
        card.addView(etPass);

        // 记住密码
        RelativeLayout cbWrap = new RelativeLayout(this);
        cbWrap.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int)(48 * density)));
        cbWrap.setBackgroundResource(R.drawable.bg_input);
        cbWrap.setFocusable(true);
        cbWrap.setNextFocusDownId(R.id.btnLogin);
        cbWrap.setPadding((int)(12 * density), 0, (int)(12 * density), 0);

        // AppCompatCheckBox：支持 API 19 上的 buttonTint 兼容着色
        CheckBox cb = new androidx.appcompat.widget.AppCompatCheckBox(this);
        RelativeLayout.LayoutParams cbLp = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cbLp.addRule(RelativeLayout.CENTER_VERTICAL);
        cb.setLayoutParams(cbLp);
        cb.setFocusable(false);
        cb.setText("记住密码");
        cb.setTextColor(0xFFEEEEEE);
        cb.setTextSize(14);
        cb.setId(View.generateViewId());
        // 兼容旧版本的勾选图标染蓝（替代 API 22 的 getButtonDrawable + setTint）
        androidx.core.widget.CompoundButtonCompat.setButtonTintList(
                cb, android.content.res.ColorStateList.valueOf(0xFF3370FF));
        cbWrap.addView(cb);
        cbWrap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { cb.toggle(); }
        });
        card.addView(cbWrap);

        // 保存引用
        if (isFnId) {
            fnidCard = card;
            fnidHostEt = etHost;
            fnidUserEt = etUser;
            fnidPassEt = etPass;
            fnidRememberCb = cb;
        } else {
            httpCard = card;
            httpHostEt = etHost;
            httpUserEt = etUser;
            httpPassEt = etPass;
            httpRememberCb = cb;
        }

        return card;
    }

    private TextView makeLabel(String text) {
        TextView tv = new TextView(this);
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tv.setPadding(0, 0, 0, (int)(4 * density));
        tv.setText(text);
        tv.setTextColor(0xFFB0B0B0);
        tv.setTextSize(12);
        return tv;
    }

    // ==================== 数据保存/加载 ====================

    private void saveCardInput() {
        SharedPreferences.Editor e = prefs.edit();
        if (isFnIdMode) {
            e.putString("fnid_host", fnidHostEt.getText().toString().trim());
            e.putString("fnid_user", fnidUserEt.getText().toString().trim());
            e.putBoolean("fnid_remember", fnidRememberCb.isChecked());
            String pass = fnidPassEt.getText().toString().trim();
            if (fnidRememberCb.isChecked()) e.putString("fnid_pass", pass);
            else e.remove("fnid_pass");
        } else {
            e.putString("host", httpHostEt.getText().toString().trim());
            e.putString("user", httpUserEt.getText().toString().trim());
            e.putBoolean("remember", httpRememberCb.isChecked());
            String pass = httpPassEt.getText().toString().trim();
            if (httpRememberCb.isChecked()) e.putString("pass", pass);
            else e.remove("pass");
        }
        e.apply();
    }

    private void loadCardInput() {
        if (isFnIdMode) {
            fnidHostEt.setText(prefs.getString("fnid_host", ""));
            fnidUserEt.setText(prefs.getString("fnid_user", "video"));
            boolean rem = prefs.getBoolean("fnid_remember", false);
            fnidRememberCb.setChecked(rem);
            fnidPassEt.setText(rem ? prefs.getString("fnid_pass", "") : "");
        } else {
            httpHostEt.setText(prefs.getString("host", "http://192.168.10.158:5666"));
            httpUserEt.setText(prefs.getString("user", "video"));
            boolean rem = prefs.getBoolean("remember", false);
            httpRememberCb.setChecked(rem);
            httpPassEt.setText(rem ? prefs.getString("pass", "") : "");
        }
    }

    private void updateModeColor() {
        if (isFnIdMode) {
            btnHttpMode.setTextColor(0xFF808080);
            btnFnIdMode.setTextColor(0xFFEEEEEE);
        } else {
            btnHttpMode.setTextColor(0xFFEEEEEE);
            btnFnIdMode.setTextColor(0xFF808080);
        }
    }

    // ==================== 登录逻辑 ====================

    private void doLogin() {
        if (isLoggingIn) return;

        String host, user, pass;
        if (isFnIdMode) {
            host = fnidHostEt.getText().toString().trim();
            user = fnidUserEt.getText().toString().trim();
            pass = fnidPassEt.getText().toString().trim();
        } else {
            host = httpHostEt.getText().toString().trim();
            user = httpUserEt.getText().toString().trim();
            pass = httpPassEt.getText().toString().trim();
        }

        if (host.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "所有字段都不能为空", Toast.LENGTH_SHORT).show();
            return;
        }

        isLoggingIn = true;
        btnLogin.setEnabled(false);
        btnLogin.setText("登录中...");

        if (isFnIdMode) {
            if (!FnIdLoginHelper.isFnId(host)) {
                Toast.makeText(this, "FN ID 格式不正确（6-30 位字符，不含点号和斜杠）", Toast.LENGTH_LONG).show();
                resetLoginState();
                return;
            }
            // 保存
            saveCardInput();
            new FnIdLoginHelper(this, host, user, pass, new FnIdLoginHelper.FnIdCallback() {
                @Override
                public void onSuccess(String token, String domain) {
                    onFnIdLoginSuccess(token, domain, user, pass);
                }
                @Override
                public void onError(String message) {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "FN ID 登录失败: " + message, Toast.LENGTH_LONG).show();
                        resetLoginState();
                    });
                }
            }).start();
        } else {
            btnLogin.post(() -> doLoginHttp(host, user, pass));
        }
    }

    private void onFnIdLoginSuccess(String token, String domain, String user, String pass) {
        FnApiManager.getInstance().setToken(token);
        // 保存 FN ID（用户输入的原值）到 fnid_host，domain 真实地址只存 host
        String rawFnId = fnidHostEt.getText().toString().trim();
        SharedPreferences.Editor e = prefs.edit();
        e.putString("fnid_host", rawFnId);
        e.putString("fnid_user", user);
        boolean rem = fnidRememberCb.isChecked();
        e.putBoolean("fnid_remember", rem);
        if (rem) e.putString("fnid_pass", pass);
        else e.remove("fnid_pass");
        e.putString("host", domain);
        e.putString("login_mode", "fnid");
        e.putBoolean("_was_fnid", true);
        e.apply();
        Log.i(TAG, "FN ID 登录成功！fnId=" + rawFnId + " domain=" + domain);
        Toast.makeText(this, "FN ID 登录成功！", Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this, HomeActivity.class));
        finish();
    }

    private void doLoginHttp(String host, String user, String pass) {
        loginStartTime = System.currentTimeMillis();
        if (!host.startsWith("http://") && !host.startsWith("https://")) host = "http://" + host;

        SharedPreferences.Editor e = prefs.edit();
        e.putString("host", host);
        e.putString("user", user);
        boolean rem = httpRememberCb.isChecked();
        e.putBoolean("remember", rem);
        if (rem) e.putString("pass", pass);
        else e.remove("pass");
        e.putString("login_mode", "http");
        e.apply();

        FnApiManager.getInstance().updateBaseUrl(host);
        Log.d(TAG, "尝试登录到: " + host + ", 用户: " + user);

        FnApiManager.getInstance().getApi().login(new LoginRequest(user, pass)).enqueue(new Callback<ApiResponse<LoginResponseData>>() {
            @Override
            public void onResponse(Call<ApiResponse<LoginResponseData>> call, Response<ApiResponse<LoginResponseData>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    if (response.body().code == 0) {
                        FnApiManager.getInstance().setToken(response.body().data.token);
                        Log.i(TAG, "HTTP 登录成功！耗时 " + (System.currentTimeMillis() - loginStartTime) + "ms");
                        Toast.makeText(MainActivity.this, "登录成功！", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(MainActivity.this, HomeActivity.class));
                        finish();
                        return;
                    } else {
                        Toast.makeText(MainActivity.this, "登录失败: " + response.body().msg, Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(MainActivity.this, "服务器响应异常，请重试", Toast.LENGTH_LONG).show();
                }
                resetLoginState();
            }
            @Override
            public void onFailure(Call<ApiResponse<LoginResponseData>> call, Throwable t) {
                Log.e(TAG, "网络请求失败: " + t.getMessage(), t);
                Toast.makeText(MainActivity.this, "网络连接失败: " + t.getMessage(), Toast.LENGTH_LONG).show();
                resetLoginState();
            }
        });
    }

    private void resetLoginState() {
        isLoggingIn = false;
        btnLogin.setEnabled(true);
        btnLogin.setText("登 录");
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
