package com.fntv.app.ui.detail;

import android.content.Intent;
import android.app.Dialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;

import com.fntv.app.PlayerActivity;
import com.fntv.app.R;
import com.fntv.app.RoundedImageView;
import com.fntv.app.api.FnApiManager;
import com.fntv.app.api.model.PlayInfoResponse;
import com.fntv.app.api.model.ItemDetail;
import com.fntv.app.api.model.ItemMeta;
import com.fntv.app.api.model.PlayListItem;
import com.fntv.app.core.Fmt;
import com.fntv.app.core.Prefs;
import com.fntv.app.core.ThemeMode;
import com.fntv.app.core.Urls;
import com.fntv.app.data.MediaRepository;
import com.fntv.app.ui.Ui;
import com.fntv.app.util.SimpleImageLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * 详情页：全幅剧照 + 标题区 + 播放 + 简介 + 选集（Apple TV 式）。
 */
public class DetailActivity extends AppCompatActivity {

    private LinearLayout content;
    private ProgressBar loading;
    private ScrollView scroll;

    private String guid;
    private String passedTitle;
    private String passedPoster;
    private String passedCategory;
    private String baseUrl;

    private PlayInfoResponse info;
    private final List<PlayListItem> seasons = new ArrayList<>();
    private int selectedSeason = -1;
    private LinearLayout seasonBar;
    private LinearLayout episodeCard;
    private int sidePad;
    private int topPad;
    /** 片名艺术字路径（GET item 的 logos），没有就退回文字标题 */
    private String logosPath;
    /** 剧本身的信息（评分 / 季数 / 年份）；play/info 返回的是第一集，评分不能直接用 */
    private ItemDetail seriesDetail;

    /** 选集视图：false = 列表现有样式，true = 海报卡片（和首页一致） */
    private boolean posterMode = false;
    private View episodesHeader;
    private View episodesBody;
    private LinearLayout seasonPosters;
    private LinearLayout episodePosters;
    private HorizontalScrollView seasonScrollView;
    private HorizontalScrollView episodeScrollView;
    private HorizontalScrollView listSeasonScroll;
    private Button modeToggle;
    private View playButton;
    private View favoriteButton;
    private TextView favoriteText;
    private ImageView favoriteIcon;
    private boolean favoriteOn;
    private boolean favoriteBusy;
    private final List<PlayListItem> currentEpisodes = new ArrayList<>();
    private final List<View> seasonCardRings = new ArrayList<>();
    private final List<View> seasonCardViews = new ArrayList<>();
    private int pendingScrollY = -1;

    /** 行首/行尾拦掉左右键，焦点不会跳出这一行 */
    private void blockRowEdges(View v, final boolean first, final boolean last) {
        v.setOnKeyListener((view, keyCode, event) -> {
            if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) return false;
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT && first) return true;
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT && last) return true;
            return false;
        });
    }
    private int currentEpisodeShown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeMode.apply(this);
        setContentView(R.layout.activity_detail);
        Ui.applyImmersive(this, (left, right, bottom) -> {
            if (content == null) return;
            content.setPadding(left, topPad, right, Ui.dp(this, 32) + bottom);
        });

        baseUrl = Prefs.host(this);
        posterMode = Prefs.bool(this, Prefs.EPISODE_POSTER_MODE, false);
        guid = getIntent().getStringExtra("guid");
        passedTitle = getIntent().getStringExtra("title");
        passedPoster = getIntent().getStringExtra("poster");
        passedCategory = getIntent().getStringExtra("category");

        content = findViewById(R.id.content);
        loading = findViewById(R.id.loading);
        scroll = findViewById(R.id.scroll);

        sidePad = computeSidePad();
        topPad = 0;
        content.setPadding(0, topPad, 0, Ui.dp(this, 32));

        AppCompatImageButton back = findViewById(R.id.btnBack);
        back.setColorFilter(Ui.color(this, R.color.text_white));
        FrameLayout.LayoutParams backLp = (FrameLayout.LayoutParams) back.getLayoutParams();
        if (Ui.isLandscape(this)) {
            // 横屏用系统返回键即可，右上角不再放返回按钮
            back.setVisibility(View.GONE);
        } else {
            backLp.topMargin = Ui.statusBarHeight(this) + Ui.dp(this, 8);
        }
        back.setLayoutParams(backLp);
        back.setOnClickListener(v -> finish());

        load();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("poster_mode", posterMode);
    }
    private int computeSidePad() {
        // 横屏 / 电视：文字直接贴左侧留白，不再按 760dp 居中，
        // 否则宽屏（电视盒子、低密度安卓5设备）左边会空出一大条。
        if (Ui.isLandscape(this)) return Ui.dimen(this, R.dimen.screen_pad);
        int available = Ui.contentWidth(this);
        return Math.max(Ui.dimen(this, R.dimen.screen_pad),
                (available - Ui.dp(this, 760)) / 2);
    }

    private void load() {
        loading.setVisibility(View.VISIBLE);
        resolve(guid, 0);
    }

    /**
     * 从剧集 / 季一路往上找到「剧」，这样无论从首页大图、继续观看还是媒体库点进来，
     * 都是同一个带季集信息的详情页。
     */
    private void resolve(final String itemGuid, final int depth) {
        if (itemGuid == null || itemGuid.isEmpty()) {
            loading.setVisibility(View.GONE);
            return;
        }
        MediaRepository.get().seasons(itemGuid, new MediaRepository.DataCallback<List<PlayListItem>>() {
            @Override
            public void onData(List<PlayListItem> list) {
                boolean hasSeasons = list != null && !list.isEmpty();
                if (hasSeasons || depth >= 2) {
                    loadDetail(itemGuid);
                    return;
                }
                // 这一层没有季信息 → 用 item 接口取父级继续往上（播放接口拿不到父级）
                MediaRepository.get().itemParent(itemGuid,
                        new MediaRepository.DataCallback<String>() {
                    @Override
                    public void onData(String parent) {
                        if (parent == null || parent.isEmpty() || parent.equals(itemGuid)) {
                            loadDetail(itemGuid);
                        } else {
                            resolve(parent, depth + 1);
                        }
                    }

                    @Override
                    public void onError(String message) {
                        loadDetail(itemGuid);
                    }
                });
            }

            @Override
            public void onError(String message) {
                loadDetail(itemGuid);
            }
        });
    }

    /** 加载详情并渲染（guid 已经定位到「剧」这一层） */
    private void loadDetail(final String itemGuid) {
        // 先取 item 详情，拿到网页同款的 logos 片名艺术字 + 剧本身的评分/季数；失败也不影响播放信息
        logosPath = null;
        seriesDetail = null;
        MediaRepository.get().item(itemGuid, new MediaRepository.DataCallback<ItemDetail>() {
            @Override
            public void onData(ItemDetail data) {
                seriesDetail = data;
                if (data != null) logosPath = data.logos;
                loadPlayDetail(itemGuid);
            }

            @Override
            public void onError(String message) {
                loadPlayDetail(itemGuid);
            }
        });
    }

    private void loadPlayDetail(final String itemGuid) {
        MediaRepository.get().detail(itemGuid, new MediaRepository.DataCallback<PlayInfoResponse>() {
            @Override
            public void onData(PlayInfoResponse data) {
                loading.setVisibility(View.GONE);
                guid = itemGuid;
                info = data;
                render();
                loadFavoriteState(itemGuid);
            }

            @Override
            public void onError(String message) {
                loading.setVisibility(View.GONE);
                TextView error = Ui.subhead(DetailActivity.this, message);
                error.setGravity(Gravity.CENTER);
                error.setPadding(0, Ui.dp(DetailActivity.this, 120), 0, 0);
                content.addView(error);
            }
        });
    }

    private void render() {
        content.removeAllViews();

        String epTitle = info.item != null && info.item.title != null ? info.item.title : passedTitle;
        String series = info.item != null && info.item.tvTitle != null ? info.item.tvTitle : "";
        int epNum = info.item != null ? info.item.episodeNumber : 0;
        String primary = Fmt.primaryTitle(epTitle, series);
        String episodeLine = Fmt.episodeLine(epTitle, series, epNum);

        String backdrop = info.getBackdropPath() != null ? info.getBackdropPath() : passedPoster;
        String heroUrl = Urls.poster(baseUrl, backdrop, 1200);

        long ts = Math.max(0, info.ts);
        long duration = resolveDuration();

        if (Ui.isLandscape(this)) {
            content.addView(buildWideHeader(heroUrl, primary, episodeLine, ts, duration));
        } else {
            int artH = heroTallHeight();
            content.addView(buildHero(heroUrl, artH));
            View titleBlock = buildTitleBlock(primary, episodeLine, epNum);
            LinearLayout.LayoutParams tbLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            // 文字块压到剧照下缘，和首页竖屏大图一样
            tbLp.topMargin = -Math.round(artH * 0.20f);
            content.addView(titleBlock, tbLp);
            content.addView(buildMeta(sidePad));
            content.addView(buildTechBadgesRow(sidePad));
            content.addView(buildActions(ts, duration, 0));
            content.addView(buildOverview());
        }

        if (guid != null && !guid.isEmpty()) {
            loadSeasons(epNum);
        }
    }

    /** 横屏 / 电视：整屏沉浸式剧照（和首页大图一样），文字压在左下角 */
    private View buildWideHeader(String heroUrl, String primary, String episodeLine,
                                 long ts, long duration) {
        final int w = Ui.screenWidth(this);
        final int h = Ui.screenHeight(this);
        final int headerH = Math.max(Math.round(h * 0.72f), Ui.dp(this, 300));
        final int surface = Ui.color(this, R.color.surface_grouped);

        FrameLayout hero = new FrameLayout(this);
        hero.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, headerH));

        RoundedImageView image = new RoundedImageView(this);
        image.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        image.setCornerRadius(0);
        image.setBackgroundColor(Ui.color(this, R.color.image_placeholder));
        FrameLayout.LayoutParams artLp = new FrameLayout.LayoutParams(
                Math.round(w * 0.62f), ViewGroup.LayoutParams.MATCH_PARENT);
        artLp.gravity = Gravity.RIGHT;
        hero.addView(image, artLp);
        if (heroUrl != null) {
            SimpleImageLoader.load(heroUrl, image, FnApiManager.getInstance().getClient());
        }

        // 左侧由页面背景色渐隐到透明，托住文字列（与首页大图一致）
        View leftFade = new View(this);
        leftFade.setBackground(Ui.gradientH(
                new int[]{surface, surface, 0x00FFFFFF & surface, 0x00FFFFFF & surface},
                new float[]{0f, 0.40f, 0.52f, 1f}));
        hero.addView(leftFade, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 顶部状态栏罩子（半透明，剧照依然透出来）
        View topFade = new View(this);
        topFade.setBackground(Ui.gradientV(
                new int[]{veil(surface, 0.70f), veil(surface, 0.22f), 0x00FFFFFF & surface},
                new float[]{0f, 0.55f, 1f}));
        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.statusBarHeight(this) + Ui.dp(this, 20));
        topLp.gravity = Gravity.TOP;
        hero.addView(topFade, topLp);

        // 底部渐隐到页面背景，和下方选集自然衔接
        View bottomFade = new View(this);
        bottomFade.setBackground(Ui.gradientV(
                new int[]{0x00FFFFFF & surface, surface}, null));
        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.round(headerH * 0.13f));
        bottomLp.gravity = Gravity.BOTTOM;
        hero.addView(bottomFade, bottomLp);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setGravity(Gravity.TOP);
        FrameLayout.LayoutParams infoLp = new FrameLayout.LayoutParams(
                Math.round(w * 0.42f), ViewGroup.LayoutParams.WRAP_CONTENT);
        infoLp.gravity = Gravity.TOP | Gravity.LEFT;
        infoLp.leftMargin = sidePad;
        // 横屏文字块贴住状态栏下方
        infoLp.topMargin = Ui.statusBarHeight(this) + Ui.dp(this, 8);
        hero.addView(left, infoLp);

        TextView eyebrowView = Ui.text(this, eyebrowText(episodeLine), 12f, R.color.accent);
        eyebrowView.setTypeface(Typeface.DEFAULT_BOLD);
        left.addView(eyebrowView);

        left.addView(buildTitleView(primary, 32f, 2, Math.round(w * 0.42f), 56));

        if (episodeLine != null) {
            left.addView(buildEpisodeRow(episodeLine));
        }

        String overview = info.item != null && info.item.overview != null
                ? info.item.overview.trim() : "";
        if (!overview.isEmpty()) {
            TextView text = buildOverviewText(overview);
            text.setMaxLines(2);
            text.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            textLp.topMargin = Ui.dp(this, 8);
            text.setLayoutParams(textLp);
            left.addView(text);
        }

        View actions = buildActions(ts, duration, 0);
        ((LinearLayout) actions).setPadding(0, Ui.dp(this, 12), 0, 0);
        left.addView(buildMeta(0));
        left.addView(buildTechBadgesRow(0));
        left.addView(actions);

        return hero;
    }

    private long resolveDuration() {
        if (info.item != null && info.item.duration > 0) return info.item.duration;
        if (info.item != null && info.item.runtime > 0) return info.item.runtime * 60L;
        return 0;
    }

    /**
     * 片名：有 logos 时显示网页同款艺术字（透明 webp），
     * 没有或加载失败时继续显示文字标题。
     */
    private View buildTitleView(String primary, float textSizeSp, int maxLines,
                                int maxWidthPx, int logoHeightDp) {
        if (logosPath == null || logosPath.isEmpty()) {
            return buildTitleText(primary, textSizeSp, maxLines);
        }

        FrameLayout box = new FrameLayout(this);
        final TextView fallback = buildTitleText(primary, textSizeSp, maxLines);
        box.addView(fallback);

        final AppCompatImageView logo = new AppCompatImageView(this);
        logo.setAdjustViewBounds(true);
        logo.setScaleType(ImageView.ScaleType.FIT_START);
        logo.setMaxWidth(Math.max(1, maxWidthPx));
        logo.setMaxHeight(Ui.dp(this, logoHeightDp));
        logo.setContentDescription(primary);
        logo.setVisibility(View.GONE);
        FrameLayout.LayoutParams logoLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, logoHeightDp));
        logoLp.topMargin = Ui.dp(this, 6);
        logoLp.gravity = Gravity.LEFT;
        box.addView(logo, logoLp);

        SimpleImageLoader.loadAlpha(Urls.poster(baseUrl, logosPath, 800), logo,
                FnApiManager.getInstance().getClient(), ThemeMode.isNight(this), new Runnable() {
                    @Override
                    public void run() {
                        fallback.setVisibility(View.GONE);
                        logo.setVisibility(View.VISIBLE);
                    }
                });
        return box;
    }

    private TextView buildTitleText(String primary, float textSizeSp, int maxLines) {
        TextView title = Ui.text(this, primary, textSizeSp, R.color.label);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setMaxLines(maxLines);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = Ui.dp(this, 6);
        title.setLayoutParams(titleLp);
        return title;
    }

    // ==================== 顶部大图 ====================

    private int heroTallHeight() {
        int width = Ui.screenWidth(this);
        return Math.min(Math.round(width * 9f / 16f),
                Math.round(Ui.screenHeight(this) * 0.60f));
    }

    private View buildHero(String heroUrl, int height) {
        int surface = Ui.color(this, R.color.surface_grouped);

        FrameLayout hero = new FrameLayout(this);
        hero.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, height));

        RoundedImageView image = new RoundedImageView(this);
        image.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        image.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        image.setCornerRadius(0);
        image.setBackgroundColor(Ui.color(this, R.color.image_placeholder));
        hero.addView(image);
        if (heroUrl != null) {
            SimpleImageLoader.load(heroUrl, image, FnApiManager.getInstance().getClient());
        }

        // 顶部渐隐：保证状态栏图标可读，剧照仍会透出来
        View topFade = new View(this);
        topFade.setBackground(Ui.gradientV(
                new int[]{veil(surface, 0.70f), veil(surface, 0.22f), 0x00FFFFFF & surface},
                new float[]{0f, 0.55f, 1f}));
        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.statusBarHeight(this) + Ui.dp(this, 22));
        topLp.gravity = Gravity.TOP;
        hero.addView(topFade, topLp);

        // 底部渐隐：透明 → 页面背景，文字块压在这段渐隐上
        View bottomFade = new View(this);
        bottomFade.setBackground(Ui.gradientV(
                new int[]{0x00FFFFFF & surface, veil(surface, 0.45f),
                          veil(surface, 0.80f), surface},
                new float[]{0f, 0.25f, 0.55f, 0.80f}));
        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.round(height * 0.62f));
        bottomLp.gravity = Gravity.BOTTOM;
        hero.addView(bottomFade, bottomLp);

        return hero;
    }

    /** 横竖屏统一的小标题：类型 · 年份 · 评分 · 季数，避免两套布局显示内容不一样 */
    private String eyebrowText(String episodeLine) {
        ItemDetail sd = seriesDetail;
        String type = sd != null && sd.type != null ? sd.type : info.type;
        StringBuilder eyebrow = new StringBuilder(Fmt.type(type));

        String year = null;
        if (sd != null) {
            year = Fmt.year(sd.releaseDate != null ? sd.releaseDate : sd.airDate);
        }
        if (year == null && info.item != null) {
            year = Fmt.year(info.item.releaseDate != null
                    ? info.item.releaseDate : info.item.airDate);
        }
        if (year != null) eyebrow.append("  ·  ").append(year);

        // play/info 对剧集返回的是第一集（评分可能是 10），优先用剧本身的评分
        String vote = sd != null ? Fmt.vote(sd.voteAverage) : null;
        if (vote == null && info.item != null) vote = Fmt.vote(info.item.voteAverage);
        if (vote != null) eyebrow.append("  ·  ★ ").append(vote);

        int seasons = sd != null && sd.numberOfSeasons > 0
                ? sd.numberOfSeasons
                : (info.item != null ? info.item.numberOfSeasons : 0);
        if (seasons > 0) eyebrow.append("  ·  ").append(seasons).append(" 季");

        int epNum = info.item != null ? info.item.episodeNumber : 0;
        if (epNum > 0 && episodeLine == null) {
            eyebrow.append("  ·  第 ").append(epNum).append(" 集");
        }
        return eyebrow.toString();
    }

    private View buildTitleBlock(String primary, String episodeLine, int epNum) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(sidePad, Ui.dp(this, 2), sidePad, 0);

        TextView eyebrowView = Ui.text(this, eyebrowText(episodeLine), 12f, R.color.accent);
        eyebrowView.setTypeface(Typeface.DEFAULT_BOLD);
        block.addView(eyebrowView);

        block.addView(buildTitleView(primary, 30f, 3,
                Math.max(1, Ui.screenWidth(this) - sidePad * 2), 54));

        if (episodeLine != null) {
            block.addView(buildEpisodeRow(episodeLine));
        }
        return block;
    }

    /** 集数行：剧集名 + 4K / 音轨 / 总时长（小徽章，紧跟在后面） */
    private View buildEpisodeRow(String episodeLine) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Ui.dp(this, 6);
        scroll.setLayoutParams(slp);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        // 集数行只作展示，遥控器不选中
        scroll.setFocusable(false);
        scroll.setFocusableInTouchMode(false);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView ep = Ui.text(this, episodeLine, 13f, R.color.label_2);
        ep.setSingleLine(true);
        ep.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams epLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        epLp.rightMargin = Ui.dp(this, 10);
        ep.setLayoutParams(epLp);
        row.addView(ep);

        scroll.addView(row);
        return scroll;
    }

    /** 4K / 音轨 / 时长徽章行（独立一行，放在播放按钮上方） */
    private View buildTechBadgesRow(int pad) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(pad, Ui.dp(this, 10), pad, 0);
        addTechBadges(row);
        if (row.getChildCount() == 0) row.setVisibility(View.GONE);
        return row;
    }

    private void addTechBadges(LinearLayout row) {
        PlayInfoResponse.ItemInfo data = info.item;
        if (data != null && data.mediaStream != null) {
            if (data.mediaStream.resolutions != null
                    && !data.mediaStream.resolutions.isEmpty()) {
                addBadge(row, data.mediaStream.resolutions.get(0).toUpperCase());
            }
            List<String> audio = data.mediaStream.audioType;
            if (audio != null && !audio.isEmpty() && audio.get(0) != null) {
                addBadge(row, audio.get(0));
            }
        }
        long duration = resolveDuration();
        if (duration > 0) addBadge(row, Fmt.duration(duration));
    }

    private void addBadge(LinearLayout row, String text) {
        if (text == null || text.isEmpty()) return;
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                Ui.dimen(this, R.dimen.hero_meta_size));
        chip.setTextColor(Ui.color(this, R.color.label_2));
        chip.setIncludeFontPadding(false);
        chip.setSingleLine(true);
        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        bg.setColor(0);
        bg.setCornerRadius(Ui.dp(this, 2));
        bg.setStroke(Math.max(1, Ui.dp(this, 0.6f)), Ui.color(this, R.color.separator));
        chip.setBackgroundDrawable(bg);
        chip.setPadding(Ui.dp(this, 6), Ui.dp(this, 3), Ui.dp(this, 6), Ui.dp(this, 3));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(this, 6);
        chip.setLayoutParams(lp);
        row.addView(chip);
    }

    // ==================== 播放按钮 ====================

    private View buildActions(long ts, long duration, int maxWidth) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(sidePad, Ui.dp(this, 16), sidePad, 0);

        final long playTs = ts;
        final long playDuration = duration;
        final boolean landscape = Ui.isLandscape(this);
        final int playH = Ui.dimen(this, R.dimen.hero_play_height);

        // 左栏宽度有限时（横屏 42% 文字列 / 窄屏竖屏），
        // 收紧按钮内边距、缩短续播进度条；横屏更窄时收藏只保留图标，
        // 保证“继续播放 + 进度条 + 收藏”都能完整显示。
        int columnPx = landscape
                ? Math.round(Ui.screenWidth(this) * 0.42f) - sidePad * 2
                : Ui.screenWidth(this) - sidePad * 2;
        int columnDp = Math.round(columnPx / getResources().getDisplayMetrics().density);
        final boolean compact = columnDp < 310;

        // 和首页大图一致的胶囊播放按钮：图标 + 文字 + 右侧内嵌续播进度
        LinearLayout playBar = new LinearLayout(this);
        playBar.setOrientation(LinearLayout.HORIZONTAL);
        playBar.setGravity(Gravity.CENTER_VERTICAL);
        playBar.setBackground(Ui.round(Ui.color(this, R.color.label), playH / 2f));
        playBar.setFocusable(true);
        playBar.setClickable(true);
        playBar.setId(View.generateViewId());
        playButton = playBar;
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            playBar.setDefaultFocusHighlightEnabled(false);
        }
        playBar.setPadding(Ui.dp(this, compact ? 14 : 20), 0,
                Ui.dp(this, compact ? 14 : 20), 0);
        // 横屏不再拉伸播放按钮：按内容宽度显示，收藏紧跟其后即可
        LinearLayout.LayoutParams playLp = landscape
                ? new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, playH)
                : new LinearLayout.LayoutParams(0, playH, 1f);
        playBar.setLayoutParams(playLp);
        playBar.setOnFocusChangeListener((v, focused) -> v.setBackground(focused
                ? Ui.round(Ui.color(this, R.color.label), playH / 2f,
                           Ui.color(this, R.color.accent), Ui.dp(this, 2))
                : Ui.round(Ui.color(this, R.color.label), playH / 2f)));

        ImageView playIcon = Ui.icon(this, R.drawable.ic_play,
                landscape ? 13 : 15, R.color.surface_grouped);
        playBar.addView(playIcon);

        TextView playText = new TextView(this);
        playText.setText(playTs > 0 ? "继续播放" : "播放");
        playText.setTextSize(14f);
        playText.setTextColor(Ui.color(this, R.color.surface_grouped));
        playText.setTypeface(Typeface.DEFAULT_BOLD);
        playText.setSingleLine(true);
        playText.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams ptLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ptLp.leftMargin = Ui.dp(this, 8);
        playText.setLayoutParams(ptLp);
        playBar.addView(playText);

        if (playTs > 0 && playDuration > 0) {
            // 竖屏播放按钮是满宽的，用 spacer 把进度条推到右侧；
            // 横屏按钮按内容收窄，不能再放带权重的 spacer，否则按钮会被撑满整行。
            if (!landscape) {
                View spacer = new View(this);
                spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
                playBar.addView(spacer);
            }

            int pct = Math.max(1, Math.min(100, (int) (playTs * 100 / playDuration)));
            LinearLayout track = new LinearLayout(this);
            track.setOrientation(LinearLayout.HORIZONTAL);
            track.setBackground(Ui.round(
                    veil(Ui.color(this, R.color.surface_grouped), 0.28f), Ui.dp(this, 2)));
            LinearLayout.LayoutParams trackLp = new LinearLayout.LayoutParams(
                    Ui.dp(this, compact ? 32 : (landscape ? 64 : 72)),
                    Math.max(3, Ui.dp(this, 3)));
            trackLp.leftMargin = Ui.dp(this, compact ? 6 : 16);
            track.setLayoutParams(trackLp);
            track.setWeightSum(100f);
            View fill = new View(this);
            fill.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, pct));
            fill.setBackground(Ui.round(Ui.color(this, R.color.accent), Ui.dp(this, 2)));
            track.addView(fill);
            View rest = new View(this);
            rest.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 100 - pct));
            track.addView(rest);
            playBar.addView(track);
        }

        playBar.setOnClickListener(v -> launchPlay(guid, title(), tvTitle(), episodeNumber(),
                info.getPosterPath() != null ? info.getPosterPath() : passedPoster,
                category(), playTs, playDuration, parentGuid()));

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER_VERTICAL);

        // 收藏按钮：只收藏整部剧（guid 已经上溯到「剧」）
        LinearLayout fav = new LinearLayout(this);
        fav.setOrientation(LinearLayout.HORIZONTAL);
        fav.setGravity(Gravity.CENTER_VERTICAL);
        fav.setFocusable(true);
        fav.setClickable(true);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            fav.setDefaultFocusHighlightEnabled(false);
        }
        fav.setPadding(Ui.dp(this, compact ? 14 : 16), 0,
                Ui.dp(this, compact ? 14 : 18), 0);
        LinearLayout.LayoutParams favLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, playH);
        favLp.leftMargin = Ui.dp(this, compact ? 10 : 12);
        fav.setLayoutParams(favLp);
        fav.setBackground(favoriteCapsuleBg(false));

        favoriteIcon = Ui.icon(this, R.drawable.ic_favorite,
                landscape ? 14 : 16, R.color.label);
        fav.addView(favoriteIcon);

        favoriteText = new TextView(this);
        favoriteText.setText("收藏");
        favoriteText.setTextSize(14f);
        favoriteText.setTextColor(Ui.color(this, R.color.label));
        favoriteText.setTypeface(Typeface.DEFAULT_BOLD);
        favoriteText.setSingleLine(true);
        LinearLayout.LayoutParams ftLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ftLp.leftMargin = Ui.dp(this, 8);
        favoriteText.setLayoutParams(ftLp);
        fav.addView(favoriteText);

        // 横屏：按真实测量结果决定显不显示“收藏”两个字，空间够就保留文字
        if (landscape) {
            int playW = measureWidth(playBar, playH);
            int favW = measureWidth(fav, playH);
            if (playW + favLp.leftMargin + favW > columnPx) {
                favoriteText.setVisibility(View.GONE);
            }
        }

        fav.setOnFocusChangeListener((v, focused) -> v.setBackground(favoriteCapsuleBg(focused)));
        fav.setOnClickListener(v -> toggleFavorite());
        favoriteButton = fav;

        btnRow.addView(playBar);
        btnRow.addView(fav);
        box.addView(btnRow);

        updateFavoriteButton();
        return box;
    }

    /** 按内容测量宽度（用于决定收藏按钮要不要显示文字） */
    private int measureWidth(View view, int exactHeight) {
        view.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(exactHeight, View.MeasureSpec.EXACTLY));
        return view.getMeasuredWidth();
    }

    private android.graphics.drawable.GradientDrawable favoriteCapsuleBg(boolean focused) {        float radius = Ui.dimen(this, R.dimen.hero_play_height) / 2f;
        if (focused) {
            return Ui.round(veil(Ui.color(this, R.color.label), 0.10f), radius,
                    Ui.color(this, R.color.accent), Ui.dp(this, 2));
        }
        return Ui.round(0, radius, Ui.color(this, R.color.separator), Ui.dp(this, 1.5f));
    }

    private void updateFavoriteButton() {
        if (favoriteText == null) return;
        favoriteText.setText(favoriteOn ? "已收藏" : "收藏");
        favoriteText.setTextColor(Ui.color(this,
                favoriteOn ? R.color.accent : R.color.label));
        favoriteIcon.setImageResource(favoriteOn
                ? R.drawable.ic_favorite_filled : R.drawable.ic_favorite);
        favoriteIcon.setColorFilter(Ui.color(this,
                favoriteOn ? R.color.accent : R.color.label));
        favoriteIcon.setContentDescription(favoriteOn ? "已收藏" : "收藏");
        if (favoriteButton != null) {
            favoriteButton.setContentDescription(favoriteOn ? "已收藏" : "收藏");
        }
    }

    private void loadFavoriteState(String itemGuid) {
        favoriteOn = false;
        MediaRepository.get().isFavorite(itemGuid, new MediaRepository.DataCallback<Boolean>() {
            @Override
            public void onData(Boolean data) {
                favoriteOn = data != null && data;
                updateFavoriteButton();
            }

            @Override
            public void onError(String message) {
                // 状态拿不到就保持未收藏外观，不影响播放
            }
        });
    }

    private void toggleFavorite() {
        if (favoriteBusy || guid == null || guid.isEmpty()) return;
        favoriteBusy = true;
        final boolean next = !favoriteOn;
        MediaRepository.get().setFavorite(guid, next, new MediaRepository.DataCallback<Boolean>() {
            @Override
            public void onData(Boolean data) {
                favoriteBusy = false;
                favoriteOn = next;
                updateFavoriteButton();
                Toast.makeText(DetailActivity.this,
                        next ? "已加入收藏" : "已取消收藏", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                favoriteBusy = false;
                Toast.makeText(DetailActivity.this, "操作失败：" + message,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static int veil(int color, float alpha) {
        return (color & 0x00FFFFFF)
                | (Math.round(Math.max(0f, Math.min(1f, alpha)) * 255) << 24);
    }

    /** 分级 / 类型 / 国家（和首页大图副信息行同源） */
    private View buildMeta(int pad) {
        final TextView meta = Ui.text(this, "", 12f, R.color.label_2);
        meta.setSingleLine(true);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        meta.setVisibility(View.GONE);
        LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        metaLp.topMargin = Ui.dp(this, 6);
        metaLp.leftMargin = pad;
        metaLp.rightMargin = pad;
        meta.setLayoutParams(metaLp);
        loadMeta(meta);
        return meta;
    }

    private void loadMeta(final TextView view) {
        if (guid == null || guid.isEmpty()) return;
        MediaRepository.get().itemMetaByGuid(guid, new MediaRepository.DataCallback<ItemMeta>() {
            @Override
            public void onData(ItemMeta data) {
                if (data == null || data.isEmpty()) return;
                view.setText(data.line());
                view.setVisibility(View.VISIBLE);
            }

            @Override
            public void onError(String message) {
            }
        });
    }

    private View buildOverview() {
        String overview = info.item != null && info.item.overview != null
                ? info.item.overview : null;
        if (overview == null || overview.trim().isEmpty()) {
            overview = "暂无简介";
        }

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(sidePad, Ui.dp(this, 24), sidePad, 0);

        TextView label = Ui.text(this, "简介", 12f, R.color.label_3);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        if (android.os.Build.VERSION.SDK_INT >= 21) label.setLetterSpacing(0.04f);
        box.addView(label);

        TextView text = buildOverviewText(overview.trim());
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textLp.topMargin = Ui.dp(this, 8);
        text.setLayoutParams(textLp);
        box.addView(text);
        return box;
    }

    /** 简介文本：可用遥控器聚焦，确定后弹出全文 */
    private TextView buildOverviewText(final String overview) {
        TextView text = Ui.text(this, overview, 13f, R.color.label_2);
        text.setLineSpacing(Ui.dp(this, 3), 1f);
        text.setFocusable(true);
        text.setClickable(true);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            text.setDefaultFocusHighlightEnabled(false);
        }
        text.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        text.setOnFocusChangeListener((v, focused) -> v.setBackground(focused
                ? Ui.round(0, Ui.dp(this, 10),
                           Ui.color(this, R.color.accent), Ui.dp(this, 2))
                : null));
        text.setOnClickListener(v -> showOverviewDialog(overview));
        return text;
    }

    /** 全文简介弹窗：圆角卡片 + 可滚动正文，跟随浅色 / 深色主题 */
    private void showOverviewDialog(String overview) {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        final int pad = Ui.dp(this, 22);
        final int cardColor = Ui.color(this, R.color.surface_elevated);
        final int labelColor = Ui.color(this, R.color.label);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundDrawable(Ui.round(cardColor, Ui.dp(this, 20)));

        TextView title = new TextView(this);
        title.setText("简介");
        title.setTextSize(20f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(labelColor);
        title.setIncludeFontPadding(false);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.leftMargin = pad;
        titleLp.rightMargin = pad;
        titleLp.topMargin = Ui.dp(this, 22);
        titleLp.bottomMargin = Ui.dp(this, 10);
        card.addView(title, titleLp);

        MaxHeightScrollView scroll = new MaxHeightScrollView(this);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setFocusable(false);
        scroll.setFocusableInTouchMode(false);
        scroll.setMaxHeight(Math.round(Ui.screenHeight(this) * 0.56f));
        TextView body = Ui.text(this, overview, 15f, R.color.label);
        body.setLineSpacing(Ui.dp(this, 5), 1f);
        body.setPadding(pad, 0, pad, Ui.dp(this, 6));
        scroll.addView(body);
        card.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final Button close = new Button(this);
        close.setText("关闭");
        close.setAllCaps(false);
        close.setTextSize(14f);
        close.setTextColor(Ui.color(this, R.color.label));
        close.setTypeface(Typeface.DEFAULT_BOLD);
        close.setBackgroundDrawable(Ui.round(0, Ui.dp(this, 10),
                Ui.color(this, R.color.separator), Ui.dp(this, 1)));
        Ui.noTint(close);
        close.setFocusable(true);
        close.setMinWidth(0);
        close.setMinimumWidth(0);
        close.setMinHeight(0);
        close.setMinimumHeight(0);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            close.setDefaultFocusHighlightEnabled(false);
        }
        close.setOnFocusChangeListener((v, focused) -> v.setBackground(focused
                ? Ui.round(0, Ui.dp(this, 10),
                           Ui.color(this, R.color.accent), Ui.dp(this, 2))
                : Ui.round(0, Ui.dp(this, 10),
                           Ui.color(this, R.color.separator), Ui.dp(this, 1))));
        close.setOnClickListener(v -> dialog.dismiss());
        // 遥控器上下键用来滚动正文（正文比弹窗高时也能看全）
        close.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                    scroll.scrollBy(0, Ui.dp(this, 56));
                    return true;
                }
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP) {
                    scroll.scrollBy(0, -Ui.dp(this, 56));
                    return true;
                }
            }
            return false;
        });
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 44));
        closeLp.leftMargin = pad;
        closeLp.rightMargin = pad;
        closeLp.topMargin = Ui.dp(this, 10);
        closeLp.bottomMargin = Ui.dp(this, 20);
        card.addView(close, closeLp);

        dialog.setContentView(card);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.45f);
            int width = Math.min(Ui.screenWidth(this) - Ui.dp(this, 80),
                    Ui.dp(this, 560));
            w.setLayout(Math.max(Ui.dp(this, 260), width),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
        close.requestFocus();
    }

    /** 简介弹窗：正文高度自适应，超过上限才滚动 */
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

    // ==================== 选集 ====================

    private void loadSeasons(final int currentEpisode) {
        MediaRepository.get().seasons(guid, new MediaRepository.DataCallback<List<PlayListItem>>() {
            @Override
            public void onData(List<PlayListItem> data) {
                seasons.clear();
                if (data != null) seasons.addAll(data);
                if (seasons.isEmpty()) return;
                buildEpisodesSection(currentEpisode);
            }

            @Override
            public void onError(String message) {
            }
        });
    }

    private void buildEpisodesSection(int currentEpisode) {
        if (episodesHeader != null) content.removeView(episodesHeader);
        if (episodesBody != null) content.removeView(episodesBody);
        episodesHeader = null;
        episodesBody = null;
        seasonBar = null;
        seasonPosters = null;
        episodePosters = null;
        episodeCard = null;
        currentEpisodeShown = currentEpisode;

        // 标题行：左侧「选集」，右侧切换列表 / 海报
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(sidePad, Ui.dp(this, 28), sidePad, 0);

        TextView title = new TextView(this);
        title.setText("选集");
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                Ui.dimen(this, R.dimen.row_title_size));
        title.setTextColor(Ui.color(this, R.color.label));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setIncludeFontPadding(false);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button toggle = new Button(this);
        toggle.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 34)));
        toggle.setBackgroundResource(R.drawable.bg_season);
        Ui.noTint(toggle);
        toggle.setText(posterMode ? "列表" : "海报");
        toggle.setTextSize(13f);
        toggle.setAllCaps(false);
        toggle.setIncludeFontPadding(true);
        toggle.setTextColor(ContextCompat.getColorStateList(this, R.color.chip_text));
        toggle.setMinWidth(0);
        toggle.setMinimumWidth(0);
        toggle.setMinHeight(0);
        toggle.setMinimumHeight(0);
        toggle.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
        toggle.setFocusable(true);
        toggle.setId(View.generateViewId());
        toggle.setOnFocusChangeListener(Ui::focus);
        // 焦点路径：继续播放 ↓ → 列表/海报
        if (playButton != null) {
            toggle.setNextFocusUpId(playButton.getId());
            playButton.setNextFocusDownId(toggle.getId());
        }
        toggle.setOnClickListener(v -> {
            final int keepScroll = scroll.getScrollY();
            posterMode = !posterMode;
            Prefs.putBool(this, Prefs.EPISODE_POSTER_MODE, posterMode);
            buildEpisodesSection(currentEpisodeShown);
            if (modeToggle != null) {
                modeToggle.post(() -> modeToggle.requestFocus());
            }
            restoreScrollSoon(keepScroll);
        });
        titleRow.addView(toggle);
        modeToggle = toggle;
        header.addView(titleRow);

        content.addView(header);
        episodesHeader = header;

        int targetSeason = 0;
        if (info.item != null && info.item.seasonNumber > 0) targetSeason = info.item.seasonNumber;
        int defaultIndex = 0;
        for (int i = 0; i < seasons.size(); i++) {
            if (seasons.get(i).seasonNumber == targetSeason) {
                defaultIndex = i;
                break;
            }
        }
        // 切换视图时保留当前选中的季
        int index = selectedSeason >= 0 && selectedSeason < seasons.size()
                ? selectedSeason : defaultIndex;

        if (posterMode) {
            buildPosterBody(currentEpisode, index);
        } else {
            buildListBody(currentEpisode, index, header);
        }
    }

    /** 列表模式：季按钮 + 白色卡片里的集列表 */
    private void buildListBody(int currentEpisode, int index, LinearLayout header) {
        seasonBar = Ui.row(this);
        seasonBar.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 4));
        HorizontalScrollView seasonScroll = new HorizontalScrollView(this);
        seasonScroll.setHorizontalScrollBarEnabled(false);
        seasonScroll.setClipToPadding(false);
        seasonScroll.setClipChildren(false);
        seasonScroll.addView(seasonBar);
        header.addView(seasonScroll);
        listSeasonScroll = seasonScroll;

        episodeCard = new LinearLayout(this);
        episodeCard.setOrientation(LinearLayout.VERTICAL);
        episodeCard.setBackgroundResource(R.drawable.bg_card);
        episodeCard.setPadding(0, 0, 0, 0);
        episodeCard.setClipChildren(false);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.leftMargin = sidePad;
        cardLp.rightMargin = sidePad;
        cardLp.topMargin = Ui.dp(this, 10);
        episodeCard.setLayoutParams(cardLp);
        content.addView(episodeCard);
        episodesBody = episodeCard;

        renderSeasonBar(index);
        loadEpisodes(index, currentEpisode);
    }

    /** 海报模式：季海报行 + 集横版卡片行（和首页卡片一致） */
    private void buildPosterBody(int currentEpisode, int index) {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setClipChildren(false);

        HorizontalScrollView seasonScroll = new HorizontalScrollView(this);
        seasonScroll.setHorizontalScrollBarEnabled(false);
        seasonScroll.setClipToPadding(false);
        seasonScroll.setClipChildren(false);
        seasonScroll.setPadding(sidePad, Ui.dp(this, 16), sidePad, Ui.dp(this, 8));
        seasonScrollView = seasonScroll;
        seasonPosters = new LinearLayout(this);
        seasonPosters.setOrientation(LinearLayout.HORIZONTAL);
        seasonPosters.setClipChildren(false);
        seasonScroll.addView(seasonPosters);
        body.addView(seasonScroll);

        HorizontalScrollView episodeScroll = new HorizontalScrollView(this);
        episodeScroll.setHorizontalScrollBarEnabled(false);
        episodeScroll.setClipToPadding(false);
        episodeScroll.setClipChildren(false);
        episodeScroll.setPadding(sidePad, Ui.dp(this, 20), sidePad, Ui.dp(this, 10));
        episodeScrollView = episodeScroll;
        episodePosters = new LinearLayout(this);
        episodePosters.setOrientation(LinearLayout.HORIZONTAL);
        episodePosters.setClipChildren(false);
        episodeScroll.addView(episodePosters);
        body.addView(episodeScroll);

        content.addView(body);
        episodesBody = body;

        renderSeasonCards(index);
        loadEpisodes(index, currentEpisode);
    }

    private void renderSeasonBar(final int selectedIndex) {
        if (seasonBar == null) return;
        seasonBar.removeAllViews();

        for (int i = 0; i < seasons.size(); i++) {
            final int index = i;
            PlayListItem season = seasons.get(i);
            Button button = new Button(this);
            button.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 36)));
            button.setBackgroundResource(R.drawable.bg_season);
            Ui.noTint(button);
            button.setText("第 " + (season.seasonNumber > 0 ? season.seasonNumber : i + 1) + " 季");
            button.setTextSize(13f);
            button.setAllCaps(false);
            button.setIncludeFontPadding(true);
            button.setTextColor(ContextCompat.getColorStateList(this, R.color.chip_text));
            button.setSelected(index == selectedIndex);
            button.setMinWidth(0);
            button.setMinimumWidth(0);
            button.setMinHeight(0);
            button.setMinimumHeight(0);
            button.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
            button.setFocusable(true);
            button.setOnFocusChangeListener((v, focused) -> {
                Ui.focus(v, focused);
                if (focused) scrollCardIntoView(v, listSeasonScroll);
            });
            blockRowEdges(button, index == 0, index == seasons.size() - 1);
            button.setOnClickListener(v -> {
                if (selectedSeason == index) return;
                pendingScrollY = scroll.getScrollY();
                renderSeasonBar(index);
                loadEpisodes(index, episodeNumber());
                // 重新生成按钮后把焦点留在刚点的那一季上
                if (seasonBar != null) {
                    seasonBar.post(() -> {
                        if (index < seasonBar.getChildCount()) {
                            seasonBar.getChildAt(index).requestFocus();
                        }
                    });
                }
            });
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) button.getLayoutParams();
            lp.rightMargin = Ui.dp(this, 8);
            button.setLayoutParams(lp);
            seasonBar.addView(button);
        }
        // 行尾留白，最右侧按钮聚焦放大时不被裁掉
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 28), 1));
        seasonBar.addView(spacer);
    }

    private void renderSeasonCards(final int selectedIndex) {
        if (seasonPosters == null) return;
        seasonPosters.removeAllViews();
        seasonCardRings.clear();
        seasonCardViews.clear();
        int width = posterCardWidth();
        for (int i = 0; i < seasons.size(); i++) {
            final int index = i;
            PlayListItem season = seasons.get(i);
            View card = buildSeasonCard(season, i == selectedIndex, index, width);
            card.setOnClickListener(v -> selectSeason(index));
            seasonCardViews.add(card);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    width, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dp(this, 12);
            lp.topMargin = Ui.dp(this, 2);
            lp.bottomMargin = Ui.dp(this, 2);
            seasonPosters.addView(card, lp);
        }
        addRowEndSpacer(seasonPosters);
    }

    /** 海报模式切换季：只更新描边和集列表，不重建卡片，避免焦点/滚动跳走 */
    private void selectSeason(int index) {
        if (index == selectedSeason) return;
        pendingScrollY = scroll.getScrollY();
        selectedSeason = index;
        for (int i = 0; i < seasonCardRings.size(); i++) {
            View ring = seasonCardRings.get(i);
            if (ring == null) continue;
            ring.setVisibility(i == index ? View.VISIBLE : View.INVISIBLE);
        }
        loadEpisodes(index, episodeNumber());
        if (index < seasonCardViews.size()) {
            View card = seasonCardViews.get(index);
            card.post(card::requestFocus);
        }
    }

    /** 等布局稳定后再恢复滚动位置，避免焦点变化把页面顶上去 */
    private void restoreScrollSoon(final int y) {
        if (y < 0) return;
        scroll.scrollTo(0, y);
        scroll.postDelayed(() -> scroll.scrollTo(0, y), 80);
    }

    private void loadEpisodes(final int seasonIndex, final int currentEpisode) {
        if (seasonIndex < 0 || seasonIndex >= seasons.size()) return;
        selectedSeason = seasonIndex;
        currentEpisodes.clear();

        MediaRepository.get().episodes(seasons.get(seasonIndex).guid,
                new MediaRepository.DataCallback<List<PlayListItem>>() {
                    @Override
                    public void onData(List<PlayListItem> data) {
                        currentEpisodes.clear();
                        if (data != null) currentEpisodes.addAll(data);
                        if (posterMode) {
                            renderEpisodeCards(currentEpisode);
                        } else {
                            renderEpisodeList(currentEpisode);
                        }
                        if (pendingScrollY >= 0) {
                            int y = pendingScrollY;
                            pendingScrollY = -1;
                            restoreScrollSoon(y);
                        }
                    }

                    @Override
                    public void onError(String message) {
                    }
                });
    }

    /** 列表模式的集列表 */
    private void renderEpisodeList(int currentEpisode) {
        if (episodeCard == null) return;
        episodeCard.removeAllViews();
        if (currentEpisodes.isEmpty()) {
            TextView empty = Ui.subhead(DetailActivity.this, "本季暂无剧集");
            empty.setPadding(Ui.dp(DetailActivity.this, 16), Ui.dp(DetailActivity.this, 16),
                    Ui.dp(DetailActivity.this, 16), Ui.dp(DetailActivity.this, 16));
            episodeCard.addView(empty);
            return;
        }
        boolean highlighted = false;
        for (int i = 0; i < currentEpisodes.size(); i++) {
            PlayListItem ep = currentEpisodes.get(i);
            boolean current = !highlighted && ep.episodeNumber == currentEpisode
                    && currentEpisode > 0;
            if (current) highlighted = true;
            episodeCard.addView(episodeRow(ep, current, i, currentEpisodes.size()));
            if (i < currentEpisodes.size() - 1) {
                episodeCard.addView(Ui.divider(DetailActivity.this, 16));
            }
        }
    }

    /** 海报模式的集卡片行 */
    private void renderEpisodeCards(int currentEpisode) {
        if (episodePosters == null) return;
        episodePosters.removeAllViews();
        if (currentEpisodes.isEmpty()) {
            TextView empty = Ui.subhead(DetailActivity.this, "本季暂无剧集");
            empty.setPadding(Ui.dp(DetailActivity.this, 4), Ui.dp(DetailActivity.this, 4),
                    Ui.dp(DetailActivity.this, 4), Ui.dp(DetailActivity.this, 4));
            episodePosters.addView(empty);
            return;
        }
        int width = wideCardWidth();
        for (int i = 0; i < currentEpisodes.size(); i++) {
            PlayListItem ep = currentEpisodes.get(i);
            View card = buildEpisodeCard(ep, ep.episodeNumber == currentEpisode && currentEpisode > 0,
                    i, currentEpisodes.size(), width);
            card.setOnClickListener(v -> launchPlay(ep.guid, ep.title,
                    ep.tvTitle != null ? ep.tvTitle : "", ep.episodeNumber, ep.poster,
                    category(), Math.max(0, ep.ts), ep.duration, ep.parentGuid));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    width, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dp(this, 12);
            lp.topMargin = Ui.dp(this, 2);
            lp.bottomMargin = Ui.dp(this, 2);
            episodePosters.addView(card, lp);
        }
        addRowEndSpacer(episodePosters);
    }

    /** 行尾留白：最右边卡片聚焦放大时不会被屏幕边缘裁掉 */
    private void addRowEndSpacer(LinearLayout row) {
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                Ui.dp(this, 28), 1));
        row.addView(spacer);
    }

    // ==================== 与首页一致的卡片 ====================

    private int availWidth() {
        return Ui.screenWidth(this) - Ui.railInset(this)
                - Ui.dimen(this, R.dimen.screen_pad);
    }

    private int posterCardWidth() {
        if (Ui.isLandscape(this)) return Math.round(Ui.screenHeight(this) * 0.27f);
        return Math.round(availWidth() * 0.40f);
    }

    private int wideCardWidth() {
        if (Ui.isLandscape(this)) return Math.round(Ui.contentWidth(this) * 0.30f);
        return Math.round(availWidth() * 0.72f);
    }

    private View buildSeasonCard(PlayListItem season, boolean selected, int index, int width) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFocusable(true);
        root.setClipChildren(false);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            root.setDefaultFocusHighlightEnabled(false);
        }
        root.setLayoutParams(new LinearLayout.LayoutParams(
                width, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout box = new FrameLayout(this);
        box.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.round(width * 1.5f)));
        box.setBackground(Ui.round(Ui.color(this, R.color.image_placeholder), Ui.dp(this, 4)));
        if (android.os.Build.VERSION.SDK_INT >= 21) box.setClipToOutline(true);
        root.addView(box);

        RoundedImageView image = new RoundedImageView(this);
        image.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setCornerRadius(4);
        image.setBackgroundColor(Ui.color(this, R.color.image_placeholder));
        box.addView(image);
        String url = Urls.poster(baseUrl, season.poster, 400);
        if (url != null) {
            SimpleImageLoader.load(url, image, FnApiManager.getInstance().getClient());
        }

        View ring = selectionRing(selected);
        box.addView(ring, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        seasonCardRings.add(ring);

        TextView title = new TextView(this);
        title.setText("第 " + (season.seasonNumber > 0 ? season.seasonNumber : 1) + " 季");
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                Ui.dimen(this, R.dimen.card_title_size));
        title.setTextColor(Ui.color(this, R.color.label));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setIncludeFontPadding(false);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = Ui.dp(this, 7);
        title.setLayoutParams(titleLp);
        root.addView(title);

        int count = season.localNumberOfEpisodes > 0
                ? season.localNumberOfEpisodes : season.numberOfEpisodes;
        String year = Fmt.year(season.airDate);
        StringBuilder meta = new StringBuilder();
        if (count > 0) meta.append("共 ").append(count).append(" 集");
        if (year != null) {
            if (meta.length() > 0) meta.append(" · ");
            meta.append(year);
        }
        if (meta.length() > 0) {
            TextView sub = Ui.text(this, meta.toString(), 11f, R.color.label_2);
            LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            subLp.topMargin = Ui.dp(this, 2);
            sub.setLayoutParams(subLp);
            root.addView(sub);
        }

        root.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? 1.06f : 1f).scaleY(focused ? 1.06f : 1f)
                    .setDuration(150).start();
            ring.setVisibility(focused || index == selectedSeason
                    ? View.VISIBLE : View.INVISIBLE);
            if (focused) scrollCardIntoView(root, seasonScrollView);
        });
        blockRowEdges(root, index == 0, index == seasons.size() - 1);
        return root;
    }

    private View buildEpisodeCard(PlayListItem ep, boolean current, int index, int count, int width) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFocusable(true);
        root.setClipChildren(false);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            root.setDefaultFocusHighlightEnabled(false);
        }
        root.setLayoutParams(new LinearLayout.LayoutParams(
                width, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout box = new FrameLayout(this);
        box.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.round(width * 9f / 16f)));
        box.setBackground(Ui.round(Ui.color(this, R.color.image_placeholder), Ui.dp(this, 4)));
        if (android.os.Build.VERSION.SDK_INT >= 21) box.setClipToOutline(true);
        root.addView(box);

        RoundedImageView image = new RoundedImageView(this);
        image.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setCornerRadius(4);
        image.setBackgroundColor(Ui.color(this, R.color.image_placeholder));
        box.addView(image);
        String url = Urls.poster(baseUrl, ep.poster, 600);
        if (url != null) {
            SimpleImageLoader.load(url, image, FnApiManager.getInstance().getClient());
        }

        View ring = selectionRing(current);
        box.addView(ring, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = new TextView(this);
        title.setText((ep.episodeNumber > 0 ? "第 " + ep.episodeNumber + " 集" : "")
                + (ep.title != null && !ep.title.isEmpty() ? " · " + ep.title : ""));
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                Ui.dimen(this, R.dimen.card_title_size));
        title.setTextColor(Ui.color(this, current ? R.color.accent : R.color.label));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setIncludeFontPadding(false);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = Ui.dp(this, 7);
        title.setLayoutParams(titleLp);
        root.addView(title);

        String overview = ep.overview != null ? ep.overview.trim() : "";
        if (!overview.isEmpty()) {
            TextView desc = Ui.text(this, overview, 11f, R.color.label_2);
            desc.setMaxLines(2);
            desc.setEllipsize(TextUtils.TruncateAt.END);
            desc.setLineSpacing(Ui.dp(this, 1.5f), 1f);
            LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            descLp.topMargin = Ui.dp(this, 3);
            desc.setLayoutParams(descLp);
            root.addView(desc);
        }

        StringBuilder sub = new StringBuilder();
        if (ep.duration > 0) sub.append(Fmt.clock(ep.duration));
        if (ep.watched == 1) {
            if (sub.length() > 0) sub.append("  ·  ");
            sub.append("已看");
        }
        if (sub.length() > 0) {
            TextView meta = Ui.text(this, sub.toString(), 11f, R.color.label_3);
            LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            metaLp.topMargin = Ui.dp(this, 3);
            meta.setLayoutParams(metaLp);
            root.addView(meta);
        }

        root.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? 1.06f : 1f).scaleY(focused ? 1.06f : 1f)
                    .setDuration(150).start();
            ring.setVisibility(focused || current ? View.VISIBLE : View.INVISIBLE);
            if (focused) scrollCardIntoView(root, episodeScrollView);
        });
        blockRowEdges(root, index == 0, index == count - 1);
        return root;
    }

    /** 卡片获得焦点时按需最小滚动，保证整张卡片可见，左右相邻卡片仍露一部分 */
    private void scrollCardIntoView(final View card, final HorizontalScrollView sv) {
        if (sv == null) return;
        sv.post(() -> {
            int margin = Ui.dp(this, 8);
            int scrollX = sv.getScrollX();
            int viewLeft = scrollX + sv.getPaddingLeft();
            int viewRight = scrollX + sv.getWidth() - sv.getPaddingRight();
            int left = card.getLeft();
            int right = card.getRight();
            int target;
            if (left < viewLeft + margin) {
                target = left - sv.getPaddingLeft() - margin;
            } else if (right > viewRight - margin) {
                target = right - sv.getWidth() + sv.getPaddingRight() + margin;
            } else {
                return;
            }
            int max = Math.max(0,
                    (sv.getChildCount() > 0 ? sv.getChildAt(0).getWidth() : 0)
                            - sv.getWidth() + sv.getPaddingLeft() + sv.getPaddingRight());
            sv.smoothScrollTo(Math.max(0, Math.min(target, max)), 0);
        });
    }

    /** 选中/聚焦用的描边圈（和首页卡片一致：只描边，不填充） */
    private View selectionRing(boolean selected) {
        View ring = new View(this);
        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        bg.setColor(0);
        bg.setCornerRadius(Ui.dp(this, 4));
        bg.setStroke(Math.max(3, Ui.dp(this, 3f)), Ui.color(this, R.color.accent));
        ring.setBackground(bg);
        ring.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
        return ring;
    }

    private View episodeRow(final PlayListItem ep, boolean current, int index, int count) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(rowBackground(index, count));
        row.setFocusable(true);
        row.setClickable(true);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            row.setDefaultFocusHighlightEnabled(false);
        }
        row.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView number = Ui.text(this, String.valueOf(ep.episodeNumber > 0 ? ep.episodeNumber : "?"),
                14f, current ? R.color.accent : R.color.label_3);
        number.setTypeface(Typeface.DEFAULT_BOLD);
        number.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 34),
                ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(number);

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        text.setLayoutParams(textLp);

        TextView name = Ui.text(this, ep.title != null ? ep.title : "未命名", 15f,
                current ? R.color.accent : R.color.label);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        text.addView(name);

        StringBuilder sub = new StringBuilder();
        if (ep.duration > 0) sub.append(Fmt.clock(ep.duration));
        if (ep.watched == 1) {
            if (sub.length() > 0) sub.append("  ·  ");
            sub.append("已看");
        }
        if (sub.length() > 0) {
            TextView meta = Ui.text(this, sub.toString(), 11f, R.color.label_3);
            LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            metaLp.topMargin = Ui.dp(this, 3);
            meta.setLayoutParams(metaLp);
            text.addView(meta);
        }
        row.addView(text);

        row.addView(Ui.icon(this, R.drawable.ic_play, 15, current ? R.color.accent : R.color.label_3));

        row.setOnFocusChangeListener(Ui::focus);
        row.setOnClickListener(v -> launchPlay(ep.guid, ep.title,
                ep.tvTitle != null ? ep.tvTitle : "", ep.episodeNumber, ep.poster,
                category(), Math.max(0, ep.ts), ep.duration, ep.parentGuid));
        blockRowEdges(row, true, true);
        return row;
    }

    /** 选集行背景：中间直角铺满，首行 / 末行圆角与卡片一致 */
    private int rowBackground(int index, int count) {
        if (count <= 1) return R.drawable.bg_row_single;
        if (index == 0) return R.drawable.bg_row_first;
        if (index == count - 1) return R.drawable.bg_row_last;
        return R.drawable.bg_row_mid;
    }

    // ==================== 播放 ====================

    private void launchPlay(String itemGuid, String title, String tvTitle, int episode,
                            String poster, String category, long ts, long duration, String parentGuid) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("guid", itemGuid);
        intent.putExtra("title", title);
        intent.putExtra("tv_title", tvTitle == null ? "" : tvTitle);
        intent.putExtra("episode_number", episode);
        intent.putExtra("poster", poster);
        intent.putExtra("category", category == null ? "" : category);
        intent.putExtra("ts", ts);
        intent.putExtra("duration", duration);
        if (parentGuid != null) intent.putExtra("parent_guid", parentGuid);
        startActivity(intent);
    }

    private String title() {
        return info.item != null && info.item.title != null ? info.item.title : passedTitle;
    }

    private String tvTitle() {
        return info.item != null && info.item.tvTitle != null ? info.item.tvTitle : "";
    }

    private int episodeNumber() {
        return info.item != null ? info.item.episodeNumber : 0;
    }

    private String parentGuid() {
        if (info.parentGuid != null && !info.parentGuid.isEmpty()) return info.parentGuid;
        return null;
    }

    private String category() {
        return passedCategory != null ? passedCategory : "";
    }
}
