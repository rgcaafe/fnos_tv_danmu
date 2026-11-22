package com.fntv.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.fntv.app.ui.Ui;

/**
 * 播放器里的列表选择弹窗（画质 / 选集 / 音轨 / 字幕）。
 * 深色圆角卡片 + 可滚动列表，焦点行有白描边，兼容 Android 4.4。
 */
public final class PlayerDialog {

    public interface OnItem {
        void onItem(int index);
    }

    private static final int CARD_COLOR = 0xF21A1A1C;
    private static final int TEXT_COLOR = 0xFFEDEDED;
    private static final int MUTED_COLOR = 0xFFB3B3B3;

    private PlayerDialog() {
    }

    public static void list(Context context, String title, String[] items,
                            int selected, final OnItem onItem) {
        if (!(context instanceof Activity) || items == null || items.length == 0) return;
        final Activity activity = (Activity) context;
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        final int pad = dp(activity, 20);
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundDrawable(Ui.round(CARD_COLOR, dp(activity, 20)));

        TextView titleView = new TextView(activity);
        titleView.setText(title == null || title.isEmpty() ? "请选择" : title);
        titleView.setTextSize(20f);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setTextColor(0xFFFFFFFF);
        titleView.setIncludeFontPadding(false);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.leftMargin = pad;
        titleLp.rightMargin = pad;
        titleLp.topMargin = dp(activity, 20);
        titleLp.bottomMargin = dp(activity, 8);
        card.addView(titleView, titleLp);

        MaxHeightScrollView scroll = new MaxHeightScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFocusable(false);
        scroll.setFocusableInTouchMode(false);
        scroll.setMaxHeight(Math.round(activity.getResources()
                .getDisplayMetrics().heightPixels * 0.58f));

        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(activity, 8), 0, dp(activity, 8), dp(activity, 4));

        final TextView[] rows = new TextView[items.length];
        for (int i = 0; i < items.length; i++) {
            final int index = i;
            final boolean isSelected = i == selected;
            TextView row = new TextView(activity);
            row.setText((isSelected ? "✓  " : "      ") + items[i]);
            row.setTextSize(15f);
            row.setTextColor(isSelected ? Ui.color(activity, R.color.accent) : TEXT_COLOR);
            row.setTypeface(isSelected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            row.setIncludeFontPadding(false);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setSingleLine(true);
            row.setEllipsize(TextUtils.TruncateAt.END);
            row.setPadding(dp(activity, 14), 0, dp(activity, 14), 0);
            row.setFocusable(true);
            row.setClickable(true);
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                row.setDefaultFocusHighlightEnabled(false);
            }
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 48));
            rowLp.bottomMargin = dp(activity, 2);
            row.setLayoutParams(rowLp);
            row.setBackgroundDrawable(isSelected
                    ? Ui.round(0x1AFFFFFF, dp(activity, 10))
                    : null);
            row.setOnFocusChangeListener((v, focused) -> v.setBackgroundDrawable(focused
                    ? Ui.round(0x26FFFFFF, dp(activity, 10), 0xFFFFFFFF, dp(activity, 2))
                    : (isSelected ? Ui.round(0x1AFFFFFF, dp(activity, 10)) : null)));
            row.setOnClickListener(v -> {
                dialog.dismiss();
                if (onItem != null) onItem.onItem(index);
            });
            list.addView(row);
            rows[i] = row;
        }
        scroll.addView(list);
        card.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View line = new View(activity);
        line.setBackgroundColor(0x26FFFFFF);
        LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(activity, 0.6f)));
        lineLp.leftMargin = pad;
        lineLp.rightMargin = pad;
        card.addView(line, lineLp);

        TextView close = new TextView(activity);
        close.setText("关闭");
        close.setTextSize(15f);
        close.setTypeface(Typeface.DEFAULT_BOLD);
        close.setTextColor(Ui.color(activity, R.color.accent));
        close.setIncludeFontPadding(false);
        close.setGravity(Gravity.CENTER);
        close.setFocusable(true);
        close.setClickable(true);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            close.setDefaultFocusHighlightEnabled(false);
        }
        close.setPadding(dp(activity, 16), 0, dp(activity, 16), 0);
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 40));
        closeLp.gravity = Gravity.END;
        closeLp.rightMargin = dp(activity, 14);
        closeLp.topMargin = dp(activity, 6);
        closeLp.bottomMargin = dp(activity, 12);
        close.setOnClickListener(v -> dialog.dismiss());
        close.setOnFocusChangeListener((v, focused) -> v.setBackgroundDrawable(focused
                ? Ui.round(0x40FFFFFF, dp(activity, 10), 0xFFFFFFFF, dp(activity, 2))
                : null));
        card.addView(close, closeLp);

        dialog.setContentView(card);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0));
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.5f);
            int width = Math.min(Ui.screenWidth(activity) - dp(activity, 80),
                    dp(activity, 460));
            w.setLayout(Math.max(dp(activity, 260), width),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();

        int focusIndex = selected >= 0 && selected < rows.length ? selected : 0;
        final TextView first = rows[focusIndex];
        first.post(first::requestFocus);
    }

    /** 深色圆角提示弹窗：标题 + 正文 + 关闭 */
    public static void message(Context context, String title, String message) {
        if (!(context instanceof Activity)) return;
        final Activity activity = (Activity) context;
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        final int pad = dp(activity, 20);
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundDrawable(Ui.round(CARD_COLOR, dp(activity, 20)));

        TextView titleView = new TextView(activity);
        titleView.setText(title == null || title.isEmpty() ? "提示" : title);
        titleView.setTextSize(20f);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setTextColor(0xFFFFFFFF);
        titleView.setIncludeFontPadding(false);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.leftMargin = pad;
        titleLp.rightMargin = pad;
        titleLp.topMargin = dp(activity, 20);
        titleLp.bottomMargin = dp(activity, 12);
        card.addView(titleView, titleLp);

        TextView body = new TextView(activity);
        body.setText(message == null ? "" : message);
        body.setTextSize(15f);
        body.setTextColor(MUTED_COLOR);
        body.setIncludeFontPadding(false);
        body.setLineSpacing(dp(activity, 4), 1f);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyLp.leftMargin = pad;
        bodyLp.rightMargin = pad;
        bodyLp.bottomMargin = dp(activity, 16);
        card.addView(body, bodyLp);

        View line = new View(activity);
        line.setBackgroundColor(0x26FFFFFF);
        LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(activity, 0.6f)));
        lineLp.leftMargin = pad;
        lineLp.rightMargin = pad;
        card.addView(line, lineLp);

        TextView close = new TextView(activity);
        close.setText("关闭");
        close.setTextSize(15f);
        close.setTypeface(Typeface.DEFAULT_BOLD);
        close.setTextColor(Ui.color(activity, R.color.accent));
        close.setIncludeFontPadding(false);
        close.setGravity(Gravity.CENTER);
        close.setFocusable(true);
        close.setClickable(true);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            close.setDefaultFocusHighlightEnabled(false);
        }
        close.setPadding(dp(activity, 16), 0, dp(activity, 16), 0);
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 40));
        closeLp.gravity = Gravity.END;
        closeLp.rightMargin = dp(activity, 14);
        closeLp.topMargin = dp(activity, 6);
        closeLp.bottomMargin = dp(activity, 12);
        close.setOnClickListener(v -> dialog.dismiss());
        close.setOnFocusChangeListener((v, focused) -> v.setBackgroundDrawable(focused
                ? Ui.round(0x40FFFFFF, dp(activity, 10), 0xFFFFFFFF, dp(activity, 2))
                : null));
        card.addView(close, closeLp);

        dialog.setContentView(card);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0));
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.5f);
            int width = Math.min(Ui.screenWidth(activity) - dp(activity, 80),
                    dp(activity, 460));
            w.setLayout(Math.max(dp(activity, 260), width),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
        final TextView first = close;
        first.post(first::requestFocus);
    }

    /** 统一播放器弹窗按钮：去掉系统主题着色，聚焦时灰底 + 白描边 */
    public static void styleButton(final View view, final boolean primary) {
        if (view == null) return;
        if (android.os.Build.VERSION.SDK_INT >= 21) view.setBackgroundTintList(null);
        final int normal = primary ? R.drawable.bg_player_primary : R.drawable.bg_player_dialog_chip;
        view.setBackgroundResource(normal);
        if (android.os.Build.VERSION.SDK_INT >= 26) view.setDefaultFocusHighlightEnabled(false);
        view.setOnFocusChangeListener((v, focused) -> {
            if (android.os.Build.VERSION.SDK_INT >= 21) v.setBackgroundTintList(null);
            if (focused) {
                v.setBackgroundResource(primary
                        ? R.drawable.bg_player_primary_focus
                        : R.drawable.bg_player_dialog_focus);
            } else {
                v.setBackgroundResource(normal);
            }
        });
    }

    private static int dp(Context context, float value) {
        return Ui.dp(context, value);
    }

    private static class MaxHeightScrollView extends ScrollView {
        private int maxHeight;

        MaxHeightScrollView(Context context) {
            super(context);
        }

        void setMaxHeight(int height) {
            maxHeight = height;
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            super.onMeasure(widthSpec, heightSpec);
            if (maxHeight > 0 && getMeasuredHeight() > maxHeight) {
                setMeasuredDimension(getMeasuredWidth(), maxHeight);
            }
        }
    }
}
