package com.fntv.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.fntv.app.api.FnApiManager;
import com.fntv.app.api.model.*;
import com.fntv.app.util.SimpleImageLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeActivity extends AppCompatActivity {

    private Button tabMovies, tabLibrary, tabSettings;
    private View panelMovies, panelLibrary, panelSettings;
    private LinearLayout moviesContainer, libraryContainer;
    private TextView tvMoviesLoading, tvLibraryLoading, tvLibraryEmpty;
    private EditText etSearch;
    private boolean isSearching = false;
    private TextView tvSettingUsername, tvSettingServer, tvDecoderValue, tvDanmuUrl;
    private Button btnLogout, btnFeedback;
    private UpdateManager updateManager;
    private RelativeLayout rlDecoderSetting, rlDanmuSetting, rlSeekStep;
    private TextView tvSeekStepValue;

    private int currentTab = 0;
    private final List<MediaDbItem> mediaLibraries = new ArrayList<>();
    private boolean showingOverview = true;
    private boolean showingEpisodes = false;
    private boolean loadingPreviews = false;

    // 媒体库浏览排序状态
    private String currentBrowseGuid;
    private String currentBrowseTitle;
    private LinearLayout currentBrowseContainer;
    private TextView currentBrowseLoading;
    private int libSortColumnIndex = 0; // 0=添加日期, 1=发行日期
    private int libSortOrderIndex = 1;  // 0=升序, 1=降序

    private FnApiManager apiManager;
    private String baseUrl = "";
    private SharedPreferences prefs;
    private static final String PREF_DECODER = "decoder_mode";

    private long t0;
    private boolean overviewBuilt = false;
    private long backPressedTime = 0;

    // 横竖屏切换时保存的页面状态
    private String savedBrowseGuid, savedBrowseTitle;
    private List<PlayListItem> savedBrowseList;
    private PlayListItem savedDetailItem;
    private PlayInfoResponse savedDetailInfo;
    private boolean browseFromLibrary;
    private String savedLiveChannelTitle;

    @Override

    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (savedDetailItem != null) {
            buildDetailPage(savedDetailItem, savedDetailInfo);
        } else if (savedBrowseList != null) {
            if (browseFromLibrary) { renderBrowseGrid(savedBrowseList, savedBrowseTitle); } else { renderGridInContainer(savedBrowseList, savedBrowseTitle, moviesContainer); }
        } else if (currentTab == 1 && savedBrowseGuid != null) {
            browseItemsInContainer(savedBrowseGuid, savedBrowseTitle,
                    libraryContainer, tvLibraryLoading);
        } else if (currentTab == 0) {
            loadingPreviews = false;
            overviewBuilt = false;
            showOverview();
            loadAllPreviews();
            loadLiveChannels();
        }
    }

    @Override

    protected void onCreate(Bundle savedInstanceState) {
        t0 = System.currentTimeMillis();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(0xFF1A1A1A);
        }

        prefs = getSharedPreferences("fntv_prefs", MODE_PRIVATE);
        apiManager = FnApiManager.getInstance();
        baseUrl = prefs.getString("host", "").replaceAll("/+$", "");
        if (apiManager.getApi() == null && !baseUrl.isEmpty()) {
            apiManager.updateBaseUrl(baseUrl);
        }
        // 切换服务器时清空观看记录
        String lastHost = prefs.getString("last_host", "");
        if (!lastHost.equals(baseUrl) && !lastHost.isEmpty()) {
            prefs.edit().remove("watch_history").putString("last_host", baseUrl).apply();
        } else if (lastHost.isEmpty() && !baseUrl.isEmpty()) {
            prefs.edit().putString("last_host", baseUrl).apply();
        }
        initViews();
        setupTabs();
        setupSettings();
        setupLogout();
        updateManager.setup();
        setupFeedback();
        setupSearch();

        switchTab(0);
        tvMoviesLoading.setVisibility(View.VISIBLE);
        tvMoviesLoading.setText("正在加载媒体库...");
        loadOverview();
    }


    private void initViews() {
        tabMovies = findViewById(R.id.tabMovies);
        tabLibrary = findViewById(R.id.tabLibrary);
        tabSettings = findViewById(R.id.tabSettings);
        panelMovies = findViewById(R.id.panelMovies);
        panelLibrary = findViewById(R.id.panelLibrary);
        panelSettings = findViewById(R.id.panelSettings);
        moviesContainer = findViewById(R.id.moviesGridContainer);
        libraryContainer = findViewById(R.id.libraryGridContainer);
        etSearch = findViewById(R.id.etSearch);
        tvMoviesLoading = findViewById(R.id.tvMoviesLoading);
        tvLibraryLoading = findViewById(R.id.tvLibraryLoading);
        tvLibraryEmpty = findViewById(R.id.tvLibraryEmpty);
        tvSettingUsername = findViewById(R.id.tvSettingUsername);
        tvSettingServer = findViewById(R.id.tvSettingServer);
        tvDecoderValue = findViewById(R.id.tvDecoderValue);
        btnLogout = findViewById(R.id.btnLogout);
        Button btnCheckUpdate = findViewById(R.id.btnCheckUpdate);
        updateManager = new UpdateManager(this, btnCheckUpdate, BuildConfig.VERSION_CODE);
        btnFeedback = findViewById(R.id.btnFeedback);
        rlDecoderSetting = findViewById(R.id.rlDecoderSetting);
        rlDanmuSetting = findViewById(R.id.rlDanmuSetting);
        rlSeekStep = findViewById(R.id.rlSeekStep);
        tvSeekStepValue = findViewById(R.id.tvSeekStepValue);
        tvDanmuUrl = findViewById(R.id.tvDanmuUrl);
        tvSettingServer.setText(prefs.getString("host", ""));

        TextView tvVersion = findViewById(R.id.tvVersionName);
        try {
            tvVersion.setText("FN TV v" + getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
        } catch (Exception ignored) {}
    }


    // ======================== Tab ========================

    @Override

    protected void onResume() {
        super.onResume();
        if (savedDetailItem != null) {
            buildDetailPage(savedDetailItem, savedDetailInfo);
        } else if (currentTab == 0 && !mediaLibraries.isEmpty() && overviewBuilt && showingOverview) {
            loadingPreviews = false;
            showOverview();
            loadAllPreviews();
            loadContinueWatching();
            loadLiveChannels();
        } else if (currentTab == 0 && !overviewBuilt && showingOverview) {
            loadOverview();
        }
    }


    private void setupTabs() {
        tabMovies.setOnClickListener(v -> switchTab(0));
        tabLibrary.setOnClickListener(v -> { switchTab(1); loadMediaLibraries(); });
        tabSettings.setOnClickListener(v -> switchTab(2));
    }


    private void switchTab(int index) {
        int prevTab = currentTab;
        currentTab = index;
        // 从媒体库切换到其他标签时清除搜索状态
        if (prevTab == 1 && isSearching) clearSearch();
        // 切换标签时清除保存的页面状态，防止横竖屏切回时错误恢复
        savedBrowseList = null; savedBrowseGuid = null;
        panelMovies.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        panelLibrary.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        panelSettings.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        tabMovies.setSelected(index == 0);
        tabLibrary.setSelected(index == 1);
        tabSettings.setSelected(index == 2);
        if (index == 0) tabMovies.requestFocus();
        else if (index == 1) tabLibrary.requestFocus();
        else tabSettings.requestFocus();
    }


    // ==================== 影视概览 ====================


    private void loadOverview() {
        Log.d("Overview", "loadOverview start  t=" + (System.currentTimeMillis() - t0) + "ms");
        showingOverview = true;
        overviewBuilt = false;
        loadingPreviews = false;
        tvMoviesLoading.setVisibility(View.VISIBLE);

        final int[] retryCount = {1};
        apiManager.getApi().getMediaDbList().enqueue(new Callback<ApiResponse<List<MediaDbItem>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<MediaDbItem>>> call,
                                   Response<ApiResponse<List<MediaDbItem>>> response) {
                String bodyStr = response.body() != null ? "code=" + response.body().code + " msg=" + response.body().msg + " data=" + (response.body().data != null ? response.body().data.size() + "条" : "null") : "nullBody";
                Log.d("Overview", "getMediaDbList resp code=" + response.code() + " " + bodyStr + " t=" + (System.currentTimeMillis() - t0) + "ms");
                if (response.body() != null && response.body().code != 0) {
                    try { Log.w("Overview", "错误响应: " + new com.google.gson.Gson().toJson(response.body())); } catch (Exception ignored) {}
                }
                // Auth Failed 时重试一次
                if (response.body() != null && response.body().code == -2 && retryCount[0] > 0) {
                    retryCount[0]--;
                    Log.d("Overview", "Auth Failed，重试中...");
                    call.clone().enqueue(this);
                    return;
                }
                if (response.isSuccessful() && response.body() != null && response.body().code == 0
                        && response.body().data != null && !response.body().data.isEmpty()) {
                    mediaLibraries.clear();
                    for (MediaDbItem lib : response.body().data) {
                        if (!lib.refreshDisabled) mediaLibraries.add(lib);
                    }
                    Log.d("Overview", "loaded " + mediaLibraries.size() + " libraries");
                    showOverview();
                    loadAllPreviews();
                    loadContinueWatching();
                    loadLiveChannels();
                } else {
                    tvMoviesLoading.setVisibility(View.GONE);
                }
            }
            @Override
            public void onFailure(Call<ApiResponse<List<MediaDbItem>>> call, Throwable t) {
                tvMoviesLoading.setVisibility(View.GONE);
                Log.e("Overview", "getMediaDbList onFailure: " + t.getMessage() + " t=" + (System.currentTimeMillis() - t0) + "ms");
            }
        });
    }

    /** 构建概览 */

    private void showOverview() {
        tvMoviesLoading.setVisibility(View.GONE);
        savedDetailItem = null; savedDetailInfo = null; savedBrowseList = null; savedBrowseGuid = null;
        savedLiveChannelTitle = null;
        showingEpisodes = false;
        moviesContainer.removeAllViews();
        showingOverview = true;
        overviewBuilt = true;

        // 继续观看（最顶部，占位，稍后由 loadContinueWatching 填充）
        LinearLayout continueWatchingBox = new LinearLayout(this);
        continueWatchingBox.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        continueWatchingBox.setOrientation(LinearLayout.VERTICAL);
        continueWatchingBox.setTag("continue_watching");
        moviesContainer.addView(continueWatchingBox);

        // 各媒体库：标题 + 预览容器
        java.util.ArrayList<Integer> viewAllIds = new java.util.ArrayList<>();
        for (MediaDbItem lib : mediaLibraries) {
            LinearLayout headerRow = makeLibHeader(lib.guid, lib.title, 0);
            moviesContainer.addView(headerRow);
            for (int ci = 0; ci < headerRow.getChildCount(); ci++) {
                View child = headerRow.getChildAt(ci);
                if (child instanceof Button && child.isFocusable()) {
                    viewAllIds.add(child.getId());
                }
            }
            // 预览卡片容器（暂空，loadAllPreviews 后填充）
            LinearLayout previewBox = new LinearLayout(this);
            previewBox.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            previewBox.setOrientation(LinearLayout.VERTICAL);
            previewBox.setPadding(6, 0, 6, 0);
            previewBox.setTag("preview_" + lib.guid);
            moviesContainer.addView(previewBox);
            moviesContainer.addView(makeSpacer(16));
        }

        // 继续观看卡片 ↓ 第一个查看全部（在 loadContinueWatching 中设置焦点）

        if (moviesContainer.getChildCount() == 0) {
            TextView e = new TextView(this);
            e.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 120));
            e.setGravity(Gravity.CENTER);
            e.setText("暂无影视内容");
            e.setTextColor(0xFF808080);
            e.setTextSize(14);
            moviesContainer.addView(e);
        }
    }

    /** 加载各媒体库预览 */

    private void loadAllPreviews() {
        if (loadingPreviews) return;
        loadingPreviews = true;
        for (final MediaDbItem lib : mediaLibraries) {
            final String guid = lib.guid;
            apiManager.getApi().getItemList(ItemListRequest.browseLibrary(guid))
                .enqueue(new Callback<ApiResponse<ItemListResponse>>() {
                @Override
                public void onResponse(Call<ApiResponse<ItemListResponse>> call,
                                       Response<ApiResponse<ItemListResponse>> response) {
                    if (!response.isSuccessful() || response.body() == null || response.body().code != 0
                            || response.body().data == null || response.body().data.list == null
                            || response.body().data.list.isEmpty()) return;
                    // 取前6个填到预览容器
                    List<PlayListItem> items = response.body().data.list;
                    if (items.size() > 20) items = items.subList(0, 20);
                    fillPreview(guid, items);
                }
                @Override public void onFailure(Call<ApiResponse<ItemListResponse>> call, Throwable t) {}
            });
        }
    }

    /** 填充预览卡片（先清空再填充，防止重复） */

    private void fillPreview(String libGuid, List<PlayListItem> items) {
        for (int i = 0; i < moviesContainer.getChildCount(); i++) {
            View v = moviesContainer.getChildAt(i);
            if (v instanceof LinearLayout && ("preview_" + libGuid).equals(v.getTag())) {
                LinearLayout box = (LinearLayout) v;
                box.removeAllViews();
                populateGrid(box, items);
                break;
            }
        }
    }

    /** 构建媒体库标题行（整行可聚焦，点击 = 查看全部） */

    private LinearLayout makeLibHeader(String libGuid, String libTitle, int count) {
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setPadding(6, 24, 6, 18);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setMinimumHeight(68);
        headerRow.setId(View.generateViewId());

        TextView header = new TextView(this);
        header.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        header.setText(libTitle);
        header.setTextColor(0xFFEEEEEE);
        header.setTextSize(18);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        headerRow.addView(header);

        Button viewAll = new Button(this);
        viewAll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, 68));
        viewAll.setBackgroundResource(R.drawable.bg_input);
        viewAll.setText("查看全部 ›");
        viewAll.setTextColor(0xFFB0B0B0);
        viewAll.setTextSize(14);
        viewAll.setFocusable(true);
        viewAll.setId(View.generateViewId());
        viewAll.setPadding(24, 0, 24, 0);
        viewAll.setOnClickListener(v -> browseItems(libGuid, libTitle));
        viewAll.setOnFocusChangeListener((v, hasFocus) -> {
            viewAll.setTextColor(hasFocus ? 0xFF81C784 : 0xFFB0B0B0);
            viewAll.setBackgroundColor(hasFocus ? 0x44FFFFFF : 0x00000000);
        });
        headerRow.setOnClickListener(v -> browseItems(libGuid, libTitle));
        headerRow.setFocusable(true);
        headerRow.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) viewAll.requestFocus();
        });
        headerRow.addView(viewAll);
        return headerRow;
    }


    private String makePosterUrl(String path) {
        if (path == null || path.isEmpty()) {
            Log.d("PosterUrl", "path is null/empty");
            return null;
        }
        String p = path.startsWith("/") ? path : "/" + path;
        String fullUrl = baseUrl + "/v/api/v1/sys/img" + p + "?w=400";
        Log.d("PosterUrl", "poster=" + path + " -> " + fullUrl);
        return fullUrl;
    }


    // ==================== 继续观看 ====================


    private void addContinueWatchingApi(LinearLayout cont, List<PlayListItem> items, int viewAllId) {
        if (items == null || items.isEmpty()) {
            cont.setVisibility(View.GONE);
            return;
        }
        cont.removeAllViews();
        cont.setVisibility(View.VISIBLE);

        TextView h = new TextView(this);
        h.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        h.setPadding(6, 8, 6, 4);
        h.setText("▶ 继续观看");
        h.setTextColor(0xFF81C784);
        h.setTextSize(15);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        cont.addView(h);

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 410));
        hsv.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(10, 8, 10, 8);

        for (PlayListItem item : items) {
            View card = makeContinueCard(item);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    220, 380);
            lp.setMargins(10, 0, 10, 0);
            card.setLayoutParams(lp);
            if (viewAllId > 0) card.setNextFocusDownId(viewAllId);
            row.addView(card);
        }

        hsv.addView(row);
        cont.addView(hsv);

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                loadImagesLazily(hsv, 0);
            }
        });

        cont.addView(makeSpacer(6));
    }

    private String formatContinueTitle(PlayListItem item) {
        if (item.tvTitle != null && !item.tvTitle.isEmpty()) {
            StringBuilder sb = new StringBuilder(item.tvTitle);
            if (item.seasonNumber > 0) sb.append(" 第").append(item.seasonNumber).append("季");
            if (item.episodeNumber > 0) sb.append(item.episodeNumber).append("集");
            return sb.toString();
        }
        return item.title != null ? item.title : "";
    }

    private View makeContinueCard(PlayListItem item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_media_card);
        card.setPadding(6, 6, 6, 6);
        card.setFocusable(true);

        RoundedImageView poster = new RoundedImageView(this);
        poster.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 280));
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setCornerRadius(10);
        poster.setBackgroundColor(0xFF333333);
        String imgUrl = makePosterUrl(item.poster);
        if (imgUrl != null) { poster.setTag(imgUrl); }
        card.addView(poster);

        // 进度条
        LinearLayout pBar = new LinearLayout(this);
        pBar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 5));
        pBar.setOrientation(LinearLayout.HORIZONTAL);
        pBar.setWeightSum(100);
        int pct = item.duration > 0 ? Math.max(0, Math.min(100, (int)(item.ts * 100 / item.duration))) : 0;
        Log.d("Overview", "进度条: ts=" + item.ts + " dur=" + item.duration + " pct=" + pct + " " + formatContinueTitle(item));
        if (pct > 0) {
            View f = new View(HomeActivity.this);
            f.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, pct));
            f.setBackgroundColor(0xFF81C784);
            pBar.addView(f);
        }
        if (pct < 100) {
            View r = new View(HomeActivity.this);
            r.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 100 - pct));
            r.setBackgroundColor(0xFF555555);
            pBar.addView(r);
        }
        card.addView(pBar);

        TextView title = new TextView(this);
        title.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 85));
        title.setSingleLine(true);
        title.setHorizontallyScrolling(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
        title.setMarqueeRepeatLimit(-1);
        title.setTextSize(13);
        title.setTextColor(0xFFEEEEEE);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(4, 0, 4, 0);
        title.setText(formatContinueTitle(item));
        title.setFocusable(false);
        card.addView(title);

        // 跑马灯：强制选中，始终滚动
        title.post(() -> title.setSelected(true));

        // 点击直接播放
        final PlayListItem fi = item;
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchPlayer(fi.guid, fi.title, fi.tvTitle != null ? fi.tvTitle : "",
                        fi.episodeNumber, fi.poster, fi.getCategoryLabel(),
                        fi.ts, fi.duration, fi.parentGuid);
            }
        });

        return card;
    }

    private void loadContinueWatching() {
        apiManager.getApi().getPlayList().enqueue(new Callback<ApiResponse<List<PlayListItem>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<PlayListItem>>> call,
                                   Response<ApiResponse<List<PlayListItem>>> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().code != 0
                        || response.body().data == null || response.body().data.isEmpty()) return;
                // 找到继续观看占位容器
                for (int i = 0; i < moviesContainer.getChildCount(); i++) {
                    View v = moviesContainer.getChildAt(i);
                    if (v instanceof LinearLayout && "continue_watching".equals(v.getTag())) {
                        final int firstViewAllId;
                        // 查找第一个查看全部按钮
                        int fva = -1;
                        for (int j = 0; j < moviesContainer.getChildCount(); j++) {
                            View cv = moviesContainer.getChildAt(j);
                            if (cv instanceof ViewGroup) {
                                for (int ci = 0; ci < ((ViewGroup) cv).getChildCount(); ci++) {
                                    View child = ((ViewGroup) cv).getChildAt(ci);
                                    if (child instanceof Button && child.isFocusable()) {
                                        fva = child.getId();
                                        break;
                                    }
                                }
                                if (fva > 0) break;
                            }
                        }
                        addContinueWatchingApi((LinearLayout) v, response.body().data, fva);
                        break;
                    }
                }
            }
            @Override
            public void onFailure(Call<ApiResponse<List<PlayListItem>>> call, Throwable t) {}
        });
    }


    private void setFocusDownInContainer(ViewGroup group, int targetId) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View v = group.getChildAt(i);
            if (v.isFocusable()) {
                v.setNextFocusDownId(targetId);
            }
            if (v instanceof ViewGroup) {
                setFocusDownInContainer((ViewGroup) v, targetId);
            }
        }
    }


    // ==================== 横向滚动卡片 ====================


    private void populateGrid(LinearLayout cont, List<PlayListItem> items) {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 410));
        hsv.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(12, 8, 12, 8);

        // 找到 cont 前后的 headerRow，取 viewAll 按钮
        View focusUpTarget = null;
        View focusDownTarget = null;
        if (cont.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) cont.getParent();
            int idx = parent.indexOfChild(cont);
            // 前面的 headerRow（当前分类的查看全部）
            for (int si = idx - 1; si >= 0; si--) {
                View v = parent.getChildAt(si);
                if (v instanceof ViewGroup) {
                    for (int ci = 0; ci < ((ViewGroup) v).getChildCount(); ci++) {
                        View child = ((ViewGroup) v).getChildAt(ci);
                        if (child instanceof Button && child.isFocusable()) {
                            focusUpTarget = child;
                            break;
                        }
                    }
                    if (focusUpTarget != null) break;
                }
            }
            // 后面的 headerRow（下一个分类的查看全部）
            for (int si = idx + 1; si < parent.getChildCount(); si++) {
                View v = parent.getChildAt(si);
                if (v instanceof ViewGroup) {
                    for (int ci = 0; ci < ((ViewGroup) v).getChildCount(); ci++) {
                        View child = ((ViewGroup) v).getChildAt(ci);
                        if (child instanceof Button && child.isFocusable()) {
                            focusDownTarget = child;
                            break;
                        }
                    }
                    if (focusDownTarget != null) break;
                }
            }
        }

        for (int i = 0; i < items.size(); i++) {
            View card = makeItemCard(items.get(i));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(220, 380);
            lp.setMargins(10, 0, 10, 0);
            card.setLayoutParams(lp);
            // 卡片：↑到当前查看全部，↓到下一个查看全部
            if (focusUpTarget != null) card.setNextFocusUpId(focusUpTarget.getId());
            if (focusDownTarget != null) card.setNextFocusDownId(focusDownTarget.getId());
            row.addView(card);
        }

        hsv.addView(row);
        cont.addView(hsv);

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                loadImagesLazily(hsv, 0);
            }
        });
    }

    /** 竖版卡片（2:3 比例适配海报图，图片为主，文字一条） */

    private View makeItemCard(PlayListItem item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_media_card);
        card.setPadding(6, 6, 6, 6);
        card.setFocusable(true);

        // 海报 — 16:9 比例，等页面显示完后统一逐张加载
        RoundedImageView iv = new RoundedImageView(this);
        iv.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 280));
        iv.setScaleType(ImageView.ScaleType.FIT_XY);
        iv.setBackgroundColor(0xFF333333);
        iv.setCornerRadius(10);
        String imgUrl = makePosterUrl(item.poster);
        if (imgUrl != null) { iv.setTag(imgUrl); }
        card.addView(iv);

        // 底部文字条：类型 + 标题
        LinearLayout textBar = new LinearLayout(this);
        textBar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 88));
        textBar.setOrientation(LinearLayout.VERTICAL);
        textBar.setGravity(Gravity.CENTER_VERTICAL);
        textBar.setPadding(0, 4, 0, 4);

        TextView tag = new TextView(this);
        tag.setTextSize(9);
        tag.setTextColor(0xFF78909C);
        String t = item.type;
        if ("TV".equals(t)) t = "剧集";
        else if ("Movie".equals(t)) t = "电影";
        else if ("Directory".equals(t)) t = "文件夹";
        else if ("Video".equals(t)) t = "视频";
        tag.setText(t);
        textBar.addView(tag);

        final TextView title = new TextView(this);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        title.setMarqueeRepeatLimit(-1);
        title.setTextSize(11);
        title.setTextColor(0xFFEEEEEE);
        title.setText(item.title != null ? item.title : "未知");
        textBar.addView(title);

        card.addView(textBar);

        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                title.setSelected(hasFocus);
            }
        });

        card.setTag(item);
        card.setOnClickListener(v -> onItemClick((PlayListItem) card.getTag()));
        return card;
    }


    private void onItemClick(PlayListItem item) {
        showDetail(item);  // 全部走 getPlayInfo
    }


    // ==================== 查看全部 ====================


    private void browseItems(String ancestorGuid, String title) {
        browseItemsInContainer(ancestorGuid, title, moviesContainer, tvMoviesLoading);
    }

    private void browseItemsInContainer(String ancestorGuid, String title,
                                        LinearLayout container, TextView loadingView) {
        Log.d("Overview", "browseItems: guid=" + ancestorGuid + " title=" + title);
        savedDetailItem = null; savedDetailInfo = null;
        showingEpisodes = false;
        savedBrowseGuid = ancestorGuid; savedBrowseTitle = title;
        browseFromLibrary = (container == libraryContainer);
        if (browseFromLibrary) etSearch.setVisibility(View.GONE);
        if (container == moviesContainer) showingOverview = false;

        // 存储当前浏览上下文，排序变化时用于重新加载
        currentBrowseGuid = ancestorGuid;
        currentBrowseTitle = title;
        currentBrowseContainer = container;
        currentBrowseLoading = loadingView;

        container.removeAllViews();
        loadingView.setVisibility(View.VISIBLE);

        String sortColumn = libSortColumnIndex == 0 ? "create_time" : "release_date";
        String sortType = libSortOrderIndex == 0 ? "ASC" : "DESC";
        ItemListRequest request = new ItemListRequest(ancestorGuid,
                Arrays.asList("Movie", "TV", "Directory", "Video"),
                true, sortColumn, sortType, 50);
        apiManager.getApi().getItemList(request)
                .enqueue(new Callback<ApiResponse<ItemListResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<ItemListResponse>> call,
                                   Response<ApiResponse<ItemListResponse>> response) {
                loadingView.setVisibility(View.GONE);
                Log.d("Overview", "browseItems response code=" + response.code());

                if (response.isSuccessful() && response.body() != null && response.body().code == 0
                        && response.body().data != null && response.body().data.list != null
                        && !response.body().data.list.isEmpty()) {
                    List<PlayListItem> list = response.body().data.list;
                    savedBrowseList = list;
                    int total = response.body().data.total;
                    Log.d("Overview", "browseItems: got " + list.size() + " items, total=" + total);

                    TextView h = new TextView(HomeActivity.this);
                    h.setLayoutParams(new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    h.setPadding(6, 8, 6, 4);
                    h.setText(title + "  (" + total + "项)");
                    h.setTextColor(0xFFEEEEEE);
                    h.setTextSize(14);
                    container.addView(h);

                    // 排序筛选栏
                    LinearLayout sortFilterBar = makeLibSortFilterBar();
                    sortFilterBar.setTag("lib_sort_bar");
                    container.addView(sortFilterBar);
                    container.addView(makeSpacer(6));

                    // 自适应列数网格（最小卡片宽200dp）
                    float density = getResources().getDisplayMetrics().density;
                    int cols = Math.max(3, (int) (getResources().getDisplayMetrics().widthPixels / (130 * density)));
                    for (int idx = 0; idx < list.size(); idx += cols) {
                        LinearLayout row = new LinearLayout(HomeActivity.this);
                        row.setLayoutParams(new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        int inRow = Math.min(cols, list.size() - idx);
                        for (int c = 0; c < cols && idx + c < list.size(); c++) {
                            PlayListItem pli = list.get(idx + c);
                            View card = makeItemCard(pli);
                            // 图片按9:16竖版比例
                            if (card instanceof ViewGroup) {
                                View ch = ((ViewGroup) card).getChildAt(0);
                                if (ch instanceof ImageView) {
                                    int posterH = Math.min(550, (getResources().getDisplayMetrics().widthPixels / cols) * 3 / 2);
                                    ch.setLayoutParams(new LinearLayout.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT, posterH));
                                }
                            }
                            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                            lp.rightMargin = 6;
                            lp.leftMargin = 6;
                            card.setLayoutParams(lp);
                            row.addView(card);
                        }
                        // 补齐空位
                        for (int e = inRow; e < cols; e++) {
                            View spacer = new View(HomeActivity.this);
                            spacer.setLayoutParams(new LinearLayout.LayoutParams(
                                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
                            row.addView(spacer);
                        }
                        container.addView(row);
                        container.addView(makeSpacer(12));
                    }
                    new Handler(Looper.getMainLooper()).post(() -> loadImagesLazily(container, 0));
                } else {
                    TextView e = new TextView(HomeActivity.this);
                    e.setLayoutParams(new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, 120));
                    e.setGravity(Gravity.CENTER);
                    e.setText("暂无内容");
                    e.setTextColor(0xFF808080);
                    e.setTextSize(14);
                    container.addView(e);
                }
            }
            @Override
            public void onFailure(Call<ApiResponse<ItemListResponse>> call, Throwable t) {
                loadingView.setVisibility(View.GONE);
            }
        });
    }

    /** 从缓存数据重绘浏览网格（横竖屏切换时调用） */

    private void renderBrowseGrid(List<PlayListItem> list, String title) {
        renderGridInContainer(list, title, libraryContainer);
    }

    /** 在指定容器中绘制缓存网格 */

    private void renderGridInContainer(List<PlayListItem> list, String title, LinearLayout container) {
        container.removeAllViews();
        int total = list.size();

        TextView h = new TextView(this);
        h.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        h.setPadding(6, 8, 6, 4);
        h.setText(title + "  (" + total + "项)");
        h.setTextColor(0xFFEEEEEE);
        h.setTextSize(14);
        container.addView(h);

        float density = getResources().getDisplayMetrics().density;
        int cols = Math.max(3, (int) (getResources().getDisplayMetrics().widthPixels / (130 * density)));
        for (int idx = 0; idx < list.size(); idx += cols) {
            LinearLayout row = new LinearLayout(this);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            row.setOrientation(LinearLayout.HORIZONTAL);
            int inRow = Math.min(cols, list.size() - idx);
            for (int c = 0; c < cols && idx + c < list.size(); c++) {
                PlayListItem pli = list.get(idx + c);
                View card = makeItemCard(pli);
                if (card instanceof ViewGroup) {
                    View ch = ((ViewGroup) card).getChildAt(0);
                    if (ch instanceof ImageView) {
                        int posterH = Math.min(550, (getResources().getDisplayMetrics().widthPixels / cols) * 3 / 2);
                        ch.setLayoutParams(new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, posterH));
                    }
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                lp.rightMargin = 6; lp.leftMargin = 6;
                card.setLayoutParams(lp);
                row.addView(card);
            }
            for (int e = inRow; e < cols; e++) {
                View spacer = new View(this);
                spacer.setLayoutParams(new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
                row.addView(spacer);
            }
            container.addView(row);
            container.addView(makeSpacer(12));
        }
        new Handler(Looper.getMainLooper()).post(() -> loadImagesLazily(container, 0));
    }

    // ==================== 媒体库排序筛选 ====================

    /** 使用当前排序重新加载媒体库内容 */
    private void reFetchLibraryItems() {
        if (currentBrowseGuid == null || currentBrowseContainer == null) return;
        browseItemsInContainer(currentBrowseGuid, currentBrowseTitle,
                currentBrowseContainer, currentBrowseLoading);
    }

    /** 构建排序筛选栏 */
    private LinearLayout makeLibSortFilterBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundResource(R.drawable.bg_input);
        bar.setPadding(16, 18, 16, 18);

        // 排序方式标签
        TextView sortLabel = new TextView(this);
        sortLabel.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        sortLabel.setGravity(Gravity.CENTER_VERTICAL);
        sortLabel.setText("排序方式: ");
        sortLabel.setTextColor(0xFFB0B0B0);
        sortLabel.setTextSize(13);
        bar.addView(sortLabel);

        // 排序列选择按钮
        Button sortColumnBtn = new Button(this);
        sortColumnBtn.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, 78));
        sortColumnBtn.setBackgroundResource(R.drawable.bg_btn_primary);
        String[] colLabels = {"添加日期", "发行日期"};
        sortColumnBtn.setText(colLabels[libSortColumnIndex] + " ▾");
        sortColumnBtn.setTextColor(0xFFEEEEEE);
        sortColumnBtn.setTextSize(12);
        sortColumnBtn.setFocusable(true);
        sortColumnBtn.setPadding(14, 0, 14, 0);
        sortColumnBtn.setOnFocusChangeListener((v, hasFocus) -> {
            v.setScaleX(hasFocus ? 1.08f : 1.0f);
            v.setScaleY(hasFocus ? 1.08f : 1.0f);
        });
        final Button colBtnRef = sortColumnBtn;
        sortColumnBtn.setOnClickListener(v ->
                new android.app.AlertDialog.Builder(this)
                        .setTitle("排序方式")
                        .setSingleChoiceItems(colLabels, libSortColumnIndex,
                                (dialog, which) -> {
                                    libSortColumnIndex = which;
                                    colBtnRef.setText(colLabels[which] + " ▾");
                                    dialog.dismiss();
                                    reFetchLibraryItems();
                                })
                        .setNegativeButton("取消", null)
                        .show()
        );
        bar.addView(sortColumnBtn);

        // 弹性间隔
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        bar.addView(spacer);

        // 顺序标签
        TextView orderLabel = new TextView(this);
        orderLabel.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        orderLabel.setGravity(Gravity.CENTER_VERTICAL);
        orderLabel.setText("顺序: ");
        orderLabel.setTextColor(0xFFB0B0B0);
        orderLabel.setTextSize(13);
        bar.addView(orderLabel);

        // 排序顺序选择按钮
        Button sortOrderBtn = new Button(this);
        sortOrderBtn.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, 78));
        sortOrderBtn.setBackgroundResource(R.drawable.bg_btn_primary);
        String[] orderLabels = {"升序", "降序"};
        sortOrderBtn.setText(orderLabels[libSortOrderIndex] + " ▾");
        sortOrderBtn.setTextColor(0xFFEEEEEE);
        sortOrderBtn.setTextSize(12);
        sortOrderBtn.setFocusable(true);
        sortOrderBtn.setPadding(14, 0, 14, 0);
        sortOrderBtn.setOnFocusChangeListener((v, hasFocus) -> {
            v.setScaleX(hasFocus ? 1.08f : 1.0f);
            v.setScaleY(hasFocus ? 1.08f : 1.0f);
        });
        final Button orderBtnRef = sortOrderBtn;
        sortOrderBtn.setOnClickListener(v ->
                new android.app.AlertDialog.Builder(this)
                        .setTitle("排序顺序")
                        .setSingleChoiceItems(orderLabels, libSortOrderIndex,
                                (dialog, which) -> {
                                    libSortOrderIndex = which;
                                    orderBtnRef.setText(orderLabels[which] + " ▾");
                                    dialog.dismiss();
                                    reFetchLibraryItems();
                                })
                        .setNegativeButton("取消", null)
                        .show()
        );
        bar.addView(sortOrderBtn);

        return bar;
    }

    /** 启动播放器 */
    private void launchPlayer(String guid, String title, String tvTitle, int epNum,
                              String poster, String cat, long ts, long dur) {
        launchPlayer(guid, title, tvTitle, epNum, poster, cat, ts, dur, null);
    }

    private void launchPlayer(String guid, String title, String tvTitle, int epNum,
                              String poster, String cat, long ts, long dur, String parentGuid) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("guid", guid);
        intent.putExtra("title", title);
        intent.putExtra("tv_title", tvTitle);
        intent.putExtra("episode_number", epNum);
        intent.putExtra("poster", poster);
        intent.putExtra("category", cat);
        intent.putExtra("ts", ts);
        intent.putExtra("duration", dur);
        if (parentGuid != null) intent.putExtra("parent_guid", parentGuid);
        startActivity(intent);
    }


    // ==================== 详情页（getPlayInfo → 按类型展示） ====================


    private void showDetail(PlayListItem item) {
        switchTab(0);
        savedBrowseList = null; savedBrowseGuid = null;
        showingOverview = false;
        showingEpisodes = false;
        moviesContainer.removeAllViews();
        tvMoviesLoading.setVisibility(View.VISIBLE);
        tvMoviesLoading.setText("加载中...");

        Map<String, String> body = new HashMap<>();
        body.put("item_guid", item.guid);
        apiManager.getApi().getPlayInfo(body).enqueue(new Callback<ApiResponse<PlayInfoResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<PlayInfoResponse>> call,
                                   Response<ApiResponse<PlayInfoResponse>> response) {
                tvMoviesLoading.setVisibility(View.GONE);
                if (!response.isSuccessful() || response.body() == null || response.body().code != 0
                        || response.body().data == null) {
                    Toast.makeText(HomeActivity.this, "获取详情失败", Toast.LENGTH_SHORT).show();
                    return;
                }
                buildDetailPage(item, response.body().data);
            }
            @Override
            public void onFailure(Call<ApiResponse<PlayInfoResponse>> call, Throwable t) {
                tvMoviesLoading.setVisibility(View.GONE);
                Toast.makeText(HomeActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** 构建详情页（自动适应横竖屏） */

    private void buildDetailPage(PlayListItem item, PlayInfoResponse info) {
        moviesContainer.removeAllViews();
        savedDetailItem = item; savedDetailInfo = info;

        boolean isLandscape = getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE;

        String epTitle = info.item != null && info.item.title != null ? info.item.title : item.title;
        String series = info.item != null && info.item.tvTitle != null ? info.item.tvTitle : "";
        int epNum = info.item != null ? info.item.episodeNumber : 0;

        // 海报路径
        String posterPath = info.getBackdropPath();
        if (posterPath == null || (info.item != null && info.item.backdrops == null)) {
            posterPath = info.getPosterPath();
        }
        if (posterPath == null) posterPath = item.poster;
        String pUrl = makePosterUrl(posterPath);

        if (isLandscape) {
            // ====== 横屏：左图右信息 ======
            int screenH = getResources().getDisplayMetrics().heightPixels;
            // 减去状态栏高度
            int sbId = getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (sbId > 0) screenH -= getResources().getDimensionPixelSize(sbId);
            // 减去导航栏高度（手机底部虚拟键）
            int nbId = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
            if (nbId > 0) screenH -= getResources().getDimensionPixelSize(nbId);
            // 减去底部菜单栏（tabBar 52dp + divider 1dp）
            screenH -= (int)(53 * getResources().getDisplayMetrics().density);
            LinearLayout rootRow = new LinearLayout(this);
            rootRow.setOrientation(LinearLayout.HORIZONTAL);
            rootRow.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            // 左侧海报（用卡片同款竖图 9:16），圆角处理
            String posterLand = makePosterUrl(item.poster);
            RoundedImageView posterL = new RoundedImageView(this);
            int posterW = screenH * 2 / 3;
            posterL.setLayoutParams(new LinearLayout.LayoutParams(posterW, screenH));
            posterL.setScaleType(ImageView.ScaleType.FIT_XY);
            posterL.setBackgroundColor(0xFF1A1A1A);
            posterL.setCornerRadius(10);
            if (posterLand != null) {
                posterL.setTag(posterLand);
                new Handler(Looper.getMainLooper()).post(() ->
                        SimpleImageLoader.load(posterLand, posterL, apiManager.getClient()));
            }
            rootRow.addView(posterL);

            // 右侧滚动内容
            androidx.core.widget.NestedScrollView svL = new androidx.core.widget.NestedScrollView(this);
            svL.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
            LinearLayout contentL = new LinearLayout(this);
            contentL.setOrientation(LinearLayout.VERTICAL);
            contentL.setPadding(16, 12, 16, 20);
            buildDetailContent(contentL, item, info, epTitle, series, epNum, pUrl);
            svL.addView(contentL);
            rootRow.addView(svL);

            moviesContainer.addView(rootRow);
        } else {
            // ====== 竖屏：原布局 ======
            androidx.core.widget.NestedScrollView scrollView = new androidx.core.widget.NestedScrollView(this);
            scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(14, 0, 14, 20);

            // 海报
            RoundedImageView poster = new RoundedImageView(this);
            poster.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            poster.setAdjustViewBounds(true);
            poster.setScaleType(ImageView.ScaleType.FIT_CENTER);
            poster.setBackgroundColor(0xFF2A2A2A);
            poster.setCornerRadius(10);
            if (pUrl != null) { poster.setTag(pUrl); }
            content.addView(poster);
            if (pUrl != null) {
                new Handler(Looper.getMainLooper()).post(() ->
                        SimpleImageLoader.load(pUrl, poster, apiManager.getClient()));
            }
            content.addView(makeSpacer(12));

            buildDetailContent(content, item, info, epTitle, series, epNum, pUrl);

            scrollView.addView(content);
            moviesContainer.addView(scrollView);
        }
    }

    /** 构建详情页的核心内容（元数据、播放按钮、剧集列表） */
    private void buildDetailContent(LinearLayout content, PlayListItem item, PlayInfoResponse info,
                                     String epTitle, String series, int epNum, String pUrl) {
        // 元数据卡片
        LinearLayout metaCard = new LinearLayout(this);
        metaCard.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        metaCard.setOrientation(LinearLayout.VERTICAL);
        metaCard.setBackgroundResource(R.drawable.bg_card);
        metaCard.setPadding(16, 16, 16, 16);

        String typeStr = info.type != null ? info.type : item.type;
        String typeLabel = "电影";
        if ("Episode".equals(typeStr)) typeLabel = "剧集";
        else if ("TV".equals(typeStr)) typeLabel = "剧集";
        else if ("Video".equals(typeStr)) typeLabel = "视频";

        int runtime = info.item != null ? info.item.runtime : item.runtime;
        // 评分（直接取 PlayListItem 的 vote_average，保留一位小数）
        String voteRaw = item.voteAverage;
        Log.d("Detail", "vote_average from item=" + voteRaw
                + " from info=" + (info.item != null ? info.item.voteAverage : "null"));
        String voteLabel = null;
        if (voteRaw != null && !voteRaw.isEmpty() && !voteRaw.equals("0") && !voteRaw.equals("0.0")) {
            try {
                float v = Float.parseFloat(voteRaw);
                // vote_average 是 0~10 分
                if (v > 0) voteLabel = String.format("%.1f", v);
            } catch (NumberFormatException ignored) {}
        }

        // 标题
        String bigTitle = epTitle != null ? epTitle : "";
        if (!series.isEmpty() && !series.equals(epTitle)) {
            bigTitle = series + (epNum > 0 ? " " + epNum + "集" : "") + (epTitle != null ? " " + epTitle : "");
        }
        TextView titleBig = new TextView(this);
        titleBig.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        titleBig.setText(bigTitle.trim());
        titleBig.setTextColor(0xFFFFFFFF);
        titleBig.setTextSize(22);
        titleBig.setTypeface(Typeface.DEFAULT_BOLD);
        metaCard.addView(titleBig);

        // 元数据行
        TextView meta = new TextView(this);
        meta.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        meta.setPadding(0, 8, 0, 0);
        StringBuilder mt = new StringBuilder(typeLabel);

        // 剧集信息: tv_title + parent_title
        if (info.item != null && info.item.tvTitle != null && !info.item.tvTitle.isEmpty()) {
            mt.append("  ").append(info.item.tvTitle);
        }
        if (info.item != null && info.item.parentTitle != null && !info.item.parentTitle.isEmpty()) {
            mt.append("  ").append(info.item.parentTitle);
        }
        if (info.item != null && info.item.episodeNumber > 0) {
            mt.append("  第").append(info.item.episodeNumber).append("集");
        }
        if (runtime > 0) {
            long durSec = info.item != null && info.item.duration > 0 ? info.item.duration : runtime * 60L;
            mt.append("  ·  ").append(formatDuration(durSec));
        }
        if (voteLabel != null) mt.append("  ·  ⭐").append(voteLabel);
        // 分辨率
        if (info.item != null && info.item.mediaStream != null
                && info.item.mediaStream.resolutions != null
                && !info.item.mediaStream.resolutions.isEmpty()) {
            mt.append("  ·  ").append(info.item.mediaStream.resolutions.get(0));
        }
        // 音轨
        if (info.item != null && info.item.mediaStream != null
                && info.item.mediaStream.audioType != null
                && !info.item.mediaStream.audioType.isEmpty()) {
            mt.append("  ·  ").append(info.item.mediaStream.audioType.get(0));
        }
        // 日期
        String date = info.item != null && info.item.releaseDate != null
                ? info.item.releaseDate : (info.item != null ? info.item.airDate : null);
        if (date != null && !date.isEmpty()) mt.append("  ·  ").append(date);

        meta.setText(mt.toString());
        meta.setTextColor(0xFFB0B0B0);
        meta.setTextSize(13);
        metaCard.addView(meta);

        content.addView(metaCard);
        content.addView(makeSpacer(12));

        // 简介卡片（限制最大高度，防过长遮挡播放按钮）
        String overview = info.item != null && info.item.overview != null
                ? info.item.overview : item.overview;
        if (overview != null && !overview.isEmpty()) {
            LinearLayout overviewCard = new LinearLayout(this);
            overviewCard.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            overviewCard.setOrientation(LinearLayout.VERTICAL);
            overviewCard.setBackgroundResource(R.drawable.bg_card);
            overviewCard.setPadding(16, 16, 16, 16);

            TextView ovLabel = new TextView(this);
            ovLabel.setText("简介");
            ovLabel.setTextColor(0xFF78909C);
            ovLabel.setTextSize(11);
            overviewCard.addView(ovLabel);

            androidx.core.widget.NestedScrollView ovScroll = new androidx.core.widget.NestedScrollView(this);
            ovScroll.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 300));
            ovScroll.setFocusable(true);
            ovScroll.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
            ovScroll.setOnFocusChangeListener((v, hasFocus) ->
                    v.setBackgroundColor(hasFocus ? 0x33FFFFFF : 0x00000000));
            TextView ov = new TextView(this);
            ov.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            ov.setPadding(0, 6, 0, 0);
            ov.setText(overview);
            ov.setTextColor(0xFFCCCCCC);
            ov.setTextSize(14);
            ov.setLineSpacing(6, 1);
            ovScroll.addView(ov);
            overviewCard.addView(ovScroll);
            content.addView(overviewCard);
            content.addView(makeSpacer(12));
        }

        // 播放时间优先用 play/info 接口返回的 ts
        final String pGuid = item.guid;
        final String pTitle = item.title;
        final String pTV = info.item != null && info.item.tvTitle != null ? info.item.tvTitle : "";
        final String pPoster = item.poster;
        final String pCat = item.getCategoryLabel();
        final long pTs = info.ts > 0 ? info.ts : (item.ts > 0 ? item.ts : 0);
        // 总时长优先用 play/info 接口的 duration，其次是 runtime 转秒、item.duration
        long rawDur = info.item != null && info.item.duration > 0 ? info.item.duration : 0;
        if (rawDur <= 0 && info.item != null && info.item.runtime > 0) rawDur = info.item.runtime * 60L;
        if (rawDur <= 0) rawDur = item.duration;
        final long pDur = rawDur;

        final int pEp = item.episodeNumber;
        final int progressPct = pDur > 0 ? Math.max(0, Math.min(100, (int)(pTs * 100 / pDur))) : 0;
        final String pParentGuid = info.parentGuid != null && !info.parentGuid.isEmpty() ? info.parentGuid : item.parentGuid;

        Log.d("Detail", "pParentGuid=" + pParentGuid + " info.parentGuid=" + (info.parentGuid != null ? info.parentGuid : "null") + " item.parentGuid=" + (item.parentGuid != null ? item.parentGuid : "null"));
        Log.d("Detail", "继续播放: info.ts=" + info.ts + " pTs=" + pTs
                + " info.item.duration=" + (info.item != null ? info.item.duration : "null")
                + " info.item.runtime=" + (info.item != null ? info.item.runtime : "null")
                + " item.duration=" + item.duration + " → pDur=" + pDur + " pTs=" + pTs);

        // 播放按钮（自适应高度）
        FrameLayout playFrame = new FrameLayout(this);
        playFrame.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        playFrame.setMinimumHeight(120);

        // 圆角背景（进度条用两层：蓝色 + 灰色）
        if (progressPct > 0) {
            LinearLayout progressLayer = new LinearLayout(this);
            progressLayer.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            progressLayer.setOrientation(LinearLayout.HORIZONTAL);
            progressLayer.setWeightSum(100);

            // 圆角裁剪：用 GradientDrawable 做背景
            float r = 10 * getResources().getDisplayMetrics().density;
            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setCornerRadii(new float[]{r, r, r, r, r, r, r, r});
            gd.setColor(0xFF455A64);
            progressLayer.setBackgroundDrawable(gd);

            View fill = new View(HomeActivity.this);
            fill.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, progressPct));
            android.graphics.drawable.GradientDrawable fillGd = new android.graphics.drawable.GradientDrawable();
            fillGd.setCornerRadii(new float[]{r, r, 0, 0, 0, 0, r, r});
            fillGd.setColor(0xFF2D6CDF);
            fill.setBackgroundDrawable(fillGd);
            progressLayer.addView(fill);

            View rest = new View(HomeActivity.this);
            rest.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 100 - progressPct));
            rest.setBackgroundColor(0x00000000); // 透明
            progressLayer.addView(rest);

            playFrame.addView(progressLayer);
        } else {
            playFrame.setBackgroundResource(R.drawable.bg_btn_primary);
        }

        // 按钮本身（透明背景）
        Button playBtn = new Button(this);
        playBtn.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        playBtn.setBackgroundDrawable(null);
        playBtn.setFocusable(true);
        playBtn.setGravity(Gravity.CENTER);
        playBtn.setTextColor(0xFFFFFFFF);
        playBtn.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                playBtn.setBackgroundColor(0x55FFFFFF);
                playBtn.setScaleX(1.05f);
                playBtn.setScaleY(1.05f);
            } else {
                playBtn.setBackgroundColor(0x00000000);
                playBtn.setScaleX(1.0f);
                playBtn.setScaleY(1.0f);
            }
        });
        if (pTs > 0) {
            playBtn.setTextSize(16);
            playBtn.setText("▶  继续播放\n" + formatDuration(pTs) + " / " + formatDuration(pDur));
        } else {
            playBtn.setTextSize(22);
            playBtn.setText("▶  播放");
        }

        playBtn.setOnClickListener(v -> {
            Object tag = playBtn.getTag();
            long finalDur = tag instanceof Long ? (Long) tag : pDur;
            launchPlayer(pGuid, pTitle, pTV, pEp, pPoster, pCat, pTs, finalDur, pParentGuid);
        });
        playFrame.addView(playBtn);
        content.addView(playFrame);

        // Episode 类型 → 先加载季列表，点击某季后加载该季剧集
        if (item.guid != null && !item.guid.isEmpty()) {
            content.addView(makeSpacer(16));
            loadSeasons(content, item.guid, item, playBtn, pTs);
        }

    }

    /** 加载剧集列表并按季分组 */
    private void loadEpisodes(final LinearLayout content, final String parentGuid, final PlayListItem item,
                              final Button playBtn, final long pTs, final String historyEpGuid) {
        apiManager.getApi().getEpisodeList(parentGuid).enqueue(new Callback<ApiResponse<List<PlayListItem>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<PlayListItem>>> call,
                                   Response<ApiResponse<List<PlayListItem>>> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().code != 0
                        || response.body().data == null || response.body().data.isEmpty()) return;
                List<PlayListItem> episodes = response.body().data;
                showSeasons(content, episodes, item);
                // 从剧集列表找到历史记录对应的那一集，用它的精确时长覆盖按钮
                if (historyEpGuid != null) {
                    for (PlayListItem ep : episodes) {
                        if (ep.guid.equals(historyEpGuid) && ep.duration > 0) {
                            long epDur = ep.duration;
                            Log.d("Detail", "剧集列表匹配到历史剧集, 时长: " + formatDuration(epDur));
                            playBtn.setText("▶  继续播放\n" + formatDuration(pTs) + " / " + formatDuration(epDur));
                            // 通过 setTag 把修正后的时长传给点击监听
                            playBtn.setTag(epDur);
                            break;
                        }
                    }
                }
            }
            @Override public void onFailure(Call<ApiResponse<List<PlayListItem>>> call, Throwable t) {}
        });
    }

    /** 加载季列表（从 season/list 接口获取） */
    private void loadSeasons(final LinearLayout content, final String itemGuid, final PlayListItem item,
                              final Button playBtn, final long pTs) {
        TextView h = new TextView(this);
        h.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        h.setPadding(0, 14, 0, 14);
        h.setText("选择剧集");
        h.setTextColor(0xFFEEEEEE);
        h.setTextSize(22);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        content.addView(h);

        apiManager.getApi().getSeasonList(itemGuid).enqueue(new Callback<ApiResponse<List<PlayListItem>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<PlayListItem>>> call,
                                   Response<ApiResponse<List<PlayListItem>>> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().code != 0
                        || response.body().data == null || response.body().data.isEmpty()) return;
                List<PlayListItem> seasons = response.body().data;
                for (final PlayListItem season : seasons) {
                    LinearLayout card = new LinearLayout(HomeActivity.this);
                    card.setOrientation(LinearLayout.HORIZONTAL);
                    card.setBackgroundResource(R.drawable.bg_media_card);
                    card.setPadding(16, 18, 16, 18);
                    card.setFocusable(true);
                    card.setMinimumHeight(56);

                    LinearLayout tc = new LinearLayout(HomeActivity.this);
                    tc.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                    tc.setOrientation(LinearLayout.VERTICAL);
                    tc.setGravity(Gravity.CENTER_VERTICAL);
                    TextView st = new TextView(HomeActivity.this);
                    st.setTextSize(16);
                    st.setTextColor(0xFFEEEEEE);
                    st.setText("第 " + season.seasonNumber + " 季");
                    tc.addView(st);
                    TextView ss = new TextView(HomeActivity.this);
                    ss.setTextSize(12);
                    ss.setTextColor(0xFF808080);
                    ss.setText(season.localNumberOfEpisodes > 0 ? season.localNumberOfEpisodes + " 集" : "");
                    tc.addView(ss);
                    card.addView(tc);

                    TextView ar = new TextView(HomeActivity.this);
                    ar.setLayoutParams(new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    ar.setText(">");
                    ar.setTextColor(0xFF808080);
                    ar.setTextSize(20);
                    ar.setGravity(Gravity.CENTER);
                    ar.setPadding(8, 0, 0, 0);
                    card.addView(ar);

                    final int sn = season.seasonNumber > 0 ? season.seasonNumber : 1;
                    card.setOnClickListener(v -> loadEpisodesForSeason(season.guid, sn, item));
                    content.addView(card);
                    content.addView(makeSpacer(12));
                }
            }
            @Override public void onFailure(Call<ApiResponse<List<PlayListItem>>> call, Throwable t) {}
        });
    }

    /** 加载某季的剧集列表（点击季后调用，直接显示剧集） */
    private void loadEpisodesForSeason(String seasonGuid, int seasonNumber, PlayListItem original) {
        showingEpisodes = true;
        moviesContainer.removeAllViews();
        // 标题
        TextView h = new TextView(this);
        h.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 78));
        h.setGravity(Gravity.CENTER);
        h.setText("第 " + seasonNumber + " 季");
        h.setTextColor(0xFFEEEEEE);
        h.setTextSize(21);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        moviesContainer.addView(h);
        moviesContainer.addView(makeSpacer(8));

        apiManager.getApi().getEpisodeList(seasonGuid).enqueue(new Callback<ApiResponse<List<PlayListItem>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<PlayListItem>>> call,
                                   Response<ApiResponse<List<PlayListItem>>> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().code != 0
                        || response.body().data == null || response.body().data.isEmpty()) return;
                for (PlayListItem ep : response.body().data) {
                    moviesContainer.addView(makeEpisodeItem(ep, ep.guid.equals(original.guid)));
                    moviesContainer.addView(makeSpacer(8));
                }
            }
            @Override public void onFailure(Call<ApiResponse<List<PlayListItem>>> call, Throwable t) {}
        });
    }

    /** 显示季列表 */

    private void showSeasons(LinearLayout content, List<PlayListItem> episodes, PlayListItem item) {
        Map<Integer, List<PlayListItem>> map = new HashMap<>();
        for (PlayListItem ep : episodes) {
            int sn = ep.seasonNumber > 0 ? ep.seasonNumber : 1;
            if (!map.containsKey(sn)) map.put(sn, new ArrayList<PlayListItem>());
            map.get(sn).add(ep);
        }

        TextView h = new TextView(this);
        h.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        h.setPadding(0, 14, 0, 14);
        h.setText("选择剧集");
        h.setTextColor(0xFFEEEEEE);
        h.setTextSize(22);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        content.addView(h);

        List<Integer> nums = new ArrayList<>(map.keySet());
        java.util.Collections.sort(nums);
        for (final int sn : nums) {
            final List<PlayListItem> eps = map.get(sn);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setBackgroundResource(R.drawable.bg_media_card);
            card.setPadding(16, 18, 16, 18);
            card.setFocusable(true);
            card.setMinimumHeight(56);

            LinearLayout tc = new LinearLayout(this);
            tc.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            tc.setOrientation(LinearLayout.VERTICAL);
            tc.setGravity(Gravity.CENTER_VERTICAL);
            TextView st = new TextView(this); st.setTextSize(16); st.setTextColor(0xFFEEEEEE);
            st.setText("第 " + sn + " 季"); tc.addView(st);
            TextView ss = new TextView(this); ss.setTextSize(12); ss.setTextColor(0xFF808080);
            ss.setText(eps.size() + " 集"); tc.addView(ss);
            card.addView(tc);

            TextView ar = new TextView(this);
            ar.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            ar.setText(">"); ar.setTextColor(0xFF808080); ar.setTextSize(20);
            ar.setGravity(Gravity.CENTER); ar.setPadding(8, 0, 0, 0);
            card.addView(ar);

            card.setOnClickListener(v -> showEpisodes(eps, sn, sn == 1 ? item : item));
            content.addView(card);
            content.addView(makeSpacer(6));
        }
    }

    /** 显示某季剧集 */

    private void showEpisodes(List<PlayListItem> eps, int sn, PlayListItem original) {
        showingEpisodes = true;
        moviesContainer.removeAllViews();

        // 标题
        TextView h = new TextView(this);
        h.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 78));
        h.setGravity(Gravity.CENTER);
        h.setText("第 " + sn + " 季");
        h.setTextColor(0xFFEEEEEE); h.setTextSize(21);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        moviesContainer.addView(h);
        moviesContainer.addView(makeSpacer(8));

        for (PlayListItem ep : eps) {
            moviesContainer.addView(makeEpisodeItem(ep, ep.guid.equals(original.guid)));
            moviesContainer.addView(makeSpacer(8));
        }
    }

    /** 剧集条目卡片 */

    private View makeEpisodeItem(PlayListItem ep, boolean isCurrent) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setBackgroundResource(R.drawable.bg_media_card);
        card.setPadding(16, 18, 16, 18);
        card.setFocusable(true);
        card.setMinimumHeight(72);

        final String eg = ep.guid;
        final String et = ep.title;
        final String eTV = ep.tvTitle != null ? ep.tvTitle : "";
        final int eEp = ep.episodeNumber;
        final String epPo = ep.poster;
        final String epCa = ep.getCategoryLabel();
        final long epTs = ep.ts > 0 ? ep.ts : 0;
        final long epDu = ep.duration;
        final String epPG = ep.parentGuid;

        card.setOnClickListener(v -> launchPlayer(eg, et, eTV, eEp, epPo, epCa, epTs, epDu, epPG));

        LinearLayout infoCol = new LinearLayout(this);
        infoCol.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        infoCol.setOrientation(LinearLayout.HORIZONTAL);
        infoCol.setGravity(Gravity.CENTER_VERTICAL);

        TextView epNum = new TextView(this);
        epNum.setLayoutParams(new LinearLayout.LayoutParams(130, ViewGroup.LayoutParams.WRAP_CONTENT));
        epNum.setTextSize(15);
        epNum.setTextColor(isCurrent ? 0xFF81C784 : 0xFFB0B0B0);
        epNum.setText("EP" + (ep.episodeNumber > 0 ? ep.episodeNumber : "?"));
        infoCol.addView(epNum);

        LinearLayout rightCol = new LinearLayout(this);
        rightCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        rightCol.setOrientation(LinearLayout.HORIZONTAL);
        rightCol.setGravity(Gravity.CENTER_VERTICAL);

        TextView epTitle = new TextView(this);
        epTitle.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        epTitle.setTextSize(15);
        epTitle.setTextColor(isCurrent ? 0xFF81C784 : 0xFFEEEEEE);
        epTitle.setSingleLine(true);
        epTitle.setEllipsize(TextUtils.TruncateAt.END);
        StringBuilder titleText = new StringBuilder();
        titleText.append(ep.title != null ? ep.title : "未知");
        if (ep.duration > 0) titleText.append("  ").append(formatDuration(ep.duration));
        if (ep.watched == 1) titleText.append("  ✓");
        epTitle.setText(titleText.toString());
        rightCol.addView(epTitle);

        infoCol.addView(rightCol);
        card.addView(infoCol);

        if (isCurrent) {
            TextView badge = new TextView(this);
            badge.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
            badge.setGravity(Gravity.CENTER);
            badge.setText("当前");
            badge.setTextColor(0xFF81C784);
            badge.setTextSize(12);
            badge.setPadding(8, 0, 0, 8);
            card.addView(badge);
        } else {
            Button playEp = new Button(this);
            playEp.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, 76));
            playEp.setBackgroundResource(R.drawable.bg_btn_primary);
            playEp.setText("播放");
            playEp.setTextColor(0xFFEEEEEE);
            playEp.setTextSize(12);
            playEp.setFocusable(true);
            playEp.setOnFocusChangeListener((v, hasFocus) -> {
                playEp.setScaleX(hasFocus ? 1.08f : 1.0f);
                playEp.setScaleY(hasFocus ? 1.08f : 1.0f);
            });
            playEp.setPadding(14, 0, 14, 0);
            playEp.setOnClickListener(v -> launchPlayer(eg, et, eTV, eEp, epPo, epCa, epTs, epDu, epPG));
            card.addView(playEp);
        }
        return card;
    }


    // ==================== 媒体库 Tab ====================


    private void loadMediaLibraries() {
        isSearching = false;
        etSearch.setVisibility(View.VISIBLE);
        clearContainer(libraryContainer, tvLibraryLoading, tvLibraryEmpty);
        tvLibraryLoading.setVisibility(View.VISIBLE);

        final int[] retryCount = {1};
        apiManager.getApi().getMediaDbList().enqueue(new Callback<ApiResponse<List<MediaDbItem>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<MediaDbItem>>> call,
                                   Response<ApiResponse<List<MediaDbItem>>> response) {
                tvLibraryLoading.setVisibility(View.GONE);
                // Auth Failed 时重试一次
                if (response.body() != null && response.body().code == -2 && retryCount[0] > 0) {
                    retryCount[0]--;
                    Log.d("Home", "Auth Failed，重试中...");
                    call.clone().enqueue(this);
                    return;
                }
                if (response.isSuccessful() && response.body() != null && response.body().code == 0
                        && response.body().data != null && !response.body().data.isEmpty()) {
                    mediaLibraries.clear();
                    List<MediaDbItem> filteredLibs = new ArrayList<>();
                    for (MediaDbItem lib : response.body().data) {
                        if (!lib.refreshDisabled) filteredLibs.add(lib);
                    }
                    mediaLibraries.addAll(filteredLibs);
                    populateLibGrid(libraryContainer, filteredLibs);
                    return;
                }
                tvLibraryEmpty.setVisibility(View.VISIBLE);
            }
            @Override
            public void onFailure(Call<ApiResponse<List<MediaDbItem>>> call, Throwable t) {
                tvLibraryLoading.setVisibility(View.GONE);
                tvLibraryEmpty.setText("加载失败: " + t.getMessage());
                tvLibraryEmpty.setVisibility(View.VISIBLE);
            }
        });
    }

    // ==================== 搜索 ====================

    private void setupSearch() {
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || actionId == EditorInfo.IME_ACTION_DONE
                    || actionId == EditorInfo.IME_ACTION_GO
                    || actionId == EditorInfo.IME_ACTION_UNSPECIFIED
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                String q = v.getText().toString().trim();
                performSearch(q);
                hideKeyboard();
                return true;
            }
            return false;
        });
    }

    private void hideKeyboard() {
        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
    }

    private void performSearch(String query) {
        if (query.isEmpty()) {
            clearSearch();
            return;
        }
        isSearching = true;
        libraryContainer.removeAllViews();
        tvLibraryLoading.setVisibility(View.VISIBLE);
        tvLibraryLoading.setText("搜索中...");

        SearchHelper.search(apiManager, query, new SearchHelper.SearchCallback() {
            @Override
            public void onResults(List<PlayListItem> results) {
                tvLibraryLoading.setVisibility(View.GONE);
                showSearchResults(results);
            }

            @Override
            public void onEmpty() {
                tvLibraryLoading.setVisibility(View.GONE);
                showSearchEmpty();
            }

            @Override
            public void onError(String msg) {
                tvLibraryLoading.setVisibility(View.GONE);
                tvLibraryEmpty.setText(msg);
                tvLibraryEmpty.setVisibility(View.VISIBLE);
            }
        });
    }

    private void showSearchResults(List<PlayListItem> results) {
        libraryContainer.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        int cols = Math.max(3, (int) (getResources().getDisplayMetrics().widthPixels / (130 * density)));
        for (int idx = 0; idx < results.size(); idx += cols) {
            LinearLayout row = new LinearLayout(this);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            row.setOrientation(LinearLayout.HORIZONTAL);
            int inRow = Math.min(cols, results.size() - idx);
            for (int c = 0; c < cols && idx + c < results.size(); c++) {
                PlayListItem item = results.get(idx + c);
                View card = makeItemCard(item);
                if (card instanceof ViewGroup) {
                    View ch = ((ViewGroup) card).getChildAt(0);
                    if (ch instanceof ImageView) {
                        int posterH = Math.min(550, (int) (getResources().getDisplayMetrics().widthPixels / cols * 1.5));
                        ch.setLayoutParams(new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, posterH));
                    }
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                lp.rightMargin = 6;
                lp.leftMargin = 6;
                card.setLayoutParams(lp);
                row.addView(card);
            }
            for (int e = inRow; e < cols; e++) {
                View spacer = new View(this);
                spacer.setLayoutParams(new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
                row.addView(spacer);
            }
            libraryContainer.addView(row);
            libraryContainer.addView(makeSpacer(12));
        }
        new Handler(Looper.getMainLooper()).post(() -> loadImagesLazily(libraryContainer, 0));
    }

    private void showSearchEmpty() {
        libraryContainer.removeAllViews();
        TextView empty = new TextView(this);
        empty.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 120));
        empty.setGravity(Gravity.CENTER);
        empty.setText("搜索无结果");
        empty.setTextColor(0xFF808080);
        empty.setTextSize(14);
        libraryContainer.addView(empty);
    }

    private void clearSearch() {
        if (isSearching) {
            isSearching = false;
            etSearch.setText("");
            etSearch.setVisibility(View.VISIBLE);
            loadMediaLibraries();
        }
    }

    private void populateLibGrid(LinearLayout cont, List<MediaDbItem> libs) {
        cont.removeAllViews();
        for (int i = 0; i < libs.size(); i++) {
            cont.addView(makeLibCard(libs.get(i)));
            if (i < libs.size() - 1) cont.addView(makeSpacer(8));
        }
    }


    private View makeLibCard(MediaDbItem lib) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setBackgroundResource(R.drawable.bg_media_card);
        card.setPadding(16, 18, 16, 18);
        card.setFocusable(true);
        card.setMinimumHeight(56);

        LinearLayout text = new LinearLayout(this);
        text.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        text.setOrientation(LinearLayout.VERTICAL);
        text.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setTextSize(16);
        title.setTextColor(0xFFEEEEEE);
        title.setText(lib.title);
        text.addView(title);

        TextView sub = new TextView(this);
        sub.setTextSize(12);
        sub.setTextColor(0xFF808080);
        sub.setText("分类: " + (lib.category != null ? lib.category : "未分类"));
        text.addView(sub);

        card.addView(text);

        TextView arrow = new TextView(this);
        arrow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        arrow.setText("❯");
        arrow.setTextColor(0xFF808080);
        arrow.setTextSize(20);
        arrow.setGravity(Gravity.CENTER);
        arrow.setPadding(8, 0, 0, 0);
        card.addView(arrow);

        card.setTag(lib);
        card.setOnClickListener(v -> {
            MediaDbItem m = (MediaDbItem) card.getTag();
            browseItemsInContainer(m.guid, m.title, libraryContainer, tvLibraryLoading);
        });
        return card;
    }


    // ==================== 设置 ====================


    private void setupSettings() {
        tvSettingUsername.setText("用户名: " + prefs.getString("user", ""));
        String d = prefs.getString(PREF_DECODER, "hardware");
        tvDecoderValue.setText("hardware".equals(d) ? "硬解" : "软解");
        rlDecoderSetting.setOnClickListener(v -> toggleDecoder());

        // 弹幕服务器
        String danmuUrl = prefs.getString("danmu_url", "");
        if (danmuUrl.isEmpty()) {
            String host = prefs.getString("host", "");
            host = host.replaceAll("^https?://", "").replaceAll("/.*$", "").replaceAll(":\\d+$", "");
            danmuUrl = "http://" + host + ":9321";
        }
        tvDanmuUrl.setText(danmuUrl);
        rlDanmuSetting.setOnClickListener(v -> {
            android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this);
            b.setTitle("弹幕服务器地址");
            final android.widget.EditText input = new android.widget.EditText(this);
            input.setText(tvDanmuUrl.getText());
            input.setSelection(input.getText().length());
            b.setView(input);
            b.setPositiveButton("保存", (dialog, which) -> {
                String val = input.getText().toString().trim();
                if (!val.isEmpty()) {
                    prefs.edit().putString("danmu_url", val).apply();
                    tvDanmuUrl.setText(val);
                }
            });
            b.setNegativeButton("重置", (dialog, which) -> {
                prefs.edit().remove("danmu_url").apply();
                String host = prefs.getString("host", "");
                host = host.replaceAll("^https?://", "").replaceAll("/.*$", "").replaceAll(":\\d+$", "");
                tvDanmuUrl.setText("http://" + host + ":9321");
            });
            b.show();
        });

        // 快进退步长
        final int[] savedStep = {prefs.getInt("seek_step", 10)};
        tvSeekStepValue.setText(savedStep[0] + "s");
        rlSeekStep.setOnClickListener(v -> {
            android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this);
            b.setTitle("快进退步长（秒）");
            final android.widget.EditText input = new android.widget.EditText(this);
            input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            input.setText(String.valueOf(savedStep[0]));
            input.setSelection(input.getText().length());
            b.setView(input);
            b.setPositiveButton("保存", (dialog, which) -> {
                try {
                    int val = Integer.parseInt(input.getText().toString().trim());
                    if (val < 1) val = 1;
                    if (val > 300) val = 300;
                    prefs.edit().putInt("seek_step", val).apply();
                    tvSeekStepValue.setText(val + "s");
                    savedStep[0] = val;
                } catch (Exception ignored) {}
            });
            b.setNegativeButton("取消", null);
            b.show();
        });

        apiManager.getApi().getUserInfo().enqueue(new Callback<ApiResponse<UserInfoResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<UserInfoResponse>> call,
                                   Response<ApiResponse<UserInfoResponse>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().code == 0
                        && response.body().data != null) {
                    tvSettingUsername.setText("用户名: " + response.body().data.getDisplayName());
                }
            }
            @Override public void onFailure(Call<ApiResponse<UserInfoResponse>> call, Throwable t) {}
        });
    }


    private void toggleDecoder() {
        String cur = prefs.getString(PREF_DECODER, "hardware");
        if ("hardware".equals(cur)) {
            prefs.edit().putString(PREF_DECODER, "software").apply();
            tvDecoderValue.setText("软解");
            Toast.makeText(this, "解码: 软解 (CPU)", Toast.LENGTH_SHORT).show();
        } else {
            prefs.edit().putString(PREF_DECODER, "hardware").apply();
            tvDecoderValue.setText("硬解");
            Toast.makeText(this, "解码: 硬解 (GPU)", Toast.LENGTH_SHORT).show();
        }
    }


    // ==================== 问题反馈 ====================


    private void setupFeedback() {
        btnFeedback.setOnClickListener(v -> {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("问题反馈")
                    .setMessage("如有问题或建议，请加 QQ群：\n693516430")
                    .setPositiveButton("复制群号", (dialog, which) -> {
                        android.content.ClipboardManager cm = (android.content.ClipboardManager)
                                getSystemService(CLIPBOARD_SERVICE);
                        cm.setText("693516430");
                        Toast.makeText(this, "群号已复制", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("关闭", null)
                    .show();
        });
    }


    // ==================== 登出 ====================


    private void setupLogout() {
        btnLogout.setOnClickListener(v -> logout());
    }


    private void logout() {
        apiManager.setToken(null);
        Toast.makeText(this, "已退出", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("skip_auto_login", true);
        startActivity(intent);
        finish();
    }


    // ==================== 工具 ====================


    private void clearContainer(LinearLayout c, TextView l, TextView e) {
        l.setVisibility(View.GONE);
        e.setVisibility(View.GONE);
        for (int i = c.getChildCount() - 1; i >= 0; i--) {
            View v = c.getChildAt(i);
            if (v != l && v != e) c.removeView(v);
        }
    }


    private View makeSpacer(int h) {
        View v = new View(HomeActivity.this);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, h));
        return v;
    }


    private String formatDuration(long sec) {
        if (sec <= 0) return "";
        long s = sec % 60;
        long m = (sec / 60) % 60;
        long h = sec / 3600;
        if (h > 0) return h + "h" + m + "m" + s + "s";
        return m + "分" + s + "秒";
    }

    /** 逐张加载图片 */

    private void loadImagesLazily(ViewGroup container, int index) {
        List<ImageView> targets = new ArrayList<>();
        collectImageViews(container, targets);
        if (targets.isEmpty() || index >= targets.size()) return;

        ImageView iv = targets.get(index);
        Object tag = iv.getTag();
        if (tag instanceof String) {
            String url = (String) tag;
            if (url.startsWith("http")) {
                SimpleImageLoader.load(url, iv, apiManager.getClient());
            }
        }
        final int next = index + 1;
        if (next < targets.size()) {
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() { loadImagesLazily(container, next); }
            }, 100);
        }
    }


    private void collectImageViews(ViewGroup parent, List<ImageView> out) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof ImageView) {
                out.add((ImageView) child);
            } else if (child instanceof ViewGroup) {
                collectImageViews((ViewGroup) child, out);
            }
        }
    }


    // ==================== 直播频道 ====================


    /** 加载直播频道（total>0 则显示预览区） */
    private void loadLiveChannels() {
        try {
            ItemListRequest liveReq = ItemListRequest.browseLiveChannels();
            Log.d("LiveChannel", "请求体: " + new com.google.gson.Gson().toJson(liveReq));
            Log.d("LiveChannel", "loadLiveChannels 开始请求...");
            apiManager.getApi().getItemList(liveReq).enqueue(new Callback<ApiResponse<ItemListResponse>>() {
                @Override
                public void onResponse(Call<ApiResponse<ItemListResponse>> call,
                                       Response<ApiResponse<ItemListResponse>> response) {
                    try {
                        Log.d("LiveChannel", "响应 code=" + response.code()
                                + " isSuccessful=" + response.isSuccessful());
                        // 打印请求信息
                        okhttp3.Request req = call.request();
                        Log.d("LiveChannel", "请求URL: " + req.url());
                        Log.d("LiveChannel", "请求Method: " + req.method());
                        Log.d("LiveChannel", "请求Headers:");
                        for (int i = 0; i < req.headers().size(); i++) {
                            Log.d("LiveChannel", "  " + req.headers().name(i) + ": " + req.headers().value(i));
                        }
                        if (response.body() != null) {
                            Log.d("LiveChannel", "body code=" + response.body().code
                                    + " msg=" + response.body().msg
                                    + " data=" + new com.google.gson.Gson().toJson(response.body()));
                        } else {
                            Log.w("LiveChannel", "body=null");
                        }
                        if (!response.isSuccessful() || response.body() == null || response.body().code != 0
                                || response.body().data == null || response.body().data.list == null
                                || response.body().data.list.isEmpty()) return;
                        List<PlayListItem> items = response.body().data.list;
                        int total = response.body().data.total;
                        Log.d("LiveChannel", "直播频道: total=" + total + " items=" + items.size());
                        if (total > 0) {
                            List<PlayListItem> preview = items.size() > 20 ? items.subList(0, 20) : items;
                            fillLiveChannelPreview(preview, total);
                        }
                    } catch (Exception e) {
                        Log.e("LiveChannel", "onResponse 异常", e);
                    }
                }
                @Override
                public void onFailure(Call<ApiResponse<ItemListResponse>> call, Throwable t) {
                    Log.e("LiveChannel", "请求失败: " + t.getMessage(), t);
                }
            });
        } catch (Exception e) {
            Log.e("LiveChannel", "loadLiveChannels 异常", e);
        }
    }

    /** 填充直播频道预览区（先移除旧的再添加） */
    private void fillLiveChannelPreview(List<PlayListItem> items, int total) {
        // 移除已有的直播频道区域
        for (int i = moviesContainer.getChildCount() - 1; i >= 0; i--) {
            View v = moviesContainer.getChildAt(i);
            if (v instanceof LinearLayout && "live_channel".equals(v.getTag())) {
                moviesContainer.removeView(v);
            }
        }

        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setTag("live_channel");
        section.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ── 标题行 ──
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setPadding(6, 24, 6, 18);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setMinimumHeight(68);
        headerRow.setId(View.generateViewId());

        TextView header = new TextView(this);
        header.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        header.setText("直播频道");
        header.setTextColor(0xFFEEEEEE);
        header.setTextSize(18);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        headerRow.addView(header);

        Button viewAll = new Button(this);
        viewAll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 68));
        viewAll.setBackgroundResource(R.drawable.bg_input);
        viewAll.setText("查看全部 ›");
        viewAll.setTextColor(0xFFB0B0B0);
        viewAll.setTextSize(14);
        viewAll.setFocusable(true);
        viewAll.setId(View.generateViewId());
        viewAll.setPadding(24, 0, 24, 0);
        viewAll.setOnClickListener(v -> browseLiveChannels());
        viewAll.setOnFocusChangeListener((v, hasFocus) -> {
            viewAll.setTextColor(hasFocus ? 0xFF81C784 : 0xFFB0B0B0);
            viewAll.setBackgroundColor(hasFocus ? 0x44FFFFFF : 0x00000000);
        });
        headerRow.addView(viewAll);
        section.addView(headerRow);

        // ── 横向滚动卡片预览（和其他媒体库预览一致的高度）──
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 410));
        hsv.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(12, 8, 12, 8);

            // 焦点链：卡片 ↑ 到查看全部按钮，↓ 到底部菜单栏
            for (int i = 0; i < items.size(); i++) {
                View card = makeLiveChannelCard(items.get(i));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(220, 380);
                lp.setMargins(10, 0, 10, 0);
                card.setLayoutParams(lp);
                card.setNextFocusUpId(viewAll.getId());
                card.setNextFocusDownId(tabMovies.getId());
                row.addView(card);
            }

        hsv.addView(row);
        section.addView(hsv);
        section.addView(makeSpacer(16));

        // 插入到 moviesContainer
        moviesContainer.addView(section);
    }

    /** 直播频道卡片（和其他卡片样式一致，图片区域空白） */
    private View makeLiveChannelCard(PlayListItem item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_media_card);
        card.setPadding(6, 6, 6, 6);
        card.setFocusable(true);

        // 频道全名 + 彩色背景
        String shortName = item.title != null && !item.title.isEmpty() ? item.title : "?";
        int[] colors = {0xFFE53935, 0xFF1E88E5, 0xFF43A047, 0xFFFB8C00,
                        0xFF8E24AA, 0xFF00ACC1, 0xFF6D4C41, 0xFF546E7A};
        int colorIdx = item.guid != null ? Math.abs(item.guid.hashCode() % colors.length) : 0;
        float r = 10 * getResources().getDisplayMetrics().density;
        android.graphics.drawable.GradientDrawable badgeBg = new android.graphics.drawable.GradientDrawable();
        badgeBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        badgeBg.setCornerRadius(r);
        badgeBg.setColor(colors[colorIdx]);
        TextView channelBadge = new TextView(this);
        channelBadge.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 280));
        channelBadge.setGravity(Gravity.CENTER);
        channelBadge.setText(shortName);
        channelBadge.setTextColor(0xFFFFFFFF);
        int len = shortName.length();
        channelBadge.setTextSize(len <= 2 ? 56 : len <= 4 ? 40 : len <= 6 ? 30 : 22);
        channelBadge.setTypeface(Typeface.DEFAULT_BOLD);
        channelBadge.setBackground(badgeBg);
        card.addView(channelBadge);

        // 底部文字条：类型 + 标题
        LinearLayout textBar = new LinearLayout(this);
        textBar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 88));
        textBar.setOrientation(LinearLayout.VERTICAL);
        textBar.setGravity(Gravity.CENTER_VERTICAL);
        textBar.setPadding(0, 4, 0, 4);

        TextView tag = new TextView(this);
        tag.setTextSize(9);
        tag.setTextColor(0xFF78909C);
        tag.setText("直播");
        textBar.addView(tag);

        final TextView title = new TextView(this);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        title.setMarqueeRepeatLimit(-1);
        title.setTextSize(11);
        title.setTextColor(0xFFEEEEEE);
        title.setText(item.title != null ? item.title : "未知");
        textBar.addView(title);

        card.addView(textBar);

        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                title.setSelected(hasFocus);
            }
        });

        card.setTag(item);
        card.setOnClickListener(v -> {
            PlayListItem it = (PlayListItem) card.getTag();
            launchPlayer(it.guid, it.title, "", 0, null, "LiveChannel", 0, 0, it.parentGuid);
        });
        return card;
    }

    /** 直播频道查看全部 */
    private void browseLiveChannels() {
        savedDetailItem = null;
        savedDetailInfo = null;
        showingEpisodes = false;
        showingOverview = false;
        savedLiveChannelTitle = "直播频道";
        moviesContainer.removeAllViews();
        tvMoviesLoading.setVisibility(View.VISIBLE);
        tvMoviesLoading.setText("加载直播频道...");

        apiManager.getApi().getItemList(ItemListRequest.browseLiveChannels()).enqueue(new Callback<ApiResponse<ItemListResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<ItemListResponse>> call,
                                   Response<ApiResponse<ItemListResponse>> response) {
                tvMoviesLoading.setVisibility(View.GONE);
                if (!response.isSuccessful() || response.body() == null || response.body().code != 0
                        || response.body().data == null || response.body().data.list == null
                        || response.body().data.list.isEmpty()) {
                    TextView e = new TextView(HomeActivity.this);
                    e.setLayoutParams(new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, 120));
                    e.setGravity(Gravity.CENTER);
                    e.setText("暂无直播频道");
                    e.setTextColor(0xFF808080);
                    e.setTextSize(14);
                    moviesContainer.addView(e);
                    return;
                }
                List<PlayListItem> list = response.body().data.list;
                int total = response.body().data.total;
                Log.d("LiveChannel", "直播频道查看全部: total=" + total + " items=" + list.size()
                        + " resp=" + new com.google.gson.Gson().toJson(response.body()));
                renderLiveChannelGrid(list, total);
            }
            @Override
            public void onFailure(Call<ApiResponse<ItemListResponse>> call, Throwable t) {
                tvMoviesLoading.setVisibility(View.GONE);
                Toast.makeText(HomeActivity.this, "加载直播频道失败", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** 渲染直播频道网格（自适应列数） */
    private void renderLiveChannelGrid(List<PlayListItem> list, int total) {
        // 标题
        TextView h = new TextView(this);
        h.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        h.setPadding(6, 8, 6, 4);
        h.setText("直播频道  (" + total + "项)");
        h.setTextColor(0xFFEEEEEE);
        h.setTextSize(14);
        moviesContainer.addView(h);

        // 自适应列数
        float density = getResources().getDisplayMetrics().density;
        int cols = Math.max(3, (int) (getResources().getDisplayMetrics().widthPixels / (130 * density)));

        for (int idx = 0; idx < list.size(); idx += cols) {
            LinearLayout row = new LinearLayout(this);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            row.setOrientation(LinearLayout.HORIZONTAL);
            int inRow = Math.min(cols, list.size() - idx);
            for (int c = 0; c < cols && idx + c < list.size(); c++) {
                PlayListItem pli = list.get(idx + c);
                View card = makeLiveChannelCard(pli);
                // 图片占位区域高度与其他卡片一致：基于列数自适应
                if (card instanceof ViewGroup) {
                    View ch = ((ViewGroup) card).getChildAt(0);
                    int posterH = Math.min(550, (getResources().getDisplayMetrics().widthPixels / cols) * 3 / 2);
                    ch.setLayoutParams(new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, posterH));
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                lp.rightMargin = 6;
                lp.leftMargin = 6;
                card.setLayoutParams(lp);
                row.addView(card);
            }
            // 补齐空位
            for (int e = inRow; e < cols; e++) {
                View spacer = new View(this);
                spacer.setLayoutParams(new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
                row.addView(spacer);
            }
            moviesContainer.addView(row);
            moviesContainer.addView(makeSpacer(8));
        }
    }


    // ==================== 按键 ====================

    @Override

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            // 搜索框有焦点 → 隐藏键盘并清除搜索
            if (etSearch.isFocused()) {
                hideKeyboard();
                etSearch.clearFocus();
            }
            // 搜索模式 → 清除搜索
            if (isSearching) {
                clearSearch();
                return true;
            }
            // 媒体库浏览中 → 返回媒体库首页
            if (currentTab == 1 && savedBrowseGuid != null) {
                savedBrowseGuid = null; savedBrowseList = null;
                loadMediaLibraries();
                return true;
            }
            // 剧集选择页 → 返回详情页
            if (showingEpisodes && savedDetailItem != null && savedDetailInfo != null) {
                showingEpisodes = false;
                buildDetailPage(savedDetailItem, savedDetailInfo);
                return true;
            }
            if (!showingOverview) {
                loadOverview();
                return true;
            }
            if (backPressedTime + 2000 > System.currentTimeMillis()) {
                finish();
            } else {
                backPressedTime = System.currentTimeMillis();
                Toast.makeText(this, "再按一次返回桌面", Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
