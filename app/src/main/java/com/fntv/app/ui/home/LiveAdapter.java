package com.fntv.app.ui.home;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fntv.app.R;
import com.fntv.app.api.model.PlayListItem;
import com.fntv.app.ui.Ui;

import java.util.ArrayList;
import java.util.List;

/**
 * 直播频道卡片：与整体一致的克制配色（浅色卡片 + 蓝色标签）。
 */
public class LiveAdapter extends RecyclerView.Adapter<LiveAdapter.Holder> {

    public interface Listener {
        void onItem(PlayListItem item);
    }

    private final Context context;
    private final int widthPx;
    private final int heightPx;
    private final Listener listener;
    private final List<PlayListItem> items = new ArrayList<>();

    public LiveAdapter(Context context, int widthPx, int heightPx, Listener listener) {
        this.context = context;
        this.widthPx = widthPx;
        this.heightPx = heightPx;
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
        return new Holder(build(context, widthPx, heightPx));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        final PlayListItem item = items.get(position);
        String name = item.title != null && !item.title.isEmpty() ? item.title : "直播";
        holder.name.setText(name);
        holder.name.setTextSize(name.length() <= 3 ? 17f : 14f);
        holder.root.setOnClickListener(v -> {
            if (listener != null) listener.onItem(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final FrameLayout root;
        final TextView name;

        Holder(View view) {
            super(view);
            root = (FrameLayout) view;
            name = (TextView) root.getChildAt(0);
        }
    }

    private View build(Context c, int widthPx, int heightPx) {
        FrameLayout root = new FrameLayout(c);
        root.setFocusable(true);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            root.setDefaultFocusHighlightEnabled(false);
        }
        root.setBackgroundResource(R.drawable.bg_live_card);
        root.setLayoutParams(new RecyclerView.LayoutParams(widthPx, heightPx));

        TextView name = Ui.text(c, "", 16f, R.color.label);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 12), 0);
        FrameLayout.LayoutParams nameLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameLp.gravity = Gravity.CENTER;
        root.addView(name, nameLp);

        TextView badge = Ui.text(c, "直播", 10f, R.color.accent);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setBackgroundDrawable(Ui.round(Ui.color(c, R.color.accent_soft), Ui.dp(c, 6)));
        badge.setPadding(Ui.dp(c, 6), Ui.dp(c, 2), Ui.dp(c, 6), Ui.dp(c, 2));
        FrameLayout.LayoutParams badgeLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeLp.gravity = Gravity.TOP | Gravity.LEFT;
        badgeLp.leftMargin = Ui.dp(c, 10);
        badgeLp.topMargin = Ui.dp(c, 10);
        root.addView(badge, badgeLp);

        android.widget.ImageView play = Ui.icon(c, R.drawable.ic_play, 14, R.color.label_3);
        FrameLayout.LayoutParams playLp = new FrameLayout.LayoutParams(
                Ui.dp(c, 14), Ui.dp(c, 14));
        playLp.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        playLp.rightMargin = Ui.dp(c, 10);
        playLp.bottomMargin = Ui.dp(c, 10);
        root.addView(play, playLp);

        View ring = new View(c);
        GradientDrawable ringBg = new GradientDrawable();
        ringBg.setShape(GradientDrawable.RECTANGLE);
        ringBg.setColor(0);
        ringBg.setCornerRadius(Ui.dp(c, 16));
        ringBg.setStroke(Math.max(2, Ui.dp(c, 2f)), Ui.color(c, R.color.accent));
        ring.setBackground(ringBg);
        ring.setVisibility(View.INVISIBLE);
        root.addView(ring, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        root.setOnFocusChangeListener((v, focused) -> {
            Ui.focus(root, focused);
            ring.setVisibility(focused ? View.VISIBLE : View.INVISIBLE);
        });
        return root;
    }
}
