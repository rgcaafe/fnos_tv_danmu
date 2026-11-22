package com.fntv.app.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.fntv.app.R;

/**
 * 单选弹窗（排序方式 / 排序顺序等），样式与外观弹窗一致：
 * 自绘圆角卡片 + 单选行 + 取消，跟随明暗主题，兼容 Android 4.4。
 */
public final class ChoiceDialog {

    public interface OnChoice {
        void onChoice(int index);
    }

    private ChoiceDialog() {
    }

    public static void show(Activity activity, String titleText, String[] labels,
                            int current, OnChoice onChoice) {
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        final int pad = Ui.dp(activity, 22);
        final int accent = Ui.color(activity, R.color.accent);
        final int label = Ui.color(activity, R.color.label);
        final int label2 = Ui.color(activity, R.color.label_2);
        final int cardColor = Ui.color(activity, R.color.surface_elevated);

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundDrawable(Ui.round(cardColor, Ui.dp(activity, 20)));

        TextView title = new TextView(activity);
        title.setText(titleText);
        title.setTextSize(20f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(label);
        title.setIncludeFontPadding(false);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.leftMargin = pad;
        titleLp.rightMargin = pad;
        titleLp.topMargin = Ui.dp(activity, 22);
        titleLp.bottomMargin = Ui.dp(activity, 10);
        card.addView(title, titleLp);

        final View[] rows = new View[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setFocusable(true);
            row.setClickable(true);
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                row.setDefaultFocusHighlightEnabled(false);
            }
            row.setPadding(Ui.dp(activity, 14), 0, Ui.dp(activity, 14), 0);

            View dot = new View(activity);
            dot.setBackgroundDrawable(radioDrawable(activity, index == current, cardColor));
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(
                    Ui.dp(activity, 22), Ui.dp(activity, 22));
            dotLp.rightMargin = Ui.dp(activity, 14);
            row.addView(dot, dotLp);

            TextView text = new TextView(activity);
            text.setText(labels[i]);
            text.setTextSize(16f);
            text.setTextColor(index == current ? label : label2);
            text.setTypeface(index == current ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            text.setIncludeFontPadding(false);
            row.addView(text, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            row.setOnClickListener(v -> {
                dialog.dismiss();
                if (index != current && onChoice != null) onChoice.onChoice(index);
            });
            row.setOnFocusChangeListener((v, focused) -> v.setBackgroundDrawable(focused
                    ? Ui.round(Ui.color(activity, R.color.fill_strong), Ui.dp(activity, 12),
                               Ui.color(activity, R.color.accent), Ui.dp(activity, 2))
                    : null));

            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 54));
            rowLp.leftMargin = Ui.dp(activity, 8);
            rowLp.rightMargin = Ui.dp(activity, 8);
            card.addView(row, rowLp);
            rows[i] = row;
        }

        View line = new View(activity);
        line.setBackgroundColor(Ui.color(activity, R.color.separator));
        LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(activity, 0.6f)));
        lineLp.topMargin = Ui.dp(activity, 8);
        card.addView(line, lineLp);

        TextView cancel = new TextView(activity);
        cancel.setText("取消");
        cancel.setTextSize(15f);
        cancel.setTypeface(Typeface.DEFAULT_BOLD);
        cancel.setTextColor(accent);
        cancel.setGravity(Gravity.CENTER);
        cancel.setIncludeFontPadding(false);
        cancel.setFocusable(true);
        cancel.setClickable(true);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            cancel.setDefaultFocusHighlightEnabled(false);
        }
        cancel.setPadding(Ui.dp(activity, 16), 0, Ui.dp(activity, 16), 0);
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(activity, 40));
        cancelLp.gravity = Gravity.END;
        cancelLp.leftMargin = Ui.dp(activity, 8);
        cancelLp.rightMargin = Ui.dp(activity, 14);
        cancelLp.topMargin = Ui.dp(activity, 6);
        cancelLp.bottomMargin = Ui.dp(activity, 14);
        cancel.setOnClickListener(v -> dialog.dismiss());
        cancel.setOnFocusChangeListener((v, focused) -> v.setBackgroundDrawable(focused
                ? Ui.round(Ui.color(activity, R.color.fill_strong), Ui.dp(activity, 10),
                           Ui.color(activity, R.color.accent), Ui.dp(activity, 2))
                : null));
        card.addView(cancel, cancelLp);

        dialog.setContentView(card);

        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0));
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.45f);
            int width = Math.min(Ui.screenWidth(activity) - Ui.dp(activity, 80),
                    Ui.dp(activity, 380));
            w.setLayout(Math.max(Ui.dp(activity, 240), width),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();

        // 必须在 show 之后请求焦点，遥控器才能进入弹窗内容
        final View first = current >= 0 && current < rows.length ? rows[current] : rows[0];
        first.post(first::requestFocus);
    }

    /** 单选圆点：选中为蓝色圆环，未选中为细描边空心圆 */
    private static Drawable radioDrawable(Activity activity, boolean selected, int cardColor) {
        int size = Ui.dp(activity, 22);
        GradientDrawable outer = new GradientDrawable();
        outer.setShape(GradientDrawable.OVAL);
        if (selected) {
            outer.setColor(Ui.color(activity, R.color.accent));
        } else {
            outer.setColor(0);
            outer.setStroke(Math.max(1, Ui.dp(activity, 1.6f)),
                    Ui.color(activity, R.color.separator));
        }
        if (!selected) return outer;

        GradientDrawable inner = new GradientDrawable();
        inner.setShape(GradientDrawable.OVAL);
        inner.setColor(cardColor);
        LayerDrawable ld = new LayerDrawable(new Drawable[]{outer, inner});
        int inset = Math.round(size * 0.30f);
        ld.setLayerInset(1, inset, inset, inset, inset);
        return ld;
    }
}
