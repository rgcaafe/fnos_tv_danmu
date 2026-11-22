package com.fntv.app.ui.common;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fntv.app.R;
import com.fntv.app.RoundedImageView;
import com.fntv.app.api.FnApiManager;
import com.fntv.app.api.model.PlayListItem;
import com.fntv.app.core.Fmt;
import com.fntv.app.core.Urls;
import com.fntv.app.ui.Ui;
import com.fntv.app.util.SimpleImageLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * 2:3 海报卡片（Netflix TV 风格）：
 * 默认只显示海报本身（标题通常已经画在海报里），聚焦时描边并轻微放大。
 */
public class PosterAdapter extends RecyclerView.Adapter<PosterAdapter.Holder> {

    public interface Listener {
        void onItem(PlayListItem item);
    }

    private final Context context;
    private final String baseUrl;
    private final int cardWidthPx;
    private final boolean showCaption;
    private final boolean wide;
    private final Listener listener;
    /** 图片圆角（dp）；比描边框稍大，卡片角上的深色图片像素不会从蓝框外侧露出 */
    private float imageCornerDp = 4f;
    /** 聚焦描边框的圆角（dp） */
    private float ringCornerDp = 4f;
    private final List<PlayListItem> items = new ArrayList<>();
    private boolean showMeta = true;

    /** 是否显示卡片下方的副标题（集数 / 时长）；只留名字时可关掉 */
    public void setShowMeta(boolean showMeta) {
        this.showMeta = showMeta;
    }

    /** 图片圆角（dp）：比蓝色描边框略大，避免深色海报的角从框外露出 */
    public void setImageCornerDp(float dp) {
        this.imageCornerDp = dp;
    }

    /** 聚焦描边框圆角（dp） */
    public void setRingCornerDp(float dp) {
        this.ringCornerDp = dp;
    }

    public PosterAdapter(Context context, String baseUrl, int cardWidthPx, Listener listener) {
        this(context, baseUrl, cardWidthPx, false, false, listener);
    }

    public PosterAdapter(Context context, String baseUrl, int cardWidthPx,
                         boolean showCaption, Listener listener) {
        this(context, baseUrl, cardWidthPx, showCaption, false, listener);
    }

    /**
     * @param wide true 时卡片按 16:9 摆（剧集/视频的横幅剧照），否则 2:3 海报
     */
    public PosterAdapter(Context context, String baseUrl, int cardWidthPx,
                         boolean showCaption, boolean wide, Listener listener) {
        this.context = context;
        this.baseUrl = baseUrl;
        this.cardWidthPx = cardWidthPx;
        this.showCaption = showCaption;
        this.wide = wide;
        this.listener = listener;
    }

    public void submit(List<PlayListItem> data) {        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    public int size() {
        return items.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(build(context, cardWidthPx));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        final PlayListItem item = items.get(position);

        String url = Urls.poster(baseUrl, item.poster, 400);
        holder.poster.setImageBitmap(null);
        if (url != null) {
            holder.placeholder.setVisibility(View.GONE);
            SimpleImageLoader.load(url, holder.poster, FnApiManager.getInstance().getClient());
        } else {
            holder.placeholder.setVisibility(View.VISIBLE);
            holder.placeholder.setText(Fmt.initial(item.title));
        }

        boolean folder = "Directory".equals(item.type) || "Folder".equals(item.type);
        holder.tag.setVisibility(folder ? View.VISIBLE : View.GONE);

        if (holder.title != null) {
            holder.title.setText(item.title != null ? item.title : "未知");
        }
        if (holder.meta != null) {
            String meta = Fmt.cardMeta(item);
            holder.meta.setText(meta);
            holder.meta.setVisibility(meta.isEmpty() ? View.GONE : View.VISIBLE);
        }

        holder.root.setOnClickListener(v -> {
            if (listener != null) listener.onItem(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final LinearLayout root;
        final FrameLayout box;
        final RoundedImageView poster;
        final TextView placeholder;
        final TextView tag;
        final View ring;
        final TextView title;
        final TextView meta;

        Holder(View view) {
            super(view);
            root = (LinearLayout) view;
            box = (FrameLayout) root.getChildAt(0);
            poster = (RoundedImageView) box.getChildAt(0);
            placeholder = (TextView) box.getChildAt(1);
            tag = (TextView) box.getChildAt(2);
            ring = box.getChildAt(3);
            title = root.getChildCount() > 1 ? (TextView) root.getChildAt(1) : null;
            meta = root.getChildCount() > 2 ? (TextView) root.getChildAt(2) : null;
        }
    }

    private View build(Context c, int widthPx) {
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFocusable(true);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            root.setDefaultFocusHighlightEnabled(false);
        }
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.setLayoutParams(new RecyclerView.LayoutParams(
                widthPx, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout box = new FrameLayout(c);
        box.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Math.round(widthPx * (wide ? 9f / 16f : 1.5f))));
        float cornerPx = Ui.dp(c, imageCornerDp);
        box.setBackground(Ui.round(Ui.color(c, R.color.image_placeholder), cornerPx));
        if (android.os.Build.VERSION.SDK_INT >= 21) box.setClipToOutline(true);
        root.addView(box);

        RoundedImageView poster = new RoundedImageView(c);
        poster.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setCornerRadiusPx(cornerPx);
        poster.setBackgroundColor(Ui.color(c, R.color.image_placeholder));
        box.addView(poster);

        TextView placeholder = Ui.text(c, "", 20f, R.color.label_3);
        placeholder.setTypeface(Typeface.DEFAULT_BOLD);
        placeholder.setGravity(Gravity.CENTER);
        placeholder.setVisibility(View.GONE);
        box.addView(placeholder, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView tag = Ui.text(c, "文件夹", 10f, R.color.label);
        tag.setBackgroundResource(R.drawable.bg_badge);
        tag.setPadding(Ui.dp(c, 5), Ui.dp(c, 1), Ui.dp(c, 5), Ui.dp(c, 1));
        tag.setVisibility(View.GONE);
        FrameLayout.LayoutParams tagLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tagLp.gravity = Gravity.BOTTOM | Gravity.LEFT;
        tagLp.leftMargin = Ui.dp(c, 6);
        tagLp.bottomMargin = Ui.dp(c, 6);
        box.addView(tag, tagLp);

        View ring = new View(c);
        GradientDrawable ringBg = new GradientDrawable();
        ringBg.setShape(GradientDrawable.RECTANGLE);
        ringBg.setColor(0);
        ringBg.setCornerRadius(Ui.dp(c, ringCornerDp));
        ringBg.setStroke(Math.max(2, Ui.dp(c, 2f)), Ui.color(c, R.color.accent));
        ring.setBackground(ringBg);
        ring.setVisibility(View.INVISIBLE);
        box.addView(ring, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (showCaption) {
            TextView title = new TextView(c);
            title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                    Ui.dimen(c, R.dimen.card_title_size));
            title.setTextColor(Ui.color(c, R.color.label));
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            titleLp.topMargin = Ui.dp(c, 7);
            title.setLayoutParams(titleLp);
            root.addView(title);

            TextView meta = new TextView(c);
            meta.setTextSize(11f);
            meta.setTextColor(Ui.color(c, R.color.label_3));
            meta.setIncludeFontPadding(false);
            meta.setSingleLine(true);
            LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            metaLp.topMargin = Ui.dp(c, 2);
            meta.setLayoutParams(metaLp);
            if (showMeta) root.addView(meta);
            else meta = null;
        }

        root.setOnFocusChangeListener((v, focused) -> {
            root.animate().scaleX(focused ? 1.06f : 1f).scaleY(focused ? 1.06f : 1f)
                    .setDuration(150).start();
            ring.setVisibility(focused ? View.VISIBLE : View.INVISIBLE);
        });
        return root;
    }
}
