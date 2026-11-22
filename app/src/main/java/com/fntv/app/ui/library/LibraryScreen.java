package com.fntv.app.ui.library;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.fntv.app.R;
import com.fntv.app.api.model.MediaDbItem;
import com.fntv.app.core.Fmt;
import com.fntv.app.ui.Ui;

import java.util.List;

/**
 * 媒体库列表（iOS 分组卡片风格）。
 */
public final class LibraryScreen {

    public interface Listener {
        void onLibrary(MediaDbItem library);
    }

    private LibraryScreen() {
    }

    public static void render(Activity activity, LinearLayout container,
                              List<MediaDbItem> libraries, Listener listener) {
        container.removeAllViews();

        TextView title = Ui.largeTitle(activity, "媒体库");
        title.setPadding(0, Ui.dp(activity, 4), 0, 0);
        container.addView(title);

        TextView subtitle = Ui.subhead(activity,
                libraries.isEmpty() ? "还没有媒体库" : "共 " + libraries.size() + " 个媒体库");
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = Ui.dp(activity, 6);
        subLp.bottomMargin = Ui.dp(activity, 16);
        subtitle.setLayoutParams(subLp);
        container.addView(subtitle);

        LinearLayout card = Ui.column(activity);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(0, 0, 0, 0);
        container.addView(card);

        for (int i = 0; i < libraries.size(); i++) {
            final MediaDbItem lib = libraries.get(i);
            card.addView(buildRow(activity, lib, listener, i, libraries.size()));
        }
    }

    private static View buildRow(Activity activity, final MediaDbItem lib,
                                 final Listener listener, int index, int count) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        // 和详情页选集列表一致：选中背景为直角并铺满整行，首行/末行圆角跟卡片一致
        if (count <= 1) {
            row.setBackgroundResource(R.drawable.bg_row_single);
        } else if (index == 0) {
            row.setBackgroundResource(R.drawable.bg_row_first);
        } else if (index == count - 1) {
            row.setBackgroundResource(R.drawable.bg_row_last);
        } else {
            row.setBackgroundResource(R.drawable.bg_row_mid);
        }
        row.setFocusable(true);
        row.setClickable(true);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            row.setDefaultFocusHighlightEnabled(false);
        }
        row.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 10),
                Ui.dp(activity, 12), Ui.dp(activity, 10));
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView icon = Ui.text(activity, Fmt.initial(lib.title), 16f, R.color.accent);
        icon.setTypeface(Typeface.DEFAULT_BOLD);
        icon.setGravity(Gravity.CENTER);
        icon.setBackgroundDrawable(Ui.round(Ui.color(activity, R.color.accent_soft), Ui.dp(activity, 10)));
        icon.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(activity, 40), Ui.dp(activity, 40)));
        content.addView(icon);

        LinearLayout text = Ui.column(activity);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textLp.leftMargin = Ui.dp(activity, 12);
        text.setLayoutParams(textLp);

        TextView name = Ui.text(activity, lib.title == null ? "媒体库" : lib.title, 16f, R.color.label);
        name.setSingleLine(true);
        text.addView(name);

        TextView sub = Ui.text(activity, (lib.category == null || lib.category.isEmpty()
                ? "媒体库" : lib.category) + "  ·  点击浏览", 12f, R.color.label_3);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = Ui.dp(activity, 3);
        sub.setLayoutParams(subLp);
        text.addView(sub);
        content.addView(text);

        row.addView(content);

        ImageView chevron = Ui.icon(activity, R.drawable.ic_chevron_right, 16, R.color.label_3);
        row.addView(chevron);

        row.setOnFocusChangeListener(Ui::focus);
        row.setOnClickListener(v -> {
            if (listener != null) listener.onLibrary(lib);
        });

        if (index == count - 1) return row;

        LinearLayout wrapper = Ui.column(activity);
        wrapper.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        wrapper.addView(row);
        wrapper.addView(Ui.divider(activity, 66));
        return wrapper;
    }
}
