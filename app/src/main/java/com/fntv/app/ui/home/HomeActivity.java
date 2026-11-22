package com.fntv.app.ui.home;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fntv.app.BuildConfig;
import com.fntv.app.R;
import com.fntv.app.UpdateManager;
import com.fntv.app.api.FnApiManager;
import com.fntv.app.api.model.ApiResponse;
import com.fntv.app.api.model.MediaDbItem;
import com.fntv.app.api.model.PlayInfoResponse;
import com.fntv.app.api.model.ItemMeta;
import com.fntv.app.api.model.PlayListItem;
import com.fntv.app.api.model.UserInfoResponse;
import com.fntv.app.core.Prefs;
import com.fntv.app.core.ThemeMode;
import com.fntv.app.data.AuthRepository;
import com.fntv.app.data.MediaRepository;
import com.fntv.app.ui.AppearanceDialog;
import com.fntv.app.ui.Ui;
import com.fntv.app.ui.browse.BrowseActivity;
import com.fntv.app.ui.common.GridSpaceDecoration;
import com.fntv.app.ui.common.PosterAdapter;
import com.fntv.app.ui.detail.DetailActivity;
import com.fntv.app.ui.library.LibraryScreen;
import com.fntv.app.ui.login.LoginActivity;
import com.fntv.app.ui.settings.SettingsScreen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 主界面（Netflix TV 结构）：
 * 左侧图标导航 + 全幅大图推荐位 + 横向卡片行。
 */
public class HomeActivity extends AppCompatActivity
        implements HomeAdapter.Listener, LibraryScreen.Listener, SettingsScreen.Host {

    private static final int PANEL_HOME = 0;
    private static final int PANEL_LIBRARY = 1;
    private static final int PANEL_SEARCH = 2;
    private static final int PANEL_FAVORITE = 3;
    private static final int PANEL_SETTINGS = 4;

    private FrameLayout rootLayout, content, rail;
    private RecyclerView homeList, searchGrid, favoritesGrid;
    private LinearLayout panelLibrary, panelSearch, panelFavorites, libraryContainer, settingsContainer;
    private View panelSettings, libraryTopSpacer, searchTopSpacer, searchBox, topScrim;
    private View favoritesTopSpacer, favoritesHeader;
    private EditText etSearch;
    private TextView searchEmpty, favoritesSubtitle, favoritesEmpty;

    private final List<View> railItems = new ArrayList<>();
    private final List<AppCompatImageView> railIcons = new ArrayList<>();
    private int railSelected = 0;

    private HomeAdapter homeAdapter;
    private PosterAdapter searchAdapter, favoritesAdapter;
    private final List<PlayListItem> favoriteItems = new ArrayList<>();
    /** 切到收藏页后，等列表加载完再把焦点移进去 */
    private boolean focusFavoritesOnLoad;
    private final List<MediaDbItem> libraries = new ArrayList<>();
    private final Map<String, List<PlayListItem>> previews = new HashMap<>();
    private final Map<String, String> backdrops = new HashMap<>();
    private final Map<String, ItemMeta> heroMetas = new HashMap<>();
    private final Set<String> heroMetaPending = new HashSet<>();
    /** 每季的第一集（用于「从第 X 集开始」） */
    private final Map<String, PlayListItem> seasonFirstEpisodes = new HashMap<>();
    private final Set<String> seasonFirstPending = new HashSet<>();
    private List<PlayListItem> continueItems = new ArrayList<>();
    private List<PlayListItem> liveItems = new ArrayList<>();
    private boolean loading = true;
    private String homeError;
    private int currentPanel = PANEL_HOME;
    private String baseUrl;
    private String displayName;
    private long lastBackTime;
    private UpdateManager updateManager;
    private float topScrimAlpha;
    private int navLeft = -1;
    private int navBottom = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeMode.apply(this);
        setContentView(R.layout.activity_home);
        Ui.applyImmersive(this, this::applyNavInsets);

        baseUrl = Prefs.host(this);
        AuthRepository.prepare(this, baseUrl);
        if (FnApiManager.getInstance().getApi() == null) {
            goLogin();
            return;
        }

        bindViews();
        buildRail();
        switchPanel(PANEL_HOME);

        homeAdapter = new HomeAdapter(this, baseUrl, this);
        homeList.setLayoutManager(new com.fntv.app.ui.common.HomeLayoutManager(this));
        homeList.setAdapter(homeAdapter);
        homeList.setItemAnimator(null);
        homeList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView rv, int dx, int dy) {
                if (currentPanel != PANEL_HOME) return;
                float alpha = Math.min(1f, Math.max(0f,
                        rv.computeVerticalScrollOffset() / (float) Ui.dp(HomeActivity.this, 120)));
                topScrimAlpha = alpha;
                topScrim.setAlpha(alpha);
            }
        });
        setupFocusScroll();

        if (savedInstanceState != null) {
            currentPanel = savedInstanceState.getInt("panel", PANEL_HOME);
        }

        setupSearch();
        loadAll();
        switchPanel(currentPanel);

        if (TextUtils.isEmpty(Prefs.str(this, Prefs.USER, ""))) {
            goLogin();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // 朝向变化后重建首页（大图比例 / 卡片尺寸随屏幕方向变化）
        if (homeAdapter != null) {
            homeAdapter = new HomeAdapter(this, baseUrl, this);
            homeList.setLayoutManager(new com.fntv.app.ui.common.HomeLayoutManager(this));
            homeList.setAdapter(homeAdapter);
            rebuildHome();
        }
        if (rail != null) buildRail();
        if (favoritesGrid != null) setupFavoritesGrid();
        applyPanelInsets();
    }

    /**
     * 遥控器 / 键盘移动焦点时，把焦点所在控件滚进可视区。
     * 之前焦点能在看不见的地方移动（比如滚到最上面的「查看更多」、大图底部的按钮），
     * 屏幕却不动，看起来就像"选不上"。
     */
    private void setupFocusScroll() {
        rootLayout.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {
            if (newFocus == null || homeList == null) return;
            if (currentPanel != PANEL_HOME) return;
            if (!isInside(newFocus, homeList)) return;
            scrollFocusIntoView(newFocus);
        });
    }

    private boolean isInside(View v, View ancestor) {
        View p = v;
        while (p != null) {
            if (p == ancestor) return true;
            android.view.ViewParent parent = p.getParent();
            p = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    private void scrollFocusIntoView(View target) {
        if (target.getWidth() <= 0 || target.getHeight() <= 0) return;
        // 焦点事件可能在布局过程中触发，直接滚会打断 RecyclerView 的布局流程
        if (homeList.isComputingLayout()) {
            final View t = target;
            homeList.post(() -> scrollFocusIntoView(t));
            return;
        }

        // 焦点在大图里：直接把大图整块露出来，避免上半部分被切掉
        View item = homeList.findContainingItemView(target);
        if (item != null && homeList.getChildAdapterPosition(item) == 0) {
            homeList.scrollToPosition(0);
            return;
        }

        // 横屏遥控器：焦点进到某行卡片时，把这一行的标题也带出来，别只露半截。
        // 这里直接接管定位，不再走下面的“卡片可见”校正，避免两次滚动把标题顶出去。
        if (Ui.isLandscape(this) && item != null && homeAdapter != null) {
            int pos = homeList.getChildAdapterPosition(item);
            int type = homeAdapter.typeAt(pos);
            boolean isRow = type == HomeItem.TYPE_POSTERS || type == HomeItem.TYPE_CONTINUE
                    || type == HomeItem.TYPE_LIVE;
            if (isRow && homeAdapter.typeAt(pos - 1) == HomeItem.TYPE_ROW_TITLE) {
                final View rowItem = item;
                homeList.post(() -> {
                    snapRowHeader(rowItem);
                    homeList.post(() -> snapRowHeader(rowItem)); // 布局后再钉一次
                });
                return;
            }
        }

        Rect r = new Rect(0, 0, target.getWidth(), target.getHeight());
        try {
            homeList.offsetDescendantRectToMyCoords(target, r);
        } catch (IllegalArgumentException e) {
            return;
        }
        int pad = Ui.dp(this, 14);
        int topLimit = Ui.isLandscape(this)
                ? landscapeTopLimit()
                : homeList.getPaddingTop() + pad;
        int bottomLimit = homeList.getHeight() - homeList.getPaddingBottom() - pad;
        int dy = 0;
        if (r.top < topLimit) dy = r.top - topLimit;
        else if (r.bottom > bottomLimit) dy = r.bottom - bottomLimit;
        if (dy != 0) homeList.scrollBy(0, dy);
    }

    // ==================== 遥控器上下键路由 ====================
    // 规则（跨行）：从某行卡片按下 → 下一行的「查看更多」；再按下 → 这一行的卡片；
    // 从某行卡片按上 → 本行的「查看更多」；再按上 → 上一行的卡片。

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && currentPanel == PANEL_HOME
                && homeList != null) {
            int k = event.getKeyCode();
            if (k == KeyEvent.KEYCODE_DPAD_DOWN || k == KeyEvent.KEYCODE_DPAD_UP) {
                if (moveHomeFocus(k == KeyEvent.KEYCODE_DPAD_DOWN)) return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean moveHomeFocus(boolean down) {
        View focused = getCurrentFocus();
        if (focused == null) return false;
        View item = homeList.findContainingItemView(focused);
        if (item == null) return false;
        int pos = homeList.getChildAdapterPosition(item);
        if (pos <= 0) {
            // 大图区域：按下键走系统默认（在大图内部切换）
            if (down) return false;
            // 只有已经在大图最上面的控件时，才拦掉上键，避免跳到左侧菜单；
            // 大图内部的「继续播放 / 从第 X 集开始 / 剧集与更多信息」之间仍可上下移动
            View up = focused.focusSearch(View.FOCUS_UP);
            if (up != null && isInside(up, homeList)) return false;
            return true;
        }

        boolean onMore = "home_more".equals(focused.getTag());
        int type = homeAdapter == null ? -1 : homeAdapter.typeAt(pos);
        boolean isRow = type == HomeItem.TYPE_POSTERS
                || type == HomeItem.TYPE_CONTINUE || type == HomeItem.TYPE_LIVE;
        if (!onMore && !isRow) return false;

        if (onMore) {
            // 查看更多：下 → 本行卡片；上 → 上一行卡片
            if (down) return focusRowCards(pos + 1, 0);
            return focusRowCards(pos - 1, columnOf(focused));
        }
        // 卡片：下 → 下一行「查看更多」；上 → 本行「查看更多」
        if (down) return focusRowHeader(pos + 1, true);
        return focusRowHeader(pos - 1, false);
    }

    /** 当前卡片在横排里的索引 */
    private int columnOf(View focused) {
        android.view.ViewParent p = focused.getParent();
        if (p instanceof RecyclerView) {
            int i = ((RecyclerView) p).getChildAdapterPosition(focused);
            if (i >= 0) return i;
        }
        return 0;
    }

    private boolean focusRowHeader(int pos, boolean fallbackToCards) {
        if (homeAdapter == null) return false;
        if (homeAdapter.typeAt(pos) != HomeItem.TYPE_ROW_TITLE) return false;
        RecyclerView.ViewHolder vh = homeList.findViewHolderForAdapterPosition(pos);
        if (vh == null) {
            homeList.scrollToPosition(pos);
            homeList.post(() -> focusRowHeader(pos, fallbackToCards));
            return true;
        }
        ViewGroup row = (ViewGroup) vh.itemView;
        View more = row.getChildCount() > 1 ? row.getChildAt(1) : null;
        if (more != null && more.getVisibility() == View.VISIBLE && more.isFocusable()) {
            return more.requestFocus();
        }
        // 该行没有「查看更多」（继续观看 / 直播频道）→ 落到它的卡片
        return fallbackToCards && focusRowCards(pos + 1, 0);
    }

    private boolean focusRowCards(int pos, int column) {
        if (homeAdapter == null) return false;
        int type = homeAdapter.typeAt(pos);
        if (type != HomeItem.TYPE_POSTERS && type != HomeItem.TYPE_CONTINUE
                && type != HomeItem.TYPE_LIVE) {
            return false;
        }
        // 横屏：进这一行时先把整行标题顶到状态栏下方（罩子下面），避免标题被渐隐罩子压白
        if (Ui.isLandscape(this) && homeAdapter.typeAt(pos - 1) == HomeItem.TYPE_ROW_TITLE) {
            RecyclerView.LayoutManager lm = homeList.getLayoutManager();
            if (lm instanceof LinearLayoutManager) {
                ((LinearLayoutManager) lm).scrollToPositionWithOffset(pos - 1, landscapeTopLimit());
            }
        }
        RecyclerView.ViewHolder vh = homeList.findViewHolderForAdapterPosition(pos);
        if (vh == null) {
            homeList.scrollToPosition(pos);
            homeList.post(() -> focusRowCards(pos, column));
            return true;
        }
        if (!(vh.itemView instanceof RecyclerView)) return false;
        final RecyclerView rv = (RecyclerView) vh.itemView;
        final int col = Math.max(0, column);
        if (rv.getLayoutManager() == null) return false;
        rv.scrollToPosition(col);
        View target = rv.getLayoutManager().findViewByPosition(col);
        if (target == null) target = rv.getChildAt(0);
        if (target != null) return target.requestFocus();
        rv.post(() -> {
            View t = rv.getLayoutManager() == null ? null
                    : rv.getLayoutManager().findViewByPosition(col);
            if (t == null) t = rv.getChildAt(0);
            if (t != null) t.requestFocus();
        });
        return true;
    }

    /** 横屏：把某行卡片的标题一起滚进可视区（标题已经完整显示时不动） */
    private void snapRowHeader(View rowItem) {
        if (homeAdapter == null || rowItem == null || !Ui.isLandscape(this)) return;
        int pos = homeList.getChildAdapterPosition(rowItem);
        if (pos <= 0) return;
        if (homeAdapter.typeAt(pos - 1) != HomeItem.TYPE_ROW_TITLE) return;

        // 直接顶到固定位置（LayoutManager 自带的焦点滚动已在 HomeLayoutManager 里关掉）
        RecyclerView.LayoutManager lm = homeList.getLayoutManager();
        if (lm instanceof LinearLayoutManager) {
            ((LinearLayoutManager) lm).scrollToPositionWithOffset(pos - 1, landscapeTopLimit());
        }
    }

    private void repinRowHeader(int pos) {
        View f = getCurrentFocus();
        if (f == null) return;
        View item = homeList.findContainingItemView(f);
        if (item == null) return;
        int p = homeList.getChildAdapterPosition(item);
        if (p != pos && p != pos - 1) return; // 焦点已经不在这一行了
        RecyclerView.LayoutManager lm = homeList.getLayoutManager();
        if (lm instanceof LinearLayoutManager) {
            ((LinearLayoutManager) lm).scrollToPositionWithOffset(pos - 1, landscapeTopLimit());
        }
    }

    /** 横屏顶部安全线：状态栏 + 顶部渐隐罩子的高度，再留 8dp，避免标题被罩子压白 */
    private int landscapeTopLimit() {
        return Ui.statusBarHeight(this) + Ui.dp(this, 20);
    }

    private void bindViews() {
        rootLayout = findViewById(R.id.rootLayout);
        content = findViewById(R.id.content);
        rail = findViewById(R.id.rail);
        homeList = findViewById(R.id.homeList);
        panelLibrary = findViewById(R.id.panelLibrary);
        panelSearch = findViewById(R.id.panelSearch);
        panelFavorites = findViewById(R.id.panelFavorites);
        panelSettings = findViewById(R.id.panelSettings);
        libraryContainer = findViewById(R.id.libraryContainer);
        settingsContainer = findViewById(R.id.settingsContainer);
        libraryTopSpacer = findViewById(R.id.libraryTopSpacer);
        searchTopSpacer = findViewById(R.id.searchTopSpacer);
        favoritesTopSpacer = findViewById(R.id.favoritesTopSpacer);
        favoritesHeader = findViewById(R.id.favoritesHeader);
        favoritesSubtitle = findViewById(R.id.favoritesSubtitle);
        favoritesEmpty = findViewById(R.id.favoritesEmpty);
        searchBox = findViewById(R.id.searchBox);
        etSearch = findViewById(R.id.etSearch);
        searchEmpty = findViewById(R.id.searchEmpty);
        searchGrid = findViewById(R.id.searchGrid);
        favoritesGrid = findViewById(R.id.favoritesGrid);

        AppCompatImageView searchIcon = findViewById(R.id.ivSearch);
        searchIcon.setColorFilter(Ui.color(this, R.color.label_3));

        topScrim = findViewById(R.id.topScrim);
        int topInset = Ui.statusBarHeight(this);
        ViewGroup.LayoutParams scrimLp = topScrim.getLayoutParams();
        scrimLp.height = topInset + Ui.dp(this, 12);
        topScrim.setLayoutParams(scrimLp);
        int surface = Ui.color(this, R.color.surface_grouped);
        topScrim.setBackground(Ui.gradientV(
                new int[]{surface, surface, 0x00FFFFFF & surface},
                new float[]{0f, 0.66f, 1f}));
        topScrim.setAlpha(0f);

        setupSearchGrid();
        setupFavoritesGrid();
        applyPanelInsets();
    }

    // ==================== 图标导航（横屏左侧竖排 / 竖屏底部横排） ====================

    private void buildRail() {
        rail.removeAllViews();
        boolean land = Ui.isLandscape(this);
        int surface = Ui.color(this, R.color.surface_grouped);

        FrameLayout.LayoutParams railLp;
        if (land) {
            // 横屏：左侧一条竖栏，图标从顶部往下排
            rail.setBackground(Ui.gradientH(
                    new int[]{surface, surface, 0x00FFFFFF & surface},
                    new float[]{0f, 0.62f, 1f}));
            railLp = new FrameLayout.LayoutParams(
                    Ui.dimen(this, R.dimen.rail_width), ViewGroup.LayoutParams.MATCH_PARENT);
            railLp.gravity = Gravity.LEFT;
        } else {
            // 竖屏：底部一条横栏，四个图标等分屏宽（回到早期底部 Tab 的排布）
            rail.setBackgroundColor(surface);
            railLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Ui.dimen(this, R.dimen.nav_bottom_height));
            railLp.gravity = Gravity.BOTTOM;
        }
        rail.setLayoutParams(railLp);

        if (!land) {
            // 底部横栏顶部一条发丝线，和内容区分开
            View line = new View(this);
            FrameLayout.LayoutParams lineLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(this, 0.5f)));
            lineLp.gravity = Gravity.TOP;
            line.setBackgroundColor(Ui.color(this, R.color.separator));
            rail.addView(line, lineLp);
        }

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(land ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        column.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams colLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (land) {
            colLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            colLp.topMargin = Ui.statusBarHeight(this) + Ui.dp(this, 6);
        } else {
            colLp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        }
        rail.addView(column, colLp);

        addRailItem(column, R.drawable.ic_search, 0, () -> switchPanel(PANEL_SEARCH));
        addRailItem(column, R.drawable.ic_rail_home, 1, () -> switchPanel(PANEL_HOME));
        addRailItem(column, R.drawable.ic_nav_library, 2, () -> switchPanel(PANEL_LIBRARY));
        addRailItem(column, R.drawable.ic_nav_favorite, 3, () -> switchPanel(PANEL_FAVORITE));
        addRailItem(column, R.drawable.ic_nav_settings, 4, () -> switchPanel(PANEL_SETTINGS));

        updateRail();
    }

    private void addRailItem(LinearLayout column, int iconRes, int panel, Runnable action) {
        FrameLayout box = new FrameLayout(this);
        LinearLayout.LayoutParams lp;
        if (Ui.isLandscape(this)) {
            lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    Ui.dimen(this, R.dimen.rail_item_height));
        } else {
            // 底部横栏：每个图标等分屏宽
            lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        }
        box.setLayoutParams(lp);
        box.setFocusable(true);
        box.setClickable(true);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            box.setDefaultFocusHighlightEnabled(false);
        }

        AppCompatImageView icon = new AppCompatImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(Ui.color(this, R.color.label_3));
        int size = Ui.dimen(this, R.dimen.rail_icon);
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(size, size);
        iconLp.gravity = Gravity.CENTER;
        box.addView(icon, iconLp);

        final int index = railItems.size();
        box.setOnClickListener(v -> action.run());
        box.setOnFocusChangeListener((v, focused) -> {
            icon.setColorFilter(Ui.color(this, focused ? R.color.label : railColor(index)));
            v.animate().scaleX(focused ? 1.14f : 1f).scaleY(focused ? 1.14f : 1f)
                    .setDuration(130).start();
        });

        column.addView(box);
        railItems.add(box);
        railIcons.add(icon);
    }

    private int railColor(int index) {        return railSelected >= 0 && index == railSelected
                ? R.color.accent : R.color.label_2;
    }

    private void updateRail() {
        for (int i = 0; i < railIcons.size(); i++) {
            boolean selected = i == railSelected;
            railIcons.get(i).setColorFilter(Ui.color(this,
                    selected ? R.color.accent : R.color.label_2));
        }
    }

    // ==================== 面板 ====================

    /**
     * 全屏铺满后，横屏秃海/手势条会压在内容上：
     * 侧栏整体右移一段；竖屏图标在底部，内容底部留出一条栏高。
     */
    private void applyNavInsets(int left, int right, int bottom) {
        boolean changed = left != navLeft || bottom != navBottom;
        navLeft = left;
        navBottom = bottom;
        if (rail != null && Ui.isLandscape(this)) {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) rail.getLayoutParams();
            lp.leftMargin = left;
            lp.rightMargin = right;
            rail.setLayoutParams(lp);
        } else if (rail != null) {
            // 竖屏底部栏要盖住手势条那块，图标仍居中在可见区域
            ViewGroup.LayoutParams lp = rail.getLayoutParams();
            lp.height = bottomBarHeight();
            rail.setLayoutParams(lp);
        }
        applyPanelInsets();
        // 卡片尺寸依赖 Ui.railInset（含安全区），值变了要重新绑定
        if (changed && homeAdapter != null) homeAdapter.notifyDataSetChanged();
    }

    /** 竖屏底部图标栏的总高度（含盖住手势条的那一段） */
    private int bottomBarHeight() {
        return Ui.bottomBarHeight(this) + Math.max(0, navBottom);
    }

    private void applyPanelInsets() {
        if (libraryContainer == null) return;
        int topInset = Ui.statusBarHeight(this);
        int left = Ui.railInset(this);
        int sidePad = Ui.dimen(this, R.dimen.screen_pad);
        // 宽屏时限制内容宽度，避免大段文字被拉得过宽
        int avail = Ui.screenWidth(this) - left - sidePad;
        int right = Math.max(sidePad, avail - Ui.dp(this, 720));
        int bottom = bottomBarHeight();

        if (homeList != null) homeList.setPadding(0, 0, 0, bottom + Ui.dp(this, 28));

        ViewGroup.LayoutParams lpTops = libraryTopSpacer.getLayoutParams();
        lpTops.height = topInset + Ui.dp(this, 10);
        libraryTopSpacer.setLayoutParams(lpTops);

        lpTops = searchTopSpacer.getLayoutParams();
        lpTops.height = topInset + Ui.dp(this, 10);
        searchTopSpacer.setLayoutParams(lpTops);

        lpTops = favoritesTopSpacer.getLayoutParams();
        lpTops.height = topInset + Ui.dp(this, 10);
        favoritesTopSpacer.setLayoutParams(lpTops);

        favoritesHeader.setPadding(left, 0, right, 0);
        favoritesGrid.setPadding(left, Ui.dp(this, 14), right, Ui.dp(this, 24) + bottom);

        libraryContainer.setPadding(left, 0, right, Ui.dp(this, 28) + bottom);
        settingsContainer.setPadding(left, topInset + Ui.dp(this, 8), right,
                Ui.dp(this, 32) + bottom);

        ViewGroup.LayoutParams searchLp = searchBox.getLayoutParams();
        if (searchLp instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) searchLp;
            mlp.leftMargin = left;
            mlp.rightMargin = right;
            searchBox.setLayoutParams(mlp);
        }

        searchGrid.setPadding(left, 0, right, Ui.dp(this, 24) + bottom);
    }

    private void switchPanel(int panel) {
        currentPanel = panel;
        homeList.setVisibility(panel == PANEL_HOME ? View.VISIBLE : View.GONE);
        panelLibrary.setVisibility(panel == PANEL_LIBRARY ? View.VISIBLE : View.GONE);
        panelSearch.setVisibility(panel == PANEL_SEARCH ? View.VISIBLE : View.GONE);
        panelFavorites.setVisibility(panel == PANEL_FAVORITE ? View.VISIBLE : View.GONE);
        panelSettings.setVisibility(panel == PANEL_SETTINGS ? View.VISIBLE : View.GONE);

        railSelected = panel == PANEL_HOME ? 1
                : panel == PANEL_LIBRARY ? 2
                : panel == PANEL_SEARCH ? 0
                : panel == PANEL_FAVORITE ? 3 : 4;
        updateRail();

        if (topScrim != null) {
            topScrim.setAlpha(panel == PANEL_HOME ? topScrimAlpha : 0f);
        }

        if (panel == PANEL_LIBRARY) {
            renderLibrary();
            if (libraries.isEmpty()) loadLibraries();
        } else if (panel == PANEL_FAVORITE) {
            focusFavoritesOnLoad = true;
            renderFavorites();
        } else if (panel == PANEL_SETTINGS) {
            renderSettings();
        } else if (panel == PANEL_SEARCH) {
            etSearch.requestFocus();
            etSearch.postDelayed(this::showKeyboard, 160);
        } else {
            homeList.requestFocus();
        }
    }

    private void showKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT);
    }

    // ==================== 数据 ====================

    private void loadAll() {
        loading = true;
        homeError = null;
        rebuildHome();
        loadLibraries();
        loadContinue();
        loadLive();
        loadUserInfo();
    }

    private void loadLibraries() {
        MediaRepository.get().libraries(new MediaRepository.DataCallback<List<MediaDbItem>>() {
            @Override
            public void onData(List<MediaDbItem> data) {
                libraries.clear();
                for (MediaDbItem lib : data) {
                    if (!lib.refreshDisabled) libraries.add(lib);
                }
                loading = false;
                rebuildHome();
                renderLibrary();
                loadPreviews();
            }

            @Override
            public void onError(String message) {
                loading = false;
                homeError = message;
                rebuildHome();
                renderLibrary();
            }
        });
    }

    private void loadPreviews() {
        for (final MediaDbItem lib : libraries) {
            MediaRepository.get().preview(lib.guid, 20,
                    new MediaRepository.DataCallback<List<PlayListItem>>() {
                        @Override
                        public void onData(List<PlayListItem> data) {
                            previews.put(lib.guid, data);
                            rebuildHome();
                        }

                        @Override
                        public void onError(String message) {
                            previews.put(lib.guid, new ArrayList<>());
                            rebuildHome();
                        }
                    });
        }
    }

    private void loadContinue() {
        MediaRepository.get().continueWatching(new MediaRepository.DataCallback<List<PlayListItem>>() {
            @Override
            public void onData(List<PlayListItem> data) {
                continueItems = data;
                rebuildHome();
            }

            @Override
            public void onError(String message) {
                continueItems = new ArrayList<>();
                rebuildHome();
            }
        });
    }

    private void loadLive() {
        MediaRepository.get().liveChannels(new MediaRepository.DataCallback<List<PlayListItem>>() {
            @Override
            public void onData(List<PlayListItem> data) {
                liveItems = data;
                rebuildHome();
            }

            @Override
            public void onError(String message) {
                liveItems = new ArrayList<>();
                rebuildHome();
            }
        });
    }

    private void loadUserInfo() {
        if (FnApiManager.getInstance().getApi() == null) return;
        FnApiManager.getInstance().getApi().getUserInfo()
                .enqueue(new Callback<ApiResponse<UserInfoResponse>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<UserInfoResponse>> call,
                                           Response<ApiResponse<UserInfoResponse>> response) {
                        if (response.isSuccessful() && response.body() != null
                                && response.body().code == 0 && response.body().data != null) {
                            displayName = response.body().data.getDisplayName();
                            if (currentPanel == PANEL_SETTINGS) renderSettings();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<UserInfoResponse>> call, Throwable t) {
                    }
                });
    }

    /** 大图推荐位需要横版剧照，按 guid 缓存 */
    private void ensureBackdrop(PlayListItem item) {
        if (item == null || item.guid == null) return;
        if (backdrops.containsKey(item.guid)) return;
        final String itemGuid = item.guid;
        backdrops.put(itemGuid, null);
        MediaRepository.get().detail(itemGuid, new MediaRepository.DataCallback<PlayInfoResponse>() {
            @Override
            public void onData(PlayInfoResponse data) {
                String path = data.getBackdropPath();
                if (path == null || path.isEmpty()) return;
                backdrops.put(itemGuid, path);
                rebuildHome();
            }

            @Override
            public void onError(String message) {
            }
        });
    }

    private void ensureHeroMeta(final PlayListItem item) {        if (item == null || item.guid == null) return;
        if (heroMetas.containsKey(item.guid) || heroMetaPending.contains(item.guid)) return;
        heroMetaPending.add(item.guid);
        final String key = item.guid;
        MediaRepository.get().itemMeta(item, new MediaRepository.DataCallback<ItemMeta>() {
            @Override
            public void onData(ItemMeta data) {
                heroMetaPending.remove(key);
                if (data == null) return;
                heroMetas.put(key, data);
                if (homeAdapter != null) {
                    homeAdapter.setHeroMeta(data);
                    homeAdapter.notifyItemChanged(0);
                }
            }

            @Override
            public void onError(String message) {
                heroMetaPending.remove(key);
            }
        });
    }

    /** 取当前季的第一集，用于「从第 X 集开始」 */
    private void ensureSeasonFirstEpisode(final PlayListItem billboard) {
        if (billboard == null || billboard.parentGuid == null
                || billboard.episodeNumber <= 0) return;
        final String seasonGuid = billboard.parentGuid;
        PlayListItem cached = seasonFirstEpisodes.get(seasonGuid);
        if (cached != null) {
            homeAdapter.setSeasonStartEpisode(seasonGuid, cached);
            return;
        }
        if (seasonFirstPending.contains(seasonGuid)) return;
        seasonFirstPending.add(seasonGuid);
        MediaRepository.get().episodes(seasonGuid,
                new MediaRepository.DataCallback<List<PlayListItem>>() {
            @Override
            public void onData(List<PlayListItem> data) {
                seasonFirstPending.remove(seasonGuid);
                if (data == null || data.isEmpty()) return;
                PlayListItem first = data.get(0);
                for (PlayListItem e : data) {
                    if (e.episodeNumber > 0
                            && (first.episodeNumber <= 0
                                || e.episodeNumber < first.episodeNumber)) {
                        first = e;
                    }
                }
                seasonFirstEpisodes.put(seasonGuid, first);
                if (homeAdapter != null) {
                    homeAdapter.setSeasonStartEpisode(seasonGuid, first);
                }
            }

            @Override
            public void onError(String message) {
                seasonFirstPending.remove(seasonGuid);
            }
        });
    }

    private void rebuildHome() {
        if (homeAdapter == null) return;        List<HomeItem> items = new ArrayList<>();

        PlayListItem billboard = null;
        String eyebrow = "为你推荐";
        if (!continueItems.isEmpty()) {
            billboard = continueItems.get(0);
            eyebrow = "继续观看";
        } else {
            for (MediaDbItem lib : libraries) {
                List<PlayListItem> preview = previews.get(lib.guid);
                if (preview != null && !preview.isEmpty()) {
                    billboard = preview.get(0);
                    eyebrow = lib.title;
                    break;
                }
            }
        }

        HomeItem hero = HomeItem.billboard(billboard, eyebrow);
        if (billboard != null) {
            hero.backdrop = backdrops.get(billboard.guid);
            ensureBackdrop(billboard);
            ItemMeta meta = heroMetas.get(billboard.guid);
            homeAdapter.setHeroMeta(meta);
            if (meta == null) ensureHeroMeta(billboard);
        } else {
            homeAdapter.setHeroMeta(null);
        }
        items.add(hero);

        if (continueItems.size() > 1) {
            items.add(HomeItem.rowTitle("继续观看", null, null));
            items.add(HomeItem.continueRow(
                    new ArrayList<>(continueItems.subList(1, continueItems.size()))));
        }

        for (MediaDbItem lib : libraries) {
            List<PlayListItem> preview = previews.get(lib.guid);
            if (preview != null && preview.isEmpty()) continue;
            items.add(HomeItem.rowTitle(lib.title, null, lib.guid));
            if (preview == null) {
                items.add(HomeItem.loading());
            } else {
                items.add(HomeItem.posters(preview));
            }
        }

        // 直播频道放最后
        if (!liveItems.isEmpty()) {
            items.add(HomeItem.rowTitle("直播频道", null, null));
            items.add(HomeItem.liveRow(liveItems));
        }

        if (loading && libraries.isEmpty()) {
            items.add(HomeItem.loading());
        } else if (!loading && libraries.isEmpty()) {
            items.add(HomeItem.tip(homeError != null
                    ? "连接失败 · " + homeError
                    : "还没有媒体库，请先在服务器上添加媒体"));
        }

        homeAdapter.submit(items);
        if (billboard != null) ensureSeasonFirstEpisode(billboard);
    }

    private void renderLibrary() {
        if (libraryContainer == null) return;
        LibraryScreen.render(this, libraryContainer, libraries, this);
    }

    private void renderSettings() {
        if (settingsContainer == null) return;
        SettingsScreen.render(this, settingsContainer, this);
    }

    // ==================== 搜索 ====================

    private void setupSearchGrid() {
        int avail = Ui.screenWidth(this) - Ui.railInset(this)
                - Ui.dimen(this, R.dimen.screen_pad);
        int gap = Ui.dp(this, 10);
        int minWidth = Ui.dp(this, 96);
        int span = Math.max(2, (avail + gap) / (minWidth + gap));
        span = Math.min(7, span);
        int cardWidth = avail / span - gap;

        searchGrid.setLayoutManager(new GridLayoutManager(this, span));
        searchGrid.addItemDecoration(new GridSpaceDecoration(gap));
        searchAdapter = new PosterAdapter(this, baseUrl, cardWidth, true, item -> openDetail(item));
        searchGrid.setAdapter(searchAdapter);
        searchGrid.setItemAnimator(null);
        searchGrid.setClipToPadding(false);
    }

    private void setupFavoritesGrid() {
        int avail = Ui.screenWidth(this) - Ui.railInset(this)
                - Ui.dimen(this, R.dimen.screen_pad);
        int gap = Ui.dp(this, 10);
        int minWidth = Ui.dp(this, 96);
        int span = Math.max(2, (avail + gap) / (minWidth + gap));
        span = Math.min(7, span);
        int cardWidth = avail / span - gap;

        if (favoritesGrid.getItemDecorationCount() > 0) favoritesGrid.removeItemDecorationAt(0);
        favoritesGrid.setLayoutManager(new GridLayoutManager(this, span));
        favoritesGrid.addItemDecoration(new GridSpaceDecoration(gap));
        favoritesAdapter = new PosterAdapter(this, baseUrl, cardWidth, true, item -> {
            if ("Directory".equals(item.type) || "Folder".equals(item.type)) {
                openFolder(item.guid, item.title);
            } else if ("Episode".equals(item.type) || "Video".equals(item.type)) {
                // 收藏的单集直接续播
                play(item, true);
            } else {
                openDetail(item);
            }
        });
        favoritesAdapter.submit(new ArrayList<>(favoriteItems));
        favoritesGrid.setAdapter(favoritesAdapter);
        favoritesGrid.setItemAnimator(null);
        favoritesGrid.setClipToPadding(false);
    }

    private void renderFavorites() {
        if (favoritesAdapter == null) return;
        favoritesEmpty.setText("加载中…");
        favoritesEmpty.setVisibility(View.VISIBLE);
        MediaRepository.get().favorites(new MediaRepository.DataCallback<List<PlayListItem>>() {
            @Override
            public void onData(List<PlayListItem> data) {
                favoriteItems.clear();
                if (data != null) favoriteItems.addAll(data);
                favoritesAdapter.submit(new ArrayList<>(favoriteItems));
                if (favoriteItems.isEmpty()) {
                    favoritesSubtitle.setText("已收藏的剧集与单集");
                    favoritesEmpty.setText("还没有收藏内容");
                    favoritesEmpty.setVisibility(View.VISIBLE);
                } else {
                    favoritesSubtitle.setText(favoriteItems.size() + " 项");
                    favoritesEmpty.setVisibility(View.GONE);
                    if (focusFavoritesOnLoad && currentPanel == PANEL_FAVORITE) {
                        focusFavoritesOnLoad = false;
                        favoritesGrid.post(() -> favoritesGrid.requestFocus());
                    }
                }
            }

            @Override
            public void onError(String message) {
                favoritesEmpty.setText(message);
                favoritesEmpty.setVisibility(View.VISIBLE);
            }
        });
    }

    private void setupSearch() {        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            boolean search = actionId == EditorInfo.IME_ACTION_SEARCH
                    || actionId == EditorInfo.IME_ACTION_DONE
                    || actionId == EditorInfo.IME_ACTION_GO;
            if (!search) return false;
            String query = v.getText().toString().trim();
            if (query.isEmpty()) return true;
            searchEmpty.setText("搜索中…");
            searchEmpty.setVisibility(View.VISIBLE);
            searchAdapter.submit(new ArrayList<>());
            MediaRepository.get().search(query,
                    new MediaRepository.DataCallback<List<PlayListItem>>() {
                        @Override
                        public void onData(List<PlayListItem> data) {
                            searchAdapter.submit(data);
                            if (data == null || data.isEmpty()) {
                                searchEmpty.setText("没有找到相关影片");
                                searchEmpty.setVisibility(View.VISIBLE);
                            } else {
                                searchEmpty.setVisibility(View.GONE);
                            }
                        }

                        @Override
                        public void onError(String message) {
                            searchEmpty.setText(message);
                            searchEmpty.setVisibility(View.VISIBLE);
                        }
                    });
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
            return true;
        });
    }

    // ==================== 交互 ====================

    @Override
    public void onBillboard(PlayListItem item) {
        play(item, true);
    }

    @Override
    public void onRestart(PlayListItem item) {
        if (item.parentGuid != null) {
            PlayListItem first = seasonFirstEpisodes.get(item.parentGuid);
            if (first != null) {
                play(first, false);
                return;
            }
        }
        play(item, false);
    }

    @Override
    public void onItem(PlayListItem item) {
        if ("LiveChannel".equals(item.type)) {
            play(item, false);
        } else if ("Directory".equals(item.type) || "Folder".equals(item.type)) {
            openFolder(item.guid, item.title);
        } else {
            openDetail(item);
        }
    }

    @Override
    public void onSection(HomeItem section) {
        if (section.guid == null) return;
        openBrowse(section.guid, section.title);
    }

    @Override
    public void onLibrary(MediaDbItem library) {
        openBrowse(library.guid, library.title);
    }

    private void openBrowse(String guid, String title) {
        Intent intent = new Intent(this, BrowseActivity.class);
        intent.putExtra("guid", guid);
        intent.putExtra("title", title);
        startActivity(intent);
    }

    private void openFolder(String guid, String title) {
        Intent intent = new Intent(this, BrowseActivity.class);
        intent.putExtra("guid", guid);
        intent.putExtra("title", title);
        intent.putExtra("folder", true);
        startActivity(intent);
    }

    private void openType(String type, String title) {
        Intent intent = new Intent(this, BrowseActivity.class);
        intent.putExtra("type", type);
        intent.putExtra("title", title);
        startActivity(intent);
    }

    private void openLive() {
        Intent intent = new Intent(this, BrowseActivity.class);
        intent.putExtra("live", true);
        intent.putExtra("title", "直播频道");
        startActivity(intent);
    }

    private void openDetail(PlayListItem item) {
        Intent intent = new Intent(this, DetailActivity.class);
        intent.putExtra("guid", item.guid);
        intent.putExtra("title", item.title);
        intent.putExtra("poster", item.poster);
        intent.putExtra("category", item.getCategoryLabel());
        startActivity(intent);
    }

    private void play(PlayListItem item, boolean resume) {
        Intent intent = new Intent(this, com.fntv.app.PlayerActivity.class);
        intent.putExtra("guid", item.guid);
        intent.putExtra("title", item.title);
        intent.putExtra("tv_title", item.tvTitle != null ? item.tvTitle : "");
        intent.putExtra("episode_number", item.episodeNumber);
        intent.putExtra("poster", item.poster);
        intent.putExtra("category", item.getCategoryLabel());
        intent.putExtra("ts", resume ? Math.max(0, item.ts) : 0);
        intent.putExtra("duration", item.duration);
        if (item.parentGuid != null) intent.putExtra("parent_guid", item.parentGuid);
        startActivity(intent);
    }

    // ==================== 设置 ====================

    @Override
    public void reRender() {
        renderSettings();
    }

    @Override
    public void onAppearance() {
        AppearanceDialog.show(this, this::reRender);
    }

    @Override
    public void onCheckUpdate(Button button) {
        updateManager = new UpdateManager(this, button, BuildConfig.VERSION_CODE);
        updateManager.setup();
    }

    @Override
    public void onFeedback() {
        com.fntv.app.ui.CardDialog.message(this, "问题反馈",
                "如有问题或建议，请加 QQ 群：\n693516430",
                "复制群号", () -> SettingsScreen.copyGroupNumber(this),
                "关闭", null);
    }

    @Override
    public void onLogout() {
        AuthRepository.logout();
        Prefs.get(this).edit().remove(Prefs.TOKEN).apply();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.putExtra("skip_auto_login", true);
        startActivity(intent);
        finish();
    }

    @Override
    public String displayName() {
        if (displayName != null && !displayName.isEmpty()) return displayName;
        String user = Prefs.str(this, Prefs.USER, "");
        return user.isEmpty() ? "未登录" : user;
    }

    private void goLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.putExtra("skip_auto_login", true);
        startActivity(intent);
        finish();
    }

    // ==================== 生命周期 ====================

    @Override
    protected void onResume() {
        super.onResume();
        if (homeAdapter != null && !loading) {
            loadContinue();
            if (currentPanel == PANEL_SETTINGS) renderSettings();
            else if (currentPanel == PANEL_FAVORITE) renderFavorites();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("panel", currentPanel);
    }

    @Override
    public void onBackPressed() {
        if (currentPanel != PANEL_HOME) {
            switchPanel(PANEL_HOME);
            return;
        }
        if (System.currentTimeMillis() - lastBackTime < 2000) {
            super.onBackPressed();
        } else {
            lastBackTime = System.currentTimeMillis();
            Toast.makeText(this, "再按一次退出", Toast.LENGTH_SHORT).show();
        }
    }
}
