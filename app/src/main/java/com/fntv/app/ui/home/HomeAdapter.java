package com.fntv.app.ui.home;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fntv.app.R;
import com.fntv.app.RoundedImageView;
import com.fntv.app.api.FnApiManager;
import com.fntv.app.api.model.ItemMeta;
import com.fntv.app.api.model.PlayListItem;
import com.fntv.app.core.Fmt;
import com.fntv.app.core.Urls;
import com.fntv.app.ui.Ui;
import com.fntv.app.ui.common.ClippedRecyclerView;
import com.fntv.app.ui.common.PosterAdapter;
import com.fntv.app.ui.common.SpaceDecoration;
import com.fntv.app.util.SimpleImageLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * 首页适配器（Netflix TV 结构）：
 * 大图推荐位（横屏时文字在左、剧照在右）+ 横向卡片行。
 * 卡片行左右出血，左侧为图标导航让出位置。
 */
public class HomeAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface Listener {
        void onBillboard(PlayListItem item);

        /** 从头播放（大图推荐位的“重新播放 / 从第 N 集开始”） */
        void onRestart(PlayListItem item);

        void onItem(PlayListItem item);

        void onSection(HomeItem section);
    }

    private final Context context;
    private final String baseUrl;
    private final Listener listener;
    private final List<HomeItem> items = new ArrayList<>();
    private ItemMeta heroMeta;
    private String heroSeasonGuid;
    private TextView heroRestart;
    /** 季 guid → 本季第一集的集号，重新绑定时也用它 */
    private final java.util.Map<String, Integer> seasonStartEpisodes = new java.util.HashMap<>();

    public HomeAdapter(Context context, String baseUrl, Listener listener) {
        this.context = context;
        this.baseUrl = baseUrl;
        this.listener = listener;
    }

    public void submit(List<HomeItem> data) {
        List<HomeItem> next = data == null ? new ArrayList<>() : data;
        // 行数不变时只刷新变化的行：整表 notifyDataSetChanged 会把遥控器焦点和滚动位置打回原处，
        // 后台加载每返回一个媒体库就跳一次，遥控器基本没法用。
        if (next.size() == items.size()) {
            for (int i = 0; i < next.size(); i++) {
                HomeItem old = items.get(i);
                HomeItem now = next.get(i);
                if (sameContent(old, now)) continue;
                items.set(i, now);
                notifyItemChanged(i);
            }
            return;
        }
        items.clear();
        items.addAll(next);
        notifyDataSetChanged();
    }

    private boolean sameContent(HomeItem a, HomeItem b) {
        if (a == b) return true;
        if (a.type != b.type) return false;
        if (!TextUtils.equals(a.title, b.title)) return false;
        if (!TextUtils.equals(a.tip, b.tip)) return false;
        if (!TextUtils.equals(a.eyebrow, b.eyebrow)) return false;
        if (!TextUtils.equals(a.guid, b.guid)) return false;
        if (a.item != b.item) return false;
        if (a.items == b.items) return true;
        if (a.items == null || b.items == null) return false;
        if (a.items.size() != b.items.size()) return false;
        for (int i = 0; i < a.items.size(); i++) {
            if (a.items.get(i) != b.items.get(i)) return false;
        }
        return true;
    }

    public int typeAt(int position) {
        if (position < 0 || position >= items.size()) return -1;
        return items.get(position).type;
    }

    /** 大图副信息（分级 / 类型 / 国家 / 媒体库），异步拿到后刷新第一格 */
    public void setHeroMeta(ItemMeta meta) {
        this.heroMeta = meta;
    }

    /** 拿到本季第一集后，把「从第 X 集开始」改成真正的第一集 */
    public void setSeasonStartEpisode(String seasonGuid, PlayListItem first) {
        if (first == null || seasonGuid == null || first.episodeNumber <= 0) return;
        seasonStartEpisodes.put(seasonGuid, first.episodeNumber);
        if (heroRestart != null && seasonGuid.equals(heroSeasonGuid)) {
            heroRestart.setText("从第 " + first.episodeNumber + " 集开始");
        }
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context c = parent.getContext();
        switch (viewType) {
            case HomeItem.TYPE_BILLBOARD:
                return new BillboardHolder(buildBillboard(c));
            case HomeItem.TYPE_ROW_TITLE:
                return new RowTitleHolder(buildRowTitle(c));
            case HomeItem.TYPE_POSTERS:
                return new RowHolder(buildRow(c, 0));
            case HomeItem.TYPE_CONTINUE:
                return new RowHolder(buildRow(c, 1));
            case HomeItem.TYPE_LIVE:
                return new RowHolder(buildRow(c, 2));
            case HomeItem.TYPE_LOADING:
                return new LoadingHolder(buildLoading(c));
            default:
                return new TipHolder(buildTip(c));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        HomeItem item = items.get(position);
        if (holder instanceof BillboardHolder) {
            bindBillboard(((BillboardHolder) holder).views, item);
        } else if (holder instanceof RowTitleHolder) {
            RowTitleHolder h = (RowTitleHolder) holder;
            h.title.setText(item.title == null ? "" : item.title);
            boolean openable = item.guid != null;
            h.more.setVisibility(openable ? View.VISIBLE : View.GONE);
            h.more.setOnClickListener(openable ? v -> {
                if (listener != null) listener.onSection(item);
            } : null);
        } else if (holder instanceof RowHolder) {
            bindRow((RowHolder) holder, item);
        } else if (holder instanceof TipHolder) {
            ((TipHolder) holder).text.setText(item.tip == null ? "" : item.tip);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // ==================== 大图推荐位 ====================

    private View buildBillboard(Context c) {
        return Ui.isLandscape(c) ? buildHeroWide(c) : buildHeroTall(c);
    }

    /** 横屏 / 电视：文字在左，剧照在右，底部渐隐到页面背景 */
    private View buildHeroWide(Context c) {
        final int w = Ui.screenWidth(c);
        final int h = Ui.screenHeight(c);
        final int heroH = Math.round(h * 0.72f);
        final int surface = Ui.color(c, R.color.surface_grouped);

        FrameLayout root = new FrameLayout(c);
        root.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, heroH));
        root.setBackgroundColor(surface);

        ImageView art = new ImageView(c);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setBackgroundColor(Ui.color(c, R.color.image_placeholder));
        FrameLayout.LayoutParams artLp = new FrameLayout.LayoutParams(
                Math.round(w * 0.62f), ViewGroup.LayoutParams.MATCH_PARENT);
        artLp.gravity = Gravity.RIGHT;
        root.addView(art, artLp);

        // 左侧由页面背景色渐隐到透明，托住文字列
        View leftFade = new View(c);
        leftFade.setBackground(Ui.gradientH(
                new int[]{surface, surface, 0x00FFFFFF & surface, 0x00FFFFFF & surface},
                new float[]{0f, 0.40f, 0.52f, 1f}));
        root.addView(leftFade, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 顶部（状态栏）与底部收边
        addFade(root, c, true, surface);
        int fadeH = Math.round(heroH * 0.13f);
        View bottomFade = new View(c);
        bottomFade.setBackground(Ui.gradientV(
                new int[]{0x00FFFFFF & surface, surface}, null));
        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, fadeH);
        bottomLp.gravity = Gravity.BOTTOM;
        root.addView(bottomFade, bottomLp);

        HeroViews views = buildHeroInfo(c);
        FrameLayout.LayoutParams infoLp = new FrameLayout.LayoutParams(
                Math.round(w * 0.36f), ViewGroup.LayoutParams.WRAP_CONTENT);
        infoLp.gravity = Gravity.BOTTOM | Gravity.LEFT;
        infoLp.leftMargin = Ui.railInset(c);
        infoLp.bottomMargin = Math.round(heroH * 0.10f);
        root.addView(views.info, infoLp);

        views.art = art;
        root.setTag(views);
        return root;
    }

    /**
     * 竖屏：16:9 剧照在上，文字块上提并与剧照下缘重叠，
     * 剧照底部的白色渐隐托住文字，既不显得图片占满屏、也不丢可读性。
     */
    private View buildHeroTall(Context c) {
        final int w = Ui.screenWidth(c);
        final int artH = Math.round(w * 9f / 16f);
        final int surface = Ui.color(c, R.color.surface_grouped);

        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout artBox = new FrameLayout(c);
        artBox.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, artH));
        root.addView(artBox);

        ImageView art = new ImageView(c);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setBackgroundColor(Ui.color(c, R.color.image_placeholder));
        artBox.addView(art, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 下缘渐隐：透明 → 页面背景色，文字就压在这段渐隐上
        View bottomFade = new View(c);
        bottomFade.setBackground(Ui.gradientV(
                new int[]{0x00FFFFFF & surface, veil(surface, 0.45f),
                          veil(surface, 0.80f), surface},
                new float[]{0f, 0.25f, 0.55f, 0.80f}));
        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.round(artH * 0.62f));
        bottomLp.gravity = Gravity.BOTTOM;
        artBox.addView(bottomFade, bottomLp);

        // 顶部渐隐：保证状态栏图标在剧照上可读（半透明罩子，图片依然透出来）
        View topFade = new View(c);
        topFade.setBackground(Ui.gradientV(
                new int[]{veil(surface, 0.70f), veil(surface, 0.22f), 0x00FFFFFF & surface},
                new float[]{0f, 0.55f, 1f}));
        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.statusBarHeight(c) + Ui.dp(c, 22));
        topLp.gravity = Gravity.TOP;
        artBox.addView(topFade, topLp);

        HeroViews views = buildHeroInfo(c);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        infoLp.topMargin = -Math.round(artH * 0.20f);
        root.addView(views.info, infoLp);

        views.art = art;
        root.setTag(views);
        return root;
    }

    /** 顶部渐隐到页面背景，保证状态栏图标可读（半透明，剧照仍可见） */
    private void addFade(FrameLayout root, Context c, boolean top, int surface) {
        View fade = new View(c);
        if (top) {
            fade.setBackground(Ui.gradientV(
                    new int[]{veil(surface, 0.70f), veil(surface, 0.22f),
                            0x00FFFFFF & surface},
                    new float[]{0f, 0.55f, 1f}));
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.statusBarHeight(c) + Ui.dp(c, 20));
        lp.gravity = Gravity.TOP;
        root.addView(fade, lp);
    }

    /** 同色半透明（用于盖在剧照上的状态栏罩子） */
    private static int veil(int color, float alpha) {
        return (color & 0x00FFFFFF) | (Math.round(Math.max(0f, Math.min(1f, alpha)) * 255) << 24);
    }

    private HeroViews buildHeroInfo(Context c) {
        LinearLayout info = new LinearLayout(c);
        info.setOrientation(LinearLayout.VERTICAL);
        if (!Ui.isLandscape(c)) {
            info.setPadding(Ui.railInset(c), Ui.dp(c, 2),
                    Ui.dimen(c, R.dimen.screen_pad), Ui.dp(c, 10));
        }

        TextView eyebrow = new TextView(c);
        eyebrow.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                Ui.dimen(c, R.dimen.hero_eyebrow_size));
        eyebrow.setTextColor(Ui.color(c, R.color.accent));
        eyebrow.setTypeface(Typeface.DEFAULT_BOLD);
        if (android.os.Build.VERSION.SDK_INT >= 21) eyebrow.setLetterSpacing(0.12f);
        eyebrow.setSingleLine(true);
        eyebrow.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(eyebrow);

        TextView title = new TextView(c);
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                Ui.dimen(c, R.dimen.hero_title_size));
        title.setTextColor(Ui.color(c, R.color.label));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = Ui.dp(c, 4);
        title.setLayoutParams(titleLp);
        info.addView(title);

        LinearLayout badges = new LinearLayout(c);
        badges.setOrientation(LinearLayout.HORIZONTAL);
        badges.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeLp.topMargin = Ui.dp(c, 8);
        badges.setLayoutParams(badgeLp);
        info.addView(badges);

        TextView meta = new TextView(c);
        meta.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                Ui.dimen(c, R.dimen.hero_meta_size));
        meta.setTextColor(Ui.color(c, R.color.label_2));
        meta.setSingleLine(true);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        metaLp.topMargin = Ui.dp(c, 7);
        meta.setLayoutParams(metaLp);
        info.addView(meta);

        TextView overview = new TextView(c);
        overview.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                Ui.dimen(c, R.dimen.hero_overview_size));
        overview.setTextColor(Ui.color(c, R.color.label_2));
        overview.setMaxLines(Ui.isLandscape(c) ? 2 : 3);
        overview.setEllipsize(TextUtils.TruncateAt.END);
        overview.setLineSpacing(Ui.dp(c, 3), 1f);
        LinearLayout.LayoutParams ovLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ovLp.topMargin = Ui.dp(c, 8);
        overview.setLayoutParams(ovLp);
        info.addView(overview);

        // 主操作：胶囊播放按钮，右侧内嵌续播进度
        int playH = Ui.dimen(c, R.dimen.hero_play_height);
        LinearLayout playBar = new LinearLayout(c);
        playBar.setOrientation(LinearLayout.HORIZONTAL);
        playBar.setGravity(Gravity.CENTER_VERTICAL);
        playBar.setBackground(Ui.round(Ui.color(c, R.color.label), playH / 2));
        playBar.setFocusable(true);
        playBar.setClickable(true);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            playBar.setDefaultFocusHighlightEnabled(false);
        }
        playBar.setPadding(Ui.dp(c, 20), 0, Ui.dp(c, 20), 0);
        LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams(
                Ui.isLandscape(c) ? ViewGroup.LayoutParams.WRAP_CONTENT
                        : ViewGroup.LayoutParams.MATCH_PARENT, playH);
        playLp.topMargin = Ui.dp(c, 14);
        playBar.setLayoutParams(playLp);
        playBar.setOnFocusChangeListener((v, focused) -> {
            v.setBackground(focused
                    ? Ui.round(Ui.color(c, R.color.label), playH / 2f,
                               Ui.color(c, R.color.accent), Ui.dp(c, 2))
                    : Ui.round(Ui.color(c, R.color.label), playH / 2f));
        });

        ImageView playIcon = Ui.icon(c, R.drawable.ic_play,
                Ui.isLandscape(c) ? 13 : 15, R.color.surface_grouped);
        playBar.addView(playIcon);

        TextView playText = new TextView(c);
        playText.setTextSize(14f);
        playText.setTextColor(Ui.color(c, R.color.surface_grouped));
        playText.setTypeface(Typeface.DEFAULT_BOLD);
        playText.setSingleLine(true);
        playText.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams ptLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ptLp.leftMargin = Ui.dp(c, 8);
        playText.setLayoutParams(ptLp);
        playBar.addView(playText);

        LinearLayout track = new LinearLayout(c);
        track.setOrientation(LinearLayout.HORIZONTAL);
        track.setBackground(Ui.round(
                veil(Ui.color(c, R.color.surface_grouped), 0.28f), Ui.dp(c, 2)));
        LinearLayout.LayoutParams trackLp = new LinearLayout.LayoutParams(
                Ui.dp(c, Ui.isLandscape(c) ? 64 : 72), Math.max(3, Ui.dp(c, 3)));
        trackLp.leftMargin = Ui.dp(c, 16);
        track.setLayoutParams(trackLp);
        if (!Ui.isLandscape(c)) {
            // 竖屏：按钮占满内容宽，进度条靠右
            View spacer = new View(c);
            spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
            playBar.addView(spacer);
        }
        playBar.addView(track);

        info.addView(playBar);

        LinearLayout actions = new LinearLayout(c);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (Ui.isLandscape(c)) {
            actions.setOrientation(LinearLayout.VERTICAL);
            actionsLp.topMargin = Ui.dp(c, 2);
            actions.setLayoutParams(actionsLp);

            LinearLayout restartRow = heroAction(c, R.drawable.ic_replay, "");
            actions.addView(restartRow);
            LinearLayout episodesRow = heroAction(c, R.drawable.ic_episodes, "剧集与更多信息");
            actions.addView(episodesRow);

            // 显式串起大图内部的上下焦点，避免被页面层的按键拦截挡住
            playBar.setId(View.generateViewId());
            restartRow.setId(View.generateViewId());
            episodesRow.setId(View.generateViewId());
            restartRow.setNextFocusUpId(playBar.getId());
            episodesRow.setNextFocusUpId(restartRow.getId());
            playBar.setNextFocusDownId(restartRow.getId());
            restartRow.setNextFocusDownId(episodesRow.getId());

            TextView restart = (TextView) restartRow.getChildAt(1);
            TextView episodes = (TextView) episodesRow.getChildAt(1);
            restartRow.setOnClickListener(v -> restart.performClick());
            episodesRow.setOnClickListener(v -> episodes.performClick());
            info.addView(actions);

            HeroViews views = new HeroViews();
            views.info = info;
            views.eyebrow = eyebrow;
            views.title = title;
            views.badges = badges;
            views.meta = meta;
            views.overview = overview;
            views.playBar = playBar;
            views.playText = playText;
            views.track = track;
            views.restart = restart;
            views.episodes = episodes;
            return views;
        }

        // 竖屏：两个并排的次要操作按钮，避免文字行悬在页面里显散
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actionsLp.topMargin = Ui.dp(c, 10);
        actions.setLayoutParams(actionsLp);

        LinearLayout restartRow = heroAction(c, R.drawable.ic_replay, "", true);
        LinearLayout.LayoutParams r1 = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        r1.rightMargin = Ui.dp(c, 10);
        actions.addView(restartRow, r1);

        LinearLayout episodesRow = heroAction(c, R.drawable.ic_episodes, "剧集与更多信息", true);
        actions.addView(episodesRow, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        info.addView(actions);

        TextView restart = (TextView) restartRow.getChildAt(1);
        TextView episodes = (TextView) episodesRow.getChildAt(1);
        restartRow.setOnClickListener(v -> restart.performClick());
        episodesRow.setOnClickListener(v -> episodes.performClick());

        HeroViews views = new HeroViews();
        views.info = info;
        views.eyebrow = eyebrow;
        views.title = title;
        views.badges = badges;
        views.meta = meta;
        views.overview = overview;
        views.playBar = playBar;
        views.playText = playText;
        views.track = track;
        views.restart = restart;
        views.episodes = episodes;
        return views;
    }

    private LinearLayout heroAction(Context c, int iconRes, String text) {
        return heroAction(c, iconRes, text, false);
    }

    /**
     * @param ghost true 时做成描边小按钮（竖屏两个并排），false 时是左图标 + 文字的设置行（横屏）
     */
    private LinearLayout heroAction(Context c, int iconRes, String text, boolean ghost) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Ui.isLandscape(c) || !ghost ? Gravity.CENTER_VERTICAL : Gravity.CENTER);
        row.setFocusable(true);
        row.setClickable(true);
        if (ghost) {
            row.setBackground(ghostBg(c, false));
            row.setPadding(Ui.dp(c, 8), Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 10));
        } else {
            row.setPadding(0, Ui.dp(c, Ui.isLandscape(c) ? 7 : 9), 0,
                    Ui.dp(c, Ui.isLandscape(c) ? 7 : 9));
        }
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ghost ? ViewGroup.LayoutParams.MATCH_PARENT
                        : ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        AppCompatImageView icon = new AppCompatImageView(c);
        icon.setImageResource(iconRes);
        icon.setColorFilter(Ui.color(c, R.color.label_2));
        int size = Ui.dimen(c, R.dimen.hero_action_icon);
        if (ghost) size = Math.round(size * 0.85f);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(size, size);
        iconLp.rightMargin = Ui.dp(c, ghost ? 6 : 8);
        row.addView(icon, iconLp);

        TextView label = new TextView(c);
        label.setText(text);
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                Ui.dimen(c, R.dimen.hero_action_size));
        label.setTextColor(Ui.color(c, R.color.label_2));
        label.setIncludeFontPadding(false);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (!ghost) {
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                row.setDefaultFocusHighlightEnabled(false);
            }
            // 聚焦框只包住图标 + 文字，不要横贯整行
            row.setPadding(0, row.getPaddingTop(), Ui.dp(c, 12), row.getPaddingBottom());
        }

        row.setOnFocusChangeListener((v, focused) -> {
            v.setAlpha(focused ? 1f : 0.78f);
            label.setTextColor(Ui.color(c, focused ? R.color.label : R.color.label_2));
            icon.setColorFilter(Ui.color(c, focused ? R.color.label : R.color.label_2));
            if (ghost) {
                v.setBackground(ghostBg(c, focused));
            } else {
                v.setBackground(focused
                        ? Ui.round(0, Ui.dp(c, 8),
                                   Ui.color(c, R.color.accent), Ui.dp(c, 1.5f))
                        : null);
            }
        });
        if (!ghost && android.os.Build.VERSION.SDK_INT >= 26) {
            row.setDefaultFocusHighlightEnabled(false);
        }
        return row;
    }

    /** 竖屏两个并排次要按钮的描边背景（焦点时描边变蓝，不填充） */
    private GradientDrawable ghostBg(Context c, boolean focused) {
        return focused
                ? Ui.round(0, Ui.dp(c, 10),
                           Ui.color(c, R.color.accent), Ui.dp(c, 1.5f))
                : Ui.round(0, Ui.dp(c, 10),
                           Ui.color(c, R.color.separator), Ui.dp(c, 0.9f));
    }

    private void bindBillboard(HeroViews v, HomeItem item) {
        if (v == null) return;
        PlayListItem data = item.item;

        if (data == null) {
            v.art.setImageBitmap(null);
            v.art.setBackgroundColor(Ui.color(context, R.color.fill));
            v.eyebrow.setText("FN TV");
            v.title.setText("飞牛影视");
            v.badges.removeAllViews();
            v.meta.setText("");
            v.overview.setText("登录后即可浏览你的影音库");
            v.playBar.setVisibility(View.GONE);
            v.restart.setVisibility(View.GONE);
            v.episodes.setVisibility(View.GONE);
            return;
        }

        String path = item.backdrop != null && !item.backdrop.isEmpty()
                ? item.backdrop : data.poster;
        String url = Urls.poster(baseUrl, path, 1280);
        v.art.setImageBitmap(null);
        v.art.setBackgroundColor(Ui.color(context, R.color.image_placeholder));
        if (url != null) {
            SimpleImageLoader.load(url, v.art, FnApiManager.getInstance().getClient());
        }

        v.eyebrow.setText(item.eyebrow == null ? "为你推荐" : item.eyebrow);

        String series = data.tvTitle != null && !data.tvTitle.isEmpty()
                ? data.tvTitle : null;
        v.title.setText(Fmt.primaryTitle(data.title, series));

        // 徽章：年份 / 类型 / 集数 / 已看进度 / 分辨率 / 音轨
        v.badges.removeAllViews();
        addBadge(v, Fmt.year(data.airDate));
        addBadge(v, Fmt.type(data.type));
        if (data.episodeNumber > 0) {
            addBadge(v, data.seasonNumber > 0
                    ? "第 " + data.seasonNumber + " 季 · 第 " + data.episodeNumber + " 集"
                    : "第 " + data.episodeNumber + " 集");
        }
        int watchedPct = data.duration > 0 && data.ts > 0
                ? (int) (Math.min(data.ts, data.duration) * 100 / data.duration) : 0;
        if (watchedPct > 0) addBadge(v, "已看 " + watchedPct + "%", R.color.accent);
        if (data.mediaStream != null) {
            if (data.mediaStream.resolutions != null && !data.mediaStream.resolutions.isEmpty()) {
                addBadge(v, data.mediaStream.resolutions.get(0).toUpperCase());
            }
            Object audio = data.mediaStream.audioType;
            if (audio instanceof java.util.List) {
                java.util.List<?> list = (java.util.List<?>) audio;
                if (!list.isEmpty() && list.get(0) != null) {
                    addBadge(v, String.valueOf(list.get(0)));
                }
            } else if (audio != null) {
                addBadge(v, String.valueOf(audio));
            }
        }
        if (data.numberOfEpisodes > 0) addBadge(v, data.numberOfEpisodes + " 集");

        // 副信息行：分级 / 类型 / 国家 / 媒体库（拿不到就退回评分）
        if (heroMeta != null && !heroMeta.isEmpty()) {
            v.meta.setText(heroMeta.line());
            v.meta.setVisibility(View.VISIBLE);
        } else {
            String vote = Fmt.vote(data.voteAverage);
            v.meta.setText(vote == null ? "" : "★ " + vote);
            v.meta.setVisibility(vote == null ? View.GONE : View.VISIBLE);
        }

        String overview = data.overview != null ? data.overview.trim() : "";
        boolean hasOverview = !overview.isEmpty();
        v.overview.setVisibility(hasOverview ? View.VISIBLE : View.GONE);
        if (hasOverview) v.overview.setText(overview);

        final boolean playable = data.isPlayable() || "LiveChannel".equals(data.type);
        v.playBar.setVisibility(View.VISIBLE);
        v.playText.setText(playable
                ? (data.ts > 0 ? "继续播放" : "播放")
                : "查看详情");
        v.playBar.setOnClickListener(x -> {
            if (listener == null) return;
            if (playable) listener.onBillboard(data);
            else listener.onItem(data);
        });

        // 进度条
        int pct = data.duration > 0
                ? (int) (Math.min(data.ts, data.duration) * 100 / data.duration) : 0;
        v.track.setVisibility(pct > 0 ? View.VISIBLE : View.GONE);
        v.track.removeAllViews();
        if (pct > 0) {
            v.track.setWeightSum(100f);
            View fill = new View(context);
            fill.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, pct)));
            fill.setBackground(Ui.round(Ui.color(context, R.color.accent),
                    Ui.dp(context, 2)));
            v.track.addView(fill);
            View rest = new View(context);
            rest.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, 100 - pct)));
            v.track.addView(rest);
        }

        v.restart.setVisibility(playable ? View.VISIBLE : View.GONE);
        v.episodes.setVisibility("LiveChannel".equals(data.type) ? View.GONE : View.VISIBLE);
        Integer startEp = data.parentGuid != null ? seasonStartEpisodes.get(data.parentGuid) : null;
        int restartEpisode = startEp != null ? startEp : data.episodeNumber;
        v.restart.setText(restartEpisode > 0
                ? "从第 " + restartEpisode + " 集开始" : "重新播放");
        heroSeasonGuid = data.parentGuid;
        heroRestart = v.restart;
        v.restart.setOnClickListener(x -> {
            if (listener != null) listener.onRestart(data);
        });
        v.episodes.setOnClickListener(x -> {
            if (listener != null) listener.onItem(data);
        });
    }

    private void addBadge(HeroViews v, String text) {
        addBadge(v, text, R.color.label_2);
    }

    private void addBadge(HeroViews v, String text, int colorRes) {
        if (text == null || text.isEmpty()) return;
        TextView chip = new TextView(context);
        chip.setText(text);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                Ui.dimen(context, R.dimen.hero_meta_size));
        chip.setTextColor(Ui.color(context, colorRes));
        chip.setIncludeFontPadding(false);
        chip.setSingleLine(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setColor(0);
        bg.setCornerRadius(Ui.dp(context, 2));
        bg.setStroke(Math.max(1, Ui.dp(context, 0.6f)),
                Ui.color(context, R.color.separator));
        chip.setBackgroundDrawable(bg);
        chip.setPadding(Ui.dp(context, 6), Ui.dp(context, 1),
                Ui.dp(context, 6), Ui.dp(context, 2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(context, 6);
        chip.setLayoutParams(lp);
        v.badges.addView(chip);
    }

    // ==================== 行标题 ====================

    private View buildRowTitle(Context c) {
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int pad = Ui.dimen(c, R.dimen.screen_pad);
        root.setPadding(Ui.railInset(c), Ui.dimen(c, R.dimen.row_title_top),
                pad, Ui.dimen(c, R.dimen.row_title_bottom));

        TextView title = new TextView(c);
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                Ui.dimen(c, R.dimen.row_title_size));
        title.setTextColor(Ui.color(c, R.color.label));
        title.setAlpha(0.92f);
        title.setIncludeFontPadding(false);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        title.setLayoutParams(titleLp);
        root.addView(title);

        TextView more = new TextView(c);
        more.setText("查看更多");
        more.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                Ui.dimen(c, R.dimen.card_title_size));
        more.setTextColor(Ui.color(c, R.color.label_2));
        more.setIncludeFontPadding(false);
        more.setSingleLine(true);
        more.setGravity(Gravity.CENTER_VERTICAL);
        more.setPadding(Ui.dp(c, 10), 0, 0, 0);

        // 图标是 vector，API 19 上普通 ImageView#setImageResource 会加载失败，必须用 AppCompat 版本
        ImageView chevron = new AppCompatImageView(c);
        chevron.setImageResource(R.drawable.ic_chevron_right);
        chevron.setColorFilter(Ui.color(c, R.color.label_2));
        chevron.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int chSize = Math.round(Ui.dimen(c, R.dimen.card_title_size) * 1.05f);
        LinearLayout.LayoutParams chLp = new LinearLayout.LayoutParams(chSize, chSize);
        chLp.leftMargin = Ui.dp(c, 1);
        chevron.setLayoutParams(chLp);

        LinearLayout moreBox = new LinearLayout(c);
        moreBox.setOrientation(LinearLayout.HORIZONTAL);
        moreBox.setGravity(Gravity.CENTER_VERTICAL);
        moreBox.addView(more);
        moreBox.addView(chevron);
        moreBox.setPadding(Ui.dp(c, 10), Ui.dp(c, 5), Ui.dp(c, 4), Ui.dp(c, 5));
        // 遥控器要能选中「查看更多」
        moreBox.setTag("home_more");
        moreBox.setFocusable(true);
        moreBox.setFocusableInTouchMode(false);
        moreBox.setClickable(true);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            moreBox.setDefaultFocusHighlightEnabled(false);
        }
        moreBox.setOnFocusChangeListener((v, focused) -> {
            v.setBackground(focused
                    ? Ui.round(0, Ui.dp(c, 8),
                               Ui.color(c, R.color.accent), Ui.dp(c, 1.5f))
                    : null);
            int color = Ui.color(c, focused ? R.color.label : R.color.label_2);
            more.setTextColor(color);
            chevron.setColorFilter(focused ? Ui.color(c, R.color.accent) : color);
        });
        moreBox.setVisibility(View.GONE);
        root.addView(moreBox);

        root.setBaselineAligned(false);
        return root;
    }

    // ==================== 横向行 ====================

    private View buildRow(Context c, int kind) {
        RecyclerView rv = new ClippedRecyclerView(c);
        rv.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        rv.setLayoutManager(new LinearLayoutManager(c, RecyclerView.HORIZONTAL, false));
        rv.setClipToPadding(false);
        rv.setClipChildren(false);
        rv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        rv.setItemAnimator(null);
        // 上下留出卡片选中放大（1.06 倍）的空间，避免被行边界裁掉；
        // 横屏首卡再往右让 8dp，选中放大时不会贴到左侧图标栏边缘。
        int left = Ui.railInset(c) + (Ui.isLandscape(c) ? Ui.dp(c, 8) : 0);
        rv.setPadding(left, Ui.dp(c, 6), Ui.dimen(c, R.dimen.screen_pad),
                Ui.dp(c, 14));
        rv.addItemDecoration(new SpaceDecoration(Ui.dp(c, Ui.isLandscape(c) ? 16 : 8)));
        rv.setTag(kind);
        return rv;
    }

    private void bindRow(RowHolder holder, HomeItem item) {
        RecyclerView rv = holder.recycler;
        int kind = (Integer) rv.getTag();
        if (kind == 1) {
            ContinueAdapter adapter = new ContinueAdapter(context, baseUrl,
                    continueWidth(context), i -> {
                if (listener != null) listener.onItem(i);
            });
            adapter.submit(item.items);
            rv.setAdapter(adapter);
        } else if (kind == 2) {
            int w = posterWidth(context);
            LiveAdapter adapter = new LiveAdapter(context, w, Math.round(w * 1.5f), i -> {
                if (listener != null) listener.onItem(i);
            });
            adapter.submit(item.items);
            rv.setAdapter(adapter);
        } else {
            PosterAdapter adapter = new PosterAdapter(context, baseUrl,
                    posterWidth(context), true, i -> {
                if (listener != null) listener.onItem(i);
            });
            adapter.setShowMeta(false);
            adapter.submit(item.items);
            rv.setAdapter(adapter);
        }
    }

    /** 可用内容宽度（扣掉两侧留白，横屏还要扣掉左侧竖栏） */
    private int availWidth(Context c) {
        return Ui.screenWidth(c) - Ui.railInset(c) - Ui.dimen(c, R.dimen.screen_pad);
    }

    /** 海报宽度：横屏按屏幕高度比例（一屏约 6 张），竖屏按宽度 */
    private int posterWidth(Context c) {
        if (Ui.isLandscape(c)) {
            return Math.round(Ui.screenHeight(c) * 0.27f);
        }
        return Math.round(availWidth(c) * 0.40f);
    }

    private int continueWidth(Context c) {
        if (Ui.isLandscape(c)) return Math.round(Ui.contentWidth(c) * 0.30f);
        return Math.round(availWidth(c) * 0.72f);
    }

    // ==================== 其他 ====================

    private View buildLoading(Context c) {
        FrameLayout box = new FrameLayout(c);
        box.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 110)));
        ProgressBar bar = new ProgressBar(c);
        bar.getIndeterminateDrawable().setColorFilter(Ui.color(c, R.color.accent),
                android.graphics.PorterDuff.Mode.SRC_IN);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                Ui.dp(c, 20), Ui.dp(c, 20));
        lp.gravity = Gravity.CENTER;
        box.addView(bar, lp);
        return box;
    }

    private View buildTip(Context c) {
        TextView text = Ui.subhead(c, "");
        text.setGravity(Gravity.CENTER);
        text.setPadding(Ui.railInset(c), Ui.dp(c, 40),
                Ui.dimen(c, R.dimen.screen_pad), Ui.dp(c, 40));
        text.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return text;
    }

    // ==================== Holder ====================

    static class HeroViews {
        ImageView art;
        LinearLayout info;
        TextView eyebrow;
        TextView title;
        LinearLayout badges;
        TextView meta;
        TextView overview;
        LinearLayout playBar;
        TextView playText;
        LinearLayout track;
        TextView restart;
        TextView episodes;
    }

    static class BillboardHolder extends RecyclerView.ViewHolder {
        final HeroViews views;

        BillboardHolder(View v) {
            super(v);
            views = (HeroViews) v.getTag();
        }
    }

    static class RowTitleHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final View more;

        RowTitleHolder(View v) {
            super(v);
            ViewGroup g = (ViewGroup) v;
            title = (TextView) g.getChildAt(0);
            more = g.getChildAt(1);
        }
    }

    static class RowHolder extends RecyclerView.ViewHolder {
        final RecyclerView recycler;

        RowHolder(View v) {
            super(v);
            recycler = (RecyclerView) v;
        }
    }

    static class LoadingHolder extends RecyclerView.ViewHolder {
        LoadingHolder(View v) {
            super(v);
        }
    }

    static class TipHolder extends RecyclerView.ViewHolder {
        final TextView text;

        TipHolder(View v) {
            super(v);
            text = (TextView) v;
        }
    }
}
