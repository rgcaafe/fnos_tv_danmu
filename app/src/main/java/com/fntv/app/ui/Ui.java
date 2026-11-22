package com.fntv.app.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.fntv.app.R;
import com.fntv.app.core.ThemeMode;

/**
 * Apple 风格设计系统：间距、文字层级、圆角、焦点动效、系统栏。
 * 兼容 Android 4.4 (API 19)。
 */
public final class Ui {

    private Ui() {
    }

    // ==================== 尺寸 ====================

    public static int dp(Context c, float value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }

    public static int dimen(Context c, int id) {
        return c.getResources().getDimensionPixelSize(id);
    }

    public static int spx(Context c, float sp) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp,
                c.getResources().getDisplayMetrics()));
    }

    public static int color(Context c, int colorRes) {
        return ContextCompat.getColor(c, colorRes);
    }

    // ==================== 布局 ====================

    public static LinearLayout column(Context c) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return v;
    }

    public static LinearLayout row(Context c) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return v;
    }

    public static View spacer(Context c, float heightDp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(c, heightDp)));
        return v;
    }

    public static View divider(Context c, float insetStartDp) {
        View v = new View(c);
        v.setBackgroundColor(color(c, R.color.separator));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 0.5f)));
        lp.leftMargin = dp(c, insetStartDp);
        v.setLayoutParams(lp);
        return v;
    }

    public static void margins(View v, float l, float t, float r, float b) {
        ViewGroup.LayoutParams raw = v.getLayoutParams();
        if (!(raw instanceof ViewGroup.MarginLayoutParams)) return;
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) raw;
        lp.setMargins(dp(v.getContext(), l), dp(v.getContext(), t),
                dp(v.getContext(), r), dp(v.getContext(), b));
        v.setLayoutParams(lp);
    }

    public static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams fixed(Context c, float wDp, float hDp) {
        return new LinearLayout.LayoutParams(dp(c, wDp), dp(c, hDp));
    }

    // ==================== 文字 ====================

    public static TextView text(Context c, CharSequence value, float sizeSp, int colorRes) {
        TextView t = new TextView(c);
        t.setText(value);
        t.setTextSize(sizeSp);
        t.setTextColor(color(c, colorRes));
        t.setIncludeFontPadding(false);
        return t;
    }

    public static TextView bold(TextView t) {
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    public static TextView largeTitle(Context c, CharSequence value) {
        TextView t = new TextView(c);
        t.setText(value);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, dimen(c, R.dimen.large_title_size));
        t.setTextColor(color(c, R.color.label));
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setIncludeFontPadding(false);
        t.setLayoutParams(matchWrap());
        return t;
    }

    public static TextView title(Context c, CharSequence value) {
        TextView t = text(c, value, 20f, R.color.label);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    public static TextView sectionTitle(Context c, CharSequence value) {
        TextView t = new TextView(c);
        t.setText(value);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, dimen(c, R.dimen.section_title_size));
        t.setTextColor(color(c, R.color.label));
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setIncludeFontPadding(false);
        return t;
    }

    public static TextView body(Context c, CharSequence value) {
        return text(c, value, 16f, R.color.label);
    }

    public static TextView subhead(Context c, CharSequence value) {
        return text(c, value, 14f, R.color.label_2);
    }

    public static TextView caption(Context c, CharSequence value) {
        return text(c, value, 12f, R.color.label_3);
    }

    // ==================== 图标 ====================

    public static ImageView icon(Context c, int drawableRes, float sizeDp, int colorRes) {
        AppCompatImageView iv = new AppCompatImageView(c);
        iv.setImageResource(drawableRes);
        iv.setColorFilter(color(c, colorRes));
        iv.setLayoutParams(fixed(c, sizeDp, sizeDp));
        return iv;
    }

    // ==================== 背景 ====================

    public static GradientDrawable round(int fillColor, float radiusPx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setColor(fillColor);
        gd.setCornerRadius(radiusPx);
        return gd;
    }

    /** 圆角 + 可选描边（遥控器焦点框用） */
    public static GradientDrawable round(int fillColor, float radiusPx, int strokeColor, float strokePx) {
        GradientDrawable gd = round(fillColor, radiusPx);
        if (strokeColor != 0) gd.setStroke(Math.max(2, Math.round(strokePx)), strokeColor);
        return gd;
    }

    public static GradientDrawable gradient(int start, int end, float radiusPx, boolean diagonal) {
        GradientDrawable gd = new GradientDrawable(
                diagonal ? GradientDrawable.Orientation.TL_BR : GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{start, end});
        gd.setCornerRadius(radiusPx);
        return gd;
    }

    /**
     * 水平渐变（左 → 右），positions 可为 null。
     * <p>
     * GradientDrawable#setColors(colors, positions) 要 API 29 才有，
     * 这里改用 ShapeDrawable + LinearGradient，Android 4.4 也能正常画。
     */
    public static Drawable gradientH(int[] colors, float[] positions) {
        return linearGradient(colors, positions, false);
    }

    /** 竖直渐变（顶部 → 底部），positions 可为 null */
    public static Drawable gradientV(int[] colors, float[] positions) {
        return linearGradient(colors, positions, true);
    }

    private static Drawable linearGradient(final int[] colors, final float[] positions,
                                           final boolean vertical) {
        ShapeDrawable drawable = new ShapeDrawable(new RectShape());
        drawable.setShaderFactory(new ShapeDrawable.ShaderFactory() {
            @Override
            public Shader resize(int width, int height) {
                if (vertical) {
                    return new LinearGradient(0f, 0f, 0f, Math.max(1, height),
                            colors, positions, Shader.TileMode.CLAMP);
                }
                return new LinearGradient(0f, 0f, Math.max(1, width), 0f,
                        colors, positions, Shader.TileMode.CLAMP);
            }
        });
        return drawable;
    }

    public static void card(View v) {
        v.setBackgroundResource(R.drawable.bg_card);
    }

    /** 清除 AppCompat 按钮的默认着色，避免自定义背景被染成主色 */
    public static void noTint(View v) {
        if (v == null) return;
        androidx.core.view.ViewCompat.setBackgroundTintList(v, null);
    }

    /** 递归清除布局里所有 Button 的默认着色 */
    public static void noTintAll(View root) {
        clearButtonTints(root);
    }

    private static void clearButtonTints(View v) {
        if (v instanceof android.widget.Button) noTint(v);
        if (v instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) v;
            for (int i = 0; i < group.getChildCount(); i++) {
                clearButtonTints(group.getChildAt(i));
            }
        }
    }

    // ==================== 动效 ====================

    public static void focus(View v, boolean focused) {
        // 只做缩放，不给 elevation：阴影会按视图的直角轮廓画出来，
        // 圆角卡片后面会多出一个淡淡的直角方框。
        v.animate()
                .scaleX(focused ? 1.02f : 1f)
                .scaleY(focused ? 1.02f : 1f)
                .setDuration(140)
                .start();
    }

    // ==================== 系统栏 ====================

    public static void applyBars(Activity activity) {
        clearButtonTints(activity.getWindow().getDecorView());
        Window window = activity.getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(color(activity, R.color.surface_grouped));
        }
        applyCutoutMode(window);
        applyIconMode(activity);
    }

    /** 横屏时让窗口铺满刘海那一溜，避免屏幕边上留一条黑边 */
    private static void applyCutoutMode(Window window) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return;
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        window.setAttributes(lp);
    }

    /** 侧边安全区回调：横屏时刘海/手势条会占住左右一侧，单位 px */
    public interface InsetsCallback {
        void onSideInsets(int left, int right, int bottom);
    }

    /** 横屏时被刘海（或侧边手势条）占住的宽度，布局计算统一用这个值让位 */
    private static int sNavSide;

    public static int navSide() {
        return sNavSide;
    }

    /**
     * 沉浸式：内容铺满整块屏幕（状态栏下方、刘海区域都绘制）。
     * 系统栏图标颜色仍按明暗主题切换。
     */
    public static void applyImmersive(Activity activity) {
        applyImmersive(activity, null);
    }

    /**
     * 沉浸式 + 侧边安全区回调。
     * <p>
     * 横屏时刘海会转到左侧（或右侧），默认窗口为了避开刘海会整体让出一段，
     * 看起来就是屏幕边上一条黑边。这里用 SHORT_EDGES 让窗口铺满，
     * 再通过回调把让位的宽度告诉页面，自行给图标/文字留白。
     */
    public static void applyImmersive(Activity activity, final InsetsCallback callback) {
        final Window window = activity.getWindow();
        final View decor = window.getDecorView();
        clearButtonTints(decor);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.setNavigationBarDividerColor(Color.TRANSPARENT);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.setNavigationBarContrastEnforced(false);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            applyCutoutMode(window);
        }
        int flags = decor.getSystemUiVisibility()
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        decor.setSystemUiVisibility(flags);
        applyIconMode(activity);

        if (callback == null) return;
        ViewCompat.setOnApplyWindowInsetsListener(decor,
                new androidx.core.view.OnApplyWindowInsetsListener() {
                    @Override
                    public WindowInsetsCompat onApplyWindowInsets(View v, WindowInsetsCompat insets) {
                        Insets cut = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
                        Insets nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
                        // 系统导航栏（底部手势条）窗口本身已经让开了，这里只关心刘海那一溜
                        boolean land = isLandscape(activity);
                        int left = land ? cut.left : 0;
                        int right = land ? cut.right : 0;
                        int bottom = land ? 0 : nav.bottom;
                        sNavSide = left;
                        callback.onSideInsets(left, right, bottom);
                        return insets;
                    }
                });
        ViewCompat.requestApplyInsets(decor);
    }

    private static void applyIconMode(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        boolean night = ThemeMode.isNight(activity);
        View decor = activity.getWindow().getDecorView();
        int flags = decor.getSystemUiVisibility();
        if (night) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        else flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (night) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            else flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        decor.setSystemUiVisibility(flags);
    }

    /** 状态栏高度：拿不到时给个兜底，避免顶部内容压到状态栏上 */
    public static int statusBarHeight(Context c) {
        int id = c.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (id > 0) {
            int h = c.getResources().getDimensionPixelSize(id);
            if (h > 0) return h;
        }
        return dp(c, 24);
    }

    public static int screenWidth(Context c) {
        return c.getResources().getDisplayMetrics().widthPixels;
    }

    public static int screenHeight(Context c) {
        return c.getResources().getDisplayMetrics().heightPixels;
    }

    public static boolean isLandscape(Context c) {
        return c.getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
    }

    /**
     * 内容左边缘：
     * 横屏是「手势条安全区 + 左侧图标竖栏 + 页面留白」，
     * 竖屏没有左侧栏（图标在底部），只有页面留白。
     */
    public static int railInset(Context c) {
        if (!isLandscape(c)) return dimen(c, R.dimen.screen_pad);
        return sNavSide + dimen(c, R.dimen.rail_width) + dimen(c, R.dimen.rail_gap);
    }

    /** 竖屏底部图标栏占用的高度（横屏为 0） */
    public static int bottomBarHeight(Context c) {
        return isLandscape(c) ? 0 : dimen(c, R.dimen.nav_bottom_height);
    }

    /** 可用于排版的宽度（扣掉横屏手势条那一溜） */
    public static int contentWidth(Context c) {
        return screenWidth(c) - sNavSide;
    }

    public static int contentWidth(Context c, boolean rail) {
        int w = contentWidth(c);
        if (rail) w -= dimen(c, R.dimen.nav_rail_width);
        return w - dimen(c, R.dimen.screen_pad) * 2;
    }

    /** 电视/横屏：内容是否铺满全宽（大图、横向卡片行使用） */
    public static int contentWidthPx(Context c, boolean rail) {
        int w = screenWidth(c);
        if (rail) w -= dimen(c, R.dimen.nav_rail_width);
        return w;
    }
}
