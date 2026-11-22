package com.fntv.app.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.fntv.app.R;

/**
 * 通用「登录页外观弹窗」风格的卡片弹窗：圆角卡片 + 底部文字按钮，
 * 明暗主题自适应、遥控器焦点可见，兼容 Android 4.4。
 */
public final class CardDialog {

    public interface Action {
        void run();
    }

    public interface TextAction {
        void run(String text);
    }

    private CardDialog() {
    }

    /** 提示弹窗（标题 + 可滚动正文 + 两个按钮） */
    public static void message(Activity activity, String title, String message,
                               String positive, Action onPositive,
                               String negative, Action onNegative) {
        message(activity, title, message, positive, onPositive, negative, onNegative, true);
    }

    public static void message(final Activity activity, String title, String message,
                               String positive, final Action onPositive,
                               String negative, final Action onNegative,
                               boolean cancelable) {
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(cancelable);

        final int pad = Ui.dp(activity, 22);
        LinearLayout card = card(activity);
        card.addView(titleView(activity, title));

        ScrollView scroll = null;
        if (message != null && !message.isEmpty()) {
            MaxHeightScrollView sv = new MaxHeightScrollView(activity);
            sv.setVerticalScrollBarEnabled(true);
            sv.setFocusable(false);
            sv.setFocusableInTouchMode(false);
            sv.setMaxHeight(Math.round(Ui.screenHeight(activity) * 0.56f));
            TextView body = Ui.text(activity, message, 15f, R.color.label);
            body.setLineSpacing(Ui.dp(activity, 4), 1f);
            body.setPadding(pad, 0, pad, 0);
            sv.addView(body);
            card.addView(sv, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            scroll = sv;
        }

        final ScrollView bodyScroll = scroll;
        View positiveView = button(activity, dialog, positive, accent(activity), v -> {
            dialog.dismiss();
            if (onPositive != null) onPositive.run();
        });
        if (positiveView != null && bodyScroll != null) {
            positiveView.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    bodyScroll.scrollBy(0, -Ui.dp(activity, 56));
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    bodyScroll.scrollBy(0, Ui.dp(activity, 56));
                    return true;
                }
                return false;
            });
        }

        View negativeView = button(activity, dialog, negative, accent(activity), v -> {
            dialog.dismiss();
            if (onNegative != null) onNegative.run();
        });
        card.addView(buttonRow(activity, negativeView, positiveView));

        dialog.setContentView(card);
        styleWindow(dialog, activity);
        dialog.show();
        final View focus = positiveView != null ? positiveView : negativeView;
        if (focus != null) focus.post(focus::requestFocus);
    }

    /** 输入弹窗（标题 + 输入框 + 两个按钮） */
    public static void input(final Activity activity, String title, String value, int inputType,
                             String positive, final TextAction onPositive,
                             String negative, final Action onNegative) {
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        final int pad = Ui.dp(activity, 22);
        LinearLayout card = card(activity);
        card.addView(titleView(activity, title));

        final EditText input = new EditText(activity);
        input.setInputType(inputType);
        input.setText(value == null ? "" : value);
        input.setSelection(input.getText().length());
        input.setSingleLine(true);
        input.setTextSize(16f);
        input.setTextColor(Ui.color(activity, R.color.label));
        input.setHintTextColor(Ui.color(activity, R.color.label_3));
        input.setBackgroundDrawable(Ui.round(0, Ui.dp(activity, 10),
                Ui.color(activity, R.color.separator), Ui.dp(activity, 1)));
        input.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 10),
                Ui.dp(activity, 14), Ui.dp(activity, 10));
        input.setOnFocusChangeListener((v, focused) -> v.setBackgroundDrawable(
                Ui.round(0, Ui.dp(activity, 10),
                        Ui.color(activity, focused ? R.color.accent : R.color.separator),
                        Ui.dp(activity, focused ? 2 : 1))));
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inputLp.leftMargin = pad;
        inputLp.rightMargin = pad;
        inputLp.topMargin = Ui.dp(activity, 2);
        card.addView(input, inputLp);

        View positiveView = button(activity, dialog, positive, accent(activity), v -> {
            dialog.dismiss();
            if (onPositive != null) onPositive.run(input.getText().toString());
        });
        View negativeView = button(activity, dialog, negative, accent(activity), v -> {
            dialog.dismiss();
            if (onNegative != null) onNegative.run();
        });
        // 遥控器：输入框按 ↓ 进入按钮，按钮按 ↑ 回到输入框
        if (positiveView != null) {
            input.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN
                        && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    positiveView.requestFocus();
                    return true;
                }
                return false;
            });
            View.OnKeyListener backToInput = (v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN
                        && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    input.requestFocus();
                    return true;
                }
                return false;
            };
            positiveView.setOnKeyListener(backToInput);
            if (negativeView != null) negativeView.setOnKeyListener(backToInput);
        }
        card.addView(buttonRow(activity, negativeView, positiveView));

        dialog.setContentView(card);
        styleWindow(dialog, activity);
        dialog.show();
        input.post(() -> {
            input.requestFocus();
            input.setSelection(input.getText().length());
        });
    }

    // ==================== 组件 ====================

    private static LinearLayout card(Activity activity) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundDrawable(Ui.round(
                Ui.color(activity, R.color.surface_elevated), Ui.dp(activity, 20)));
        return card;
    }

    private static TextView titleView(Activity activity, String title) {
        int pad = Ui.dp(activity, 22);
        TextView view = new TextView(activity);
        view.setText(title);
        view.setTextSize(20f);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setTextColor(Ui.color(activity, R.color.label));
        view.setIncludeFontPadding(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = pad;
        lp.rightMargin = pad;
        lp.topMargin = Ui.dp(activity, 22);
        lp.bottomMargin = Ui.dp(activity, 10);
        view.setLayoutParams(lp);
        return view;
    }

    private static View buttonRow(Activity activity, View negative, View positive) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        boxLp.topMargin = Ui.dp(activity, 8);
        box.setLayoutParams(boxLp);

        View line = new View(activity);
        line.setBackgroundColor(Ui.color(activity, R.color.separator));
        box.addView(line, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(activity, 0.6f))));

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        row.setPadding(Ui.dp(activity, 8), Ui.dp(activity, 8), Ui.dp(activity, 10),
                Ui.dp(activity, 10));
        if (negative != null) row.addView(negative);
        if (positive != null) row.addView(positive);
        box.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }

    private static View button(Activity activity, final Dialog dialog, String text,
                               int color, View.OnClickListener click) {
        if (text == null || text.isEmpty()) return null;
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextSize(15f);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        view.setIncludeFontPadding(false);
        view.setFocusable(true);
        view.setClickable(true);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            view.setDefaultFocusHighlightEnabled(false);
        }
        view.setPadding(Ui.dp(activity, 16), 0, Ui.dp(activity, 16), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(activity, 40));
        lp.leftMargin = Ui.dp(activity, 4);
        view.setLayoutParams(lp);
        view.setOnFocusChangeListener((v, focused) -> v.setBackgroundDrawable(focused
                ? Ui.round(Ui.color(activity, R.color.fill_strong), Ui.dp(activity, 10),
                           Ui.color(activity, R.color.accent), Ui.dp(activity, 2))
                : null));
        view.setOnClickListener(click);
        return view;
    }

    private static int accent(Activity activity) {
        return Ui.color(activity, R.color.accent);
    }

    private static void styleWindow(Dialog dialog, Activity activity) {
        Window w = dialog.getWindow();
        if (w == null) return;
        w.setBackgroundDrawable(new ColorDrawable(0));
        w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        w.setDimAmount(0.45f);
        int width = Math.min(Ui.screenWidth(activity) - Ui.dp(activity, 80),
                Ui.dp(activity, 380));
        w.setLayout(Math.max(Ui.dp(activity, 240), width),
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /** 正文超过屏幕高度的 56% 时才滚动 */
    private static class MaxHeightScrollView extends ScrollView {
        private int maxHeight;

        MaxHeightScrollView(android.content.Context context) {
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
