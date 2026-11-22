package com.fntv.app.ui.home;

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
 * 继续观看：16:9 宽幅卡片，底部进度条，标题在卡片下方（Netflix 布局）。
 */
public class ContinueAdapter extends RecyclerView.Adapter<ContinueAdapter.Holder> {

    public interface Listener {
        void onItem(PlayListItem item);
    }

    private final Context context;
    private final String baseUrl;
    private final int widthPx;
    private final Listener listener;
    private final List<PlayListItem> items = new ArrayList<>();

    public ContinueAdapter(Context context, String baseUrl, int widthPx, Listener listener) {
        this.context = context;
        this.baseUrl = baseUrl;
        this.widthPx = widthPx;
        this.listener = listener;
    }

    public void submit(List<PlayListItem> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(build(context, widthPx));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        PlayListItem item = items.get(position);
        holder.title.setText(Fmt.continueTitle(item));

        String url = Urls.poster(baseUrl, item.poster, 600);
        holder.poster.setImageBitmap(null);
        holder.poster.setBackgroundColor(Ui.color(context, R.color.image_placeholder));
        if (url != null) {
            SimpleImageLoader.load(url, holder.poster, FnApiManager.getInstance().getClient());
        }

        if (item.episodeNumber > 0) {
            holder.badge.setVisibility(View.VISIBLE);
            holder.badge.setText("第 " + item.episodeNumber + " 集");
        } else {
            holder.badge.setVisibility(View.GONE);
        }
        int pct = item.duration > 0
                ? (int) (Math.min(item.ts, item.duration) * 100 / item.duration) : 0;
        holder.progress.setWeightSum(100f);
        holder.progress.removeAllViews();
        if (pct > 0) {
            View fill = new View(context);
            fill.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, pct));
            fill.setBackgroundDrawable(Ui.round(Ui.color(context, R.color.accent), 0));
            holder.progress.addView(fill);
        }
        if (pct < 100) {
            View rest = new View(context);
            rest.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT,
                    Math.max(1, 100 - pct)));
            holder.progress.addView(rest);
        }

        holder.sub.setVisibility(View.GONE);

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
        final RoundedImageView poster;
        final TextView title;
        final TextView sub;
        final TextView badge;
        final LinearLayout progress;

        Holder(View view) {
            super(view);
            root = (LinearLayout) view;
            FrameLayout thumb = (FrameLayout) root.getChildAt(0);
            poster = (RoundedImageView) thumb.getChildAt(0);
            badge = (TextView) thumb.getChildAt(1);
            progress = (LinearLayout) thumb.getChildAt(2);
            title = (TextView) root.getChildAt(1);
            sub = (TextView) root.getChildAt(2);
        }
    }

    private View build(Context c, int cardWidth) {
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFocusable(true);
        root.setClipChildren(false);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            root.setDefaultFocusHighlightEnabled(false);
        }
        root.setLayoutParams(new RecyclerView.LayoutParams(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout thumb = new FrameLayout(c);
        thumb.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.round(cardWidth * 9f / 16f)));
        thumb.setBackground(Ui.round(0, Ui.dp(c, 4)));
        if (android.os.Build.VERSION.SDK_INT >= 21) thumb.setClipToOutline(true);
        root.addView(thumb);

        RoundedImageView poster = new RoundedImageView(c);
        poster.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setCornerRadius(4);
        poster.setBackgroundColor(Ui.color(c, R.color.image_placeholder));
        thumb.addView(poster);

        TextView badge = Ui.text(c, "", 10f, R.color.text_white);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setBackgroundResource(R.drawable.bg_badge);
        badge.setPadding(Ui.dp(c, 6), Ui.dp(c, 2), Ui.dp(c, 6), Ui.dp(c, 2));
        badge.setVisibility(View.GONE);
        FrameLayout.LayoutParams badgeLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeLp.gravity = Gravity.TOP | Gravity.LEFT;
        badgeLp.leftMargin = Ui.dp(c, 8);
        badgeLp.topMargin = Ui.dp(c, 8);
        thumb.addView(badge, badgeLp);

        LinearLayout progress = new LinearLayout(c);
        progress.setOrientation(LinearLayout.HORIZONTAL);
        progress.setBackgroundColor(0x59FFFFFF);
        FrameLayout.LayoutParams pLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 5));
        pLp.gravity = Gravity.BOTTOM;
        thumb.addView(progress, pLp);

        TextView title = new TextView(c);
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                Ui.dimen(c, R.dimen.card_title_size) + 1);
        title.setTextColor(Ui.color(c, R.color.label));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setIncludeFontPadding(false);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = Ui.dp(c, 8);
        title.setLayoutParams(titleLp);
        root.addView(title);

        TextView sub = new TextView(c);
        sub.setTextSize(11f);
        sub.setTextColor(Ui.color(c, R.color.label_3));
        sub.setIncludeFontPadding(false);
        sub.setSingleLine(true);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = Ui.dp(c, 3);
        sub.setLayoutParams(subLp);
        root.addView(sub);

        View ring = new View(c);
        GradientDrawable ringBg = new GradientDrawable();
        ringBg.setShape(GradientDrawable.RECTANGLE);
        ringBg.setColor(0);
        ringBg.setCornerRadius(Ui.dp(c, 4));
        ringBg.setStroke(Math.max(2, Ui.dp(c, 2f)), Ui.color(c, R.color.accent));
        ring.setBackground(ringBg);
        ring.setVisibility(View.INVISIBLE);
        thumb.addView(ring, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        root.setOnFocusChangeListener((v, focused) -> {
            Ui.focus(root, focused);
            ring.setVisibility(focused ? View.VISIBLE : View.INVISIBLE);
            thumb.animate().scaleX(focused ? 1.03f : 1f).scaleY(focused ? 1.03f : 1f)
                    .setDuration(150).start();
        });
        return root;
    }
}
