package com.fntv.app.ui.settings;

import android.app.Activity;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.fntv.app.BuildConfig;
import com.fntv.app.R;
import com.fntv.app.core.Prefs;
import com.fntv.app.core.ThemeMode;
import com.fntv.app.ui.CardDialog;
import com.fntv.app.ui.Ui;

/**
 * 设置页：账户、播放、外观、关于。
 */
public final class SettingsScreen {

    public interface Host {
        void reRender();

        void onAppearance();

        void onCheckUpdate(Button button);

        void onFeedback();

        void onLogout();

        String displayName();
    }

    private SettingsScreen() {
    }

    public static void render(final Activity activity, LinearLayout container, final Host host) {
        container.removeAllViews();

        TextView title = Ui.largeTitle(activity, "设置");
        title.setPadding(0, Ui.dp(activity, 4), 0, 0);
        container.addView(title);

        TextView subtitle = Ui.subhead(activity, "账户与播放偏好");
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = Ui.dp(activity, 6);
        container.addView(subtitle);

        container.addView(accountCard(activity, host));

        // ===== 播放 =====
        container.addView(groupLabel(activity, "播放"));
        LinearLayout playback = group(activity);
        playback.addView(clickableRow(activity, "解码模式",
                "hardware".equals(Prefs.str(activity, Prefs.DECODER, "hardware")) ? "硬解" : "软解",
                R.drawable.bg_row_first,
                v -> {
                    boolean hardware = "hardware".equals(Prefs.str(activity, Prefs.DECODER, "hardware"));
                    Prefs.put(activity, Prefs.DECODER, hardware ? "software" : "hardware");
                    Toast.makeText(activity, hardware ? "已切换为软解" : "已切换为硬解",
                            Toast.LENGTH_SHORT).show();
                    host.reRender();
                }));
        playback.addView(divider(activity));
        playback.addView(clickableRow(activity, "快进 / 快退步长",
                Prefs.integer(activity, Prefs.SEEK_STEP, 10) + " 秒",
                R.drawable.bg_row_mid,
                v -> editNumber(activity, "快进 / 快退步长（秒）",
                        Prefs.integer(activity, Prefs.SEEK_STEP, 10), 1, 300, Prefs.SEEK_STEP, host)));
        playback.addView(divider(activity));
        playback.addView(clickableRow(activity, "缓冲时间",
                Prefs.integer(activity, Prefs.BUFFER_TIME, 30) + " 秒",
                R.drawable.bg_row_mid,
                v -> editNumber(activity, "缓冲时间（秒）",
                        Prefs.integer(activity, Prefs.BUFFER_TIME, 30), 5, 300, Prefs.BUFFER_TIME, host)));
        playback.addView(divider(activity));
        playback.addView(clickableRow(activity, "弹幕服务器", null,
                R.drawable.bg_row_last, v -> editDanmu(activity, host)));
        container.addView(playback);

        // ===== 外观 =====
        container.addView(groupLabel(activity, "外观"));
        LinearLayout appearance = group(activity);
        appearance.addView(clickableRow(activity, "外观",
                ThemeMode.label(activity, ThemeMode.current(activity)),
                R.drawable.bg_row_single, v -> host.onAppearance()));
        container.addView(appearance);

        // ===== 关于 =====
        container.addView(groupLabel(activity, "关于"));
        LinearLayout about = group(activity);

        LinearLayout version = plainRow(activity);
        version.addView(label(activity, "版本"));
        version.addView(value(activity, "v" + BuildConfig.VERSION_NAME, R.color.label_3));
        about.addView(version);
        about.addView(divider(activity));

        Button update = new Button(activity);
        update.setBackgroundResource(R.drawable.bg_row_mid);
        Ui.noTint(update);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            update.setDefaultFocusHighlightEnabled(false);
        }
        update.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 52)));
        update.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        update.setPadding(Ui.dp(activity, 16), 0, Ui.dp(activity, 16), 0);
        update.setText("检查更新");
        update.setTextColor(Ui.color(activity, R.color.label));
        update.setTextSize(16f);
        update.setAllCaps(false);
        update.setFocusable(true);
        about.addView(update);
        host.onCheckUpdate(update);
        about.addView(divider(activity));

        about.addView(clickableRow(activity, "问题反馈", null,
                R.drawable.bg_row_last, v -> host.onFeedback()));
        container.addView(about);

        // ===== 退出 =====
        LinearLayout logoutCard = group(activity);
        LinearLayout logout = plainRow(activity);
        logout.setBackgroundResource(R.drawable.bg_row_single);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            logout.setDefaultFocusHighlightEnabled(false);
        }
        logout.setGravity(Gravity.CENTER);
        TextView logoutText = Ui.text(activity, "退出登录", 16f, R.color.danger);
        logoutText.setTypeface(Typeface.DEFAULT_BOLD);
        logout.addView(logoutText);
        logout.setOnFocusChangeListener(Ui::focus);
        logout.setOnClickListener(v -> host.onLogout());
        logoutCard.addView(logout);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.topMargin = Ui.dp(activity, 26);
        logoutCard.setLayoutParams(cardLp);
        container.addView(logoutCard);
    }

    // ==================== 组件 ====================

    private static View accountCard(Activity activity, Host host) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(Ui.dp(activity, 16), Ui.dp(activity, 16),
                Ui.dp(activity, 16), Ui.dp(activity, 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(activity, 18);
        card.setLayoutParams(lp);

        TextView avatar = Ui.text(activity, com.fntv.app.core.Fmt.initial(host.displayName()),
                20f, R.color.text_on_accent);
        avatar.setTypeface(Typeface.DEFAULT_BOLD);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackgroundResource(R.drawable.bg_avatar);
        avatar.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(activity, 48), Ui.dp(activity, 48)));
        card.addView(avatar);

        LinearLayout text = Ui.column(activity);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textLp.leftMargin = Ui.dp(activity, 12);
        text.setLayoutParams(textLp);

        TextView name = Ui.text(activity, host.displayName(), 18f, R.color.label);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setSingleLine(true);
        text.addView(name);

        TextView server = Ui.text(activity,
                Prefs.str(activity, Prefs.HOST, "").replace("http://", "").replace("https://", ""),
                12f, R.color.label_3);
        LinearLayout.LayoutParams serverLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        serverLp.topMargin = Ui.dp(activity, 4);
        server.setLayoutParams(serverLp);
        server.setSingleLine(true);
        text.addView(server);
        card.addView(text);
        return card;
    }

    private static TextView groupLabel(Activity activity, String text) {
        TextView label = Ui.text(activity, text, 12f, R.color.label_3);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Ui.dp(activity, 4);
        lp.topMargin = Ui.dp(activity, 24);
        lp.bottomMargin = Ui.dp(activity, 8);
        label.setLayoutParams(lp);
        return label;
    }

    private static LinearLayout group(Activity activity) {
        LinearLayout card = Ui.column(activity);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(0, 0, 0, 0);
        return card;
    }

    private static LinearLayout plainRow(Activity activity) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(activity, 16), 0, Ui.dp(activity, 16), 0);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 52)));
        return row;
    }

    private static View clickableRow(Activity activity, String label, String value,
                                     int bgRes, View.OnClickListener click) {
        LinearLayout row = plainRow(activity);
        row.setBackgroundResource(bgRes);
        row.setFocusable(true);
        row.setClickable(true);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            row.setDefaultFocusHighlightEnabled(false);
        }
        row.addView(label(activity, label));
        if (value != null) row.addView(value(activity, value, R.color.label_3));
        row.addView(chevron(activity));
        row.setOnFocusChangeListener(Ui::focus);
        row.setOnClickListener(click);
        return row;
    }

    private static TextView label(Activity activity, String text) {
        TextView tv = Ui.text(activity, text, 16f, R.color.label);
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return tv;
    }

    private static TextView value(Activity activity, String text, int colorRes) {
        TextView tv = Ui.text(activity, text, 15f, colorRes);
        tv.setSingleLine(true);
        return tv;
    }

    private static ImageView chevron(Activity activity) {
        ImageView iv = Ui.icon(activity, R.drawable.ic_chevron_right, 15, R.color.label_3);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                Ui.dp(activity, 15), Ui.dp(activity, 15));
        lp.leftMargin = Ui.dp(activity, 6);
        iv.setLayoutParams(lp);
        return iv;
    }

    private static View divider(Activity activity) {
        return Ui.divider(activity, 16);
    }

    // ==================== 弹窗 ====================

    private static void editNumber(final Activity activity, String title, int current,
                                   final int min, final int max, final String key, final Host host) {
        CardDialog.input(activity, title, String.valueOf(current),
                InputType.TYPE_CLASS_NUMBER,
                "保存", text -> {
                    try {
                        int value = Integer.parseInt(text.trim());
                        value = Math.max(min, Math.min(max, value));
                        Prefs.putInt(activity, key, value);
                        host.reRender();
                    } catch (NumberFormatException ignored) {
                    }
                },
                "取消", null);
    }

    private static void editDanmu(final Activity activity, final Host host) {
        String current = Prefs.str(activity, Prefs.DANMU_URL, "");
        if (current.isEmpty()) {
            String hostPart = Prefs.str(activity, Prefs.HOST, "")
                    .replaceAll("^https?://", "").replaceAll("/.*$", "").replaceAll(":\\d+$", "");
            current = "http://" + hostPart + ":9321";
        }
        CardDialog.input(activity, "弹幕服务器地址", current,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI,
                "保存", text -> {
                    String value = text.trim();
                    if (!value.isEmpty()) Prefs.put(activity, Prefs.DANMU_URL, value);
                    host.reRender();
                },
                "重置", () -> {
                    Prefs.get(activity).edit().remove(Prefs.DANMU_URL).apply();
                    host.reRender();
                });
    }

    public static void copyGroupNumber(Activity activity) {
        ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setText("693516430");
            Toast.makeText(activity, "群号已复制", Toast.LENGTH_SHORT).show();
        }
    }
}
