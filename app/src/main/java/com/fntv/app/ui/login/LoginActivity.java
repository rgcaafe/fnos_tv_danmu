package com.fntv.app.ui.login;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.fntv.app.BuildConfig;
import com.fntv.app.FnIdLoginHelper;
import com.fntv.app.R;
import com.fntv.app.api.FnApiManager;
import com.fntv.app.api.model.ApiResponse;
import com.fntv.app.api.model.UserInfoResponse;
import com.fntv.app.core.Prefs;
import com.fntv.app.core.ThemeMode;
import com.fntv.app.data.AuthRepository;
import com.fntv.app.ui.AppearanceDialog;
import com.fntv.app.ui.Ui;
import com.fntv.app.ui.home.HomeActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 登录页：服务器账号 / FN ID，Apple 风格表单。
 */
public class LoginActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private FrameLayout cardContainer;
    private LinearLayout httpCard, fnidCard, loginCard;
    private View formCard;
    private Button btnLogin, btnHttpMode, btnFnIdMode, btnAppearance;

    private EditText httpHost, httpUser, httpPass;
    private CheckBox httpRemember;
    private EditText fnidHost, fnidUser, fnidPass;
    private CheckBox fnidRemember;

    private boolean isFnIdMode = false;
    private boolean isLoggingIn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeMode.apply(this);
        setContentView(R.layout.activity_main);
        Ui.applyBars(this);

        prefs = Prefs.get(this);

        loginCard = findViewById(R.id.loginCard);
        cardContainer = findViewById(R.id.cardContainer);
        btnLogin = findViewById(R.id.btnLogin);
        btnHttpMode = findViewById(R.id.btnHttpMode);
        btnFnIdMode = findViewById(R.id.btnFnIdMode);
        btnAppearance = findViewById(R.id.btnAppearance);
        formCard = (View) cardContainer.getParent();

        TextView version = findViewById(R.id.tvVersion);
        version.setText("FN TV · v" + BuildConfig.VERSION_NAME);

        int maxWidth = Ui.dimen(this, R.dimen.login_card_max_width);
        int available = Ui.screenWidth(this) - Ui.dp(this, 44);
        if (!Ui.isLandscape(this) && available > maxWidth) {
            ViewGroup.LayoutParams lp = loginCard.getLayoutParams();
            lp.width = maxWidth;
            loginCard.setLayoutParams(lp);
        }

        httpCard = buildCard(false);
        cardContainer.addView(httpCard);
        fnidCard = buildCard(true);
        fnidCard.setVisibility(View.GONE);
        cardContainer.addView(fnidCard);

        btnHttpMode.setNextFocusDownId(httpHost.getId());
        btnFnIdMode.setNextFocusDownId(fnidHost.getId());

        View.OnClickListener modeSwitch = v -> {
            boolean newMode = v == btnFnIdMode;
            if (newMode == isFnIdMode) return;
            saveInputs();
            isFnIdMode = newMode;
            httpCard.setVisibility(isFnIdMode ? View.GONE : View.VISIBLE);
            fnidCard.setVisibility(isFnIdMode ? View.VISIBLE : View.GONE);
            loadInputs();
            updateMode();
        };
        btnHttpMode.setOnClickListener(modeSwitch);
        btnFnIdMode.setOnClickListener(modeSwitch);

        isFnIdMode = "fnid".equals(Prefs.str(this, Prefs.LOGIN_MODE, ""));
        loadInputs();
        updateMode();
        if (isFnIdMode) {
            httpCard.setVisibility(View.GONE);
            fnidCard.setVisibility(View.VISIBLE);
        }

        // 有 token 时直接用令牌恢复会话，恢复期间不显示登录表单，避免看起来像"每次都要登录"
        if (!Prefs.str(this, Prefs.HOST, "").isEmpty()
                && !Prefs.str(this, Prefs.TOKEN, "").isEmpty()) {
            formCard.setVisibility(View.GONE);
            btnLogin.setVisibility(View.GONE);
        }

        btnLogin.setOnClickListener(v -> doLogin());
        btnAppearance.setOnClickListener(v -> {
            saveInputs();
            AppearanceDialog.show(this, this::refreshAppearanceLabel);
        });
        refreshAppearanceLabel();

        boolean skipAuto = getIntent().getBooleanExtra("skip_auto_login", false);
        if (!skipAuto) {
            btnLogin.postDelayed(this::autoLogin, 350);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAppearanceLabel();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 旋转会重建 Activity（这样能换用横/竖两套布局），先把输入保存下来
        if (httpHost != null && fnidHost != null) {
            saveInputs();
        }
    }

    /** 外观按钮上显示当前模式（跟随系统 / 浅色 / 深色） */
    private void refreshAppearanceLabel() {
        if (btnAppearance != null) {
            btnAppearance.setText(ThemeMode.label(this, ThemeMode.current(this)));
        }
    }

    // ==================== 表单 ====================

    private LinearLayout buildCard(boolean fnId) {
        final boolean land = Ui.isLandscape(this);
        LinearLayout card = new LinearLayout(this);
        card.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(0, Ui.dp(this, land ? 0 : 8), 0, 0);

        if (!land) card.addView(label(fnId ? "FN ID" : "服务器地址"));
        EditText host = input(fnId ? "例如 a123456789" : "服务器地址，例如 192.168.1.10:5666",
                fnId ? InputType.TYPE_CLASS_TEXT
                        : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        host.setId(View.generateViewId());
        card.addView(host);

        if (!land) card.addView(label("用户名"));
        EditText user = input("用户名", InputType.TYPE_CLASS_TEXT);
        user.setId(View.generateViewId());
        user.setNextFocusUpId(host.getId());
        card.addView(user);
        if (land) topMargin(user, 8);

        if (!land) card.addView(label("密码"));
        EditText pass = input("密码",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        pass.setId(View.generateViewId());
        pass.setNextFocusUpId(user.getId());
        card.addView(pass);
        if (land) topMargin(pass, 8);

        LinearLayout rememberWrap = rememberRow();
        card.addView(rememberWrap);
        CheckBox remember = (CheckBox) rememberWrap.getChildAt(0);

        if (fnId) {
            fnidHost = host;
            fnidUser = user;
            fnidPass = pass;
            fnidRemember = remember;
        } else {
            httpHost = host;
            httpUser = user;
            httpPass = pass;
            httpRemember = remember;
        }
        return card;
    }

    private TextView label(String text) {
        TextView tv = Ui.text(this, text, 12f, R.color.label_2);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 12);
        lp.bottomMargin = Ui.dp(this, 6);
        tv.setLayoutParams(lp);
        return tv;
    }

    private void topMargin(View v, int dp) {
        ViewGroup.LayoutParams p = v.getLayoutParams();
        if (p instanceof LinearLayout.LayoutParams) {
            ((LinearLayout.LayoutParams) p).topMargin = Ui.dp(this, dp);
            v.setLayoutParams(p);
        }
    }

    private EditText input(String hint, int inputType) {
        EditText et = new EditText(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(this, Ui.isLandscape(this) ? 40 : 46));
        et.setLayoutParams(lp);
        et.setBackgroundResource(R.drawable.bg_search);
        et.setHint(hint);
        et.setHintTextColor(Ui.color(this, R.color.label_3));
        // setSingleLine() 会把密码变换重置掉，所以顺序是先设单行、再设密码类型并重新套用掩码
        et.setSingleLine(true);
        et.setInputType(inputType);
        if ((inputType & InputType.TYPE_TEXT_VARIATION_PASSWORD)
                == InputType.TYPE_TEXT_VARIATION_PASSWORD) {
            et.setTransformationMethod(
                    android.text.method.PasswordTransformationMethod.getInstance());
        }
        et.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
        et.setTextColor(Ui.color(this, R.color.label));
        et.setTextSize(15f);
        return et;
    }

    private LinearLayout rememberRow() {
        LinearLayout row = new LinearLayout(this);
        boolean land = Ui.isLandscape(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, land ? 36 : 44));
        lp.topMargin = Ui.dp(this, land ? 6 : 12);
        row.setLayoutParams(lp);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_row);
        row.setFocusable(true);
        row.setClickable(true);
        row.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        row.setOnFocusChangeListener((v, f) -> Ui.focus(row, f));

        CheckBox cb = new androidx.appcompat.widget.AppCompatCheckBox(this);
        cb.setFocusable(false);
        cb.setText("记住密码");
        cb.setTextColor(Ui.color(this, R.color.label));
        cb.setTextSize(14f);
        androidx.core.widget.CompoundButtonCompat.setButtonTintList(cb,
                android.content.res.ColorStateList.valueOf(Ui.color(this, R.color.accent)));
        row.addView(cb);

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        row.addView(spacer);

        row.addView(Ui.text(this, "下次自动登录", 11f, R.color.label_3));
        row.setOnClickListener(v -> cb.toggle());
        return row;
    }

    // ==================== 偏好 ====================

    private void saveInputs() {
        SharedPreferences.Editor e = prefs.edit();
        if (isFnIdMode) {
            e.putString(Prefs.FNID_HOST, fnidHost.getText().toString().trim());
            e.putString(Prefs.FNID_USER, fnidUser.getText().toString().trim());
            e.putBoolean(Prefs.FNID_REMEMBER, fnidRemember.isChecked());
            if (fnidRemember.isChecked()) e.putString(Prefs.FNID_PASS, fnidPass.getText().toString());
            else e.remove(Prefs.FNID_PASS);
        } else {
            e.putString(Prefs.HOST, httpHost.getText().toString().trim());
            e.putString(Prefs.USER, httpUser.getText().toString().trim());
            e.putBoolean(Prefs.REMEMBER, httpRemember.isChecked());
            if (httpRemember.isChecked()) e.putString(Prefs.PASS, httpPass.getText().toString());
            else e.remove(Prefs.PASS);
        }
        e.apply();
    }

    private void loadInputs() {
        if (isFnIdMode) {
            fnidHost.setText(Prefs.str(this, Prefs.FNID_HOST, ""));
            fnidUser.setText(Prefs.str(this, Prefs.FNID_USER, "video"));
            boolean remember = Prefs.bool(this, Prefs.FNID_REMEMBER, false);
            fnidRemember.setChecked(remember);
            fnidPass.setText(remember ? Prefs.str(this, Prefs.FNID_PASS, "") : "");
        } else {
            httpHost.setText(Prefs.str(this, Prefs.HOST, "http://192.168.10.158:5666"));
            httpUser.setText(Prefs.str(this, Prefs.USER, "video"));
            boolean remember = Prefs.bool(this, Prefs.REMEMBER, false);
            httpRemember.setChecked(remember);
            httpPass.setText(remember ? Prefs.str(this, Prefs.PASS, "") : "");
        }
    }

    private void updateMode() {
        btnHttpMode.setSelected(!isFnIdMode);
        btnFnIdMode.setSelected(isFnIdMode);
        btnHttpMode.setTextColor(Ui.color(this, isFnIdMode ? R.color.label_2 : R.color.label));
        btnFnIdMode.setTextColor(Ui.color(this, isFnIdMode ? R.color.label : R.color.label_2));
    }

    // ==================== 登录 ====================

    private void autoLogin() {
        if (isLoggingIn) return;
        // 有上次登录留下的 token：先直接用它验证，通过就直接进首页，连登录请求都不用发
        String host = Prefs.str(this, Prefs.HOST, "");
        String token = Prefs.str(this, Prefs.TOKEN, "");
        if (!host.isEmpty() && !token.isEmpty()) {
            restoreSession();
            return;
        }
        if (!canAutoLogin()) return;
        btnLogin.setText("自动登录中...");
        doLogin();
    }

    private boolean canAutoLogin() {
        return isFnIdMode
                ? !Prefs.str(this, Prefs.FNID_HOST, "").isEmpty()
                && !Prefs.str(this, Prefs.FNID_USER, "").isEmpty()
                && !Prefs.str(this, Prefs.FNID_PASS, "").isEmpty()
                && Prefs.bool(this, Prefs.FNID_REMEMBER, false)
                : !Prefs.str(this, Prefs.HOST, "").isEmpty()
                && !Prefs.str(this, Prefs.USER, "").isEmpty()
                && !Prefs.str(this, Prefs.PASS, "").isEmpty()
                && Prefs.bool(this, Prefs.REMEMBER, false);
    }

    /** 用保存的 token 直接恢复会话；token 失效再退回账号密码登录 */
    private void restoreSession() {
        isLoggingIn = true;
        btnLogin.setEnabled(false);
        btnLogin.setText("正在恢复登录...");
        AuthRepository.prepare(this, Prefs.str(this, Prefs.HOST, ""));
        FnApiManager api = FnApiManager.getInstance();
        if (api.getApi() == null) {
            reset();
            if (canAutoLogin()) doLogin();
            return;
        }
        api.getApi().getUserInfo().enqueue(new Callback<ApiResponse<UserInfoResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<UserInfoResponse>> call,
                                   Response<ApiResponse<UserInfoResponse>> response) {
                ApiResponse<UserInfoResponse> body = response.body();
                if (response.isSuccessful() && body != null && body.code == 0) {
                    enterHome(false);
                } else {
                    reset();
                    if (canAutoLogin()) doLogin();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<UserInfoResponse>> call, Throwable t) {
                reset();
                if (canAutoLogin()) doLogin();
            }
        });
    }

    private void doLogin() {
        if (isLoggingIn) return;

        String host, user, pass;
        if (isFnIdMode) {
            host = fnidHost.getText().toString().trim();
            user = fnidUser.getText().toString().trim();
            pass = fnidPass.getText().toString();
        } else {
            host = httpHost.getText().toString().trim();
            user = httpUser.getText().toString().trim();
            pass = httpPass.getText().toString();
        }
        if (host.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "请填写完整信息", Toast.LENGTH_SHORT).show();
            return;
        }

        isLoggingIn = true;
        btnLogin.setEnabled(false);
        btnLogin.setText("登录中...");
        saveInputs();

        if (isFnIdMode) {
            if (!FnIdLoginHelper.isFnId(host)) {
                Toast.makeText(this, "FN ID 格式不正确", Toast.LENGTH_LONG).show();
                reset();
                return;
            }
            new FnIdLoginHelper(this, host, user, pass, new FnIdLoginHelper.FnIdCallback() {
                @Override
                public void onSuccess(String token, String domain) {
                    onFnIdSuccess(token, domain, user, pass);
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() -> {
                        Toast.makeText(LoginActivity.this, "FN ID 登录失败：" + message,
                                Toast.LENGTH_LONG).show();
                        reset();
                    });
                }
            }).start();
            return;
        }

        final String finalHost = AuthRepository.normalizeHost(host);
        AuthRepository.loginHttp(host, user, pass, new AuthRepository.LoginCallback() {
            @Override
            public void onSuccess() {
                prefs.edit()
                        .putString(Prefs.HOST, finalHost)
                        .putString(Prefs.USER, user)
                        .putString(Prefs.TOKEN, FnApiManager.getInstance().getToken())
                        .putString(Prefs.LOGIN_MODE, "http")
                        .apply();
                enterHome();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(LoginActivity.this, message, Toast.LENGTH_LONG).show();
                reset();
            }
        });
    }

    private void onFnIdSuccess(String token, String domain, String user, String pass) {
        FnApiManager.getInstance().setToken(token);
        prefs.edit()
                .putString(Prefs.FNID_HOST, fnidHost.getText().toString().trim())
                .putString(Prefs.FNID_USER, user)
                .putBoolean(Prefs.FNID_REMEMBER, fnidRemember.isChecked())
                .putString(Prefs.FNID_PASS, fnidRemember.isChecked() ? pass : "")
                .putString(Prefs.USER, user)
                .putString(Prefs.HOST, domain)
                .putString(Prefs.TOKEN, token)
                .putString(Prefs.LOGIN_MODE, "fnid")
                .apply();
        enterHome();
    }

    private void enterHome() {
        enterHome(true);
    }

    private void enterHome(boolean toast) {
        if (toast) Toast.makeText(this, "登录成功", Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this, HomeActivity.class));
        finish();
    }

    private void reset() {
        isLoggingIn = false;
        btnLogin.setEnabled(true);
        btnLogin.setText("登 录");
        // token 恢复失败时把表单放回来
        formCard.setVisibility(View.VISIBLE);
        loginCard.setVisibility(View.VISIBLE);
        cardContainer.setVisibility(View.VISIBLE);
        btnLogin.setVisibility(View.VISIBLE);
        btnHttpMode.setVisibility(View.VISIBLE);
        btnFnIdMode.setVisibility(View.VISIBLE);
        btnAppearance.setVisibility(View.VISIBLE);
        httpCard.setVisibility(isFnIdMode ? View.GONE : View.VISIBLE);
        fnidCard.setVisibility(isFnIdMode ? View.VISIBLE : View.GONE);
    }
}
