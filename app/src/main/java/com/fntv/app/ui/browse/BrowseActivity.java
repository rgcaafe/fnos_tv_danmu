package com.fntv.app.ui.browse;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fntv.app.PlayerActivity;
import com.fntv.app.R;
import com.fntv.app.api.model.ItemListResponse;
import com.fntv.app.api.model.PlayListItem;
import com.fntv.app.core.Prefs;
import com.fntv.app.core.ThemeMode;
import com.fntv.app.data.MediaRepository;
import com.fntv.app.ui.ChoiceDialog;
import com.fntv.app.ui.Ui;
import com.fntv.app.ui.common.GridSpaceDecoration;
import com.fntv.app.ui.common.PosterAdapter;
import com.fntv.app.ui.detail.DetailActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * 浏览页：媒体库网格 / 搜索结果 / 直播频道。
 */
public class BrowseActivity extends AppCompatActivity implements PosterAdapter.Listener {

    private RecyclerView grid;
    private ProgressBar loading;
    private TextView empty, titleView, subtitleView;
    private LinearLayout sortBar;

    private PosterAdapter adapter;
    private String guid;
    private String title;
    private String query;
    private String type;
    private boolean live;
    private boolean folder;
    private int sortColumn = 0; // 0=添加日期, 1=发行日期
    private int sortOrder = 1;  // 0=升序, 1=降序
    private String baseUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeMode.apply(this);
        setContentView(R.layout.activity_browse);
        Ui.applyImmersive(this, (left, right, bottom) -> {
            View root = findViewById(R.id.root);
            if (root != null) root.setPadding(left, 0, right, bottom);
        });

        baseUrl = Prefs.host(this);
        guid = getIntent().getStringExtra("guid");
        title = getIntent().getStringExtra("title");
        query = getIntent().getStringExtra("query");
        type = getIntent().getStringExtra("type");
        live = getIntent().getBooleanExtra("live", false);
        folder = getIntent().getBooleanExtra("folder", false);

        grid = findViewById(R.id.grid);
        loading = findViewById(R.id.loading);
        empty = findViewById(R.id.empty);
        titleView = findViewById(R.id.tvTitle);
        subtitleView = findViewById(R.id.tvSubtitle);
        sortBar = findViewById(R.id.sortBar);

        titleView.setText(title == null ? "浏览" : title);
        subtitleView.setText(query != null ? "搜索：" + query : "");

        View header = findViewById(R.id.header);
        header.setPadding(header.getPaddingLeft(), 0,
                header.getPaddingRight(), header.getPaddingBottom());

        // 和首页一致：状态栏区域用占位高度撑开
        View topSpacer = findViewById(R.id.topSpacer);
        ViewGroup.LayoutParams spacerLp = topSpacer.getLayoutParams();
        spacerLp.height = Ui.statusBarHeight(this) + Ui.dp(this, 12);
        topSpacer.setLayoutParams(spacerLp);

        AppCompatImageButton back = findViewById(R.id.btnBack);
        back.setColorFilter(Ui.color(this, R.color.label));
        back.setOnClickListener(v -> finish());

        setupGrid();
        setupSortBar();
        load();
    }

    private void setupGrid() {
        int contentWidth = Ui.contentWidth(this) - Ui.dimen(this, R.dimen.screen_pad) * 2;
        int minWidth = Ui.dimen(this, R.dimen.grid_min_width);
        int gap = Ui.dp(this, 12);
        int span = Math.max(2, (contentWidth + gap) / (minWidth + gap));
        span = Math.min(8, span);
        int cardWidth = contentWidth / span - gap;

        grid.setLayoutManager(new GridLayoutManager(this, span));
        grid.addItemDecoration(new GridSpaceDecoration(gap));
        adapter = new PosterAdapter(this, baseUrl, cardWidth, true, this);
        adapter.setImageCornerDp(8f);
        adapter.setRingCornerDp(0f);
        grid.setAdapter(adapter);
        grid.setItemAnimator(null);
        grid.setClipToPadding(false);
        grid.setClipChildren(false);
        int pad = Ui.dimen(this, R.dimen.screen_pad);
        // 顶部留出放大空间，卡片聚焦时上边不会被裁掉
        grid.setPadding(pad, Ui.dp(this, 14), pad, Ui.dp(this, 28));
    }

    private void setupSortBar() {
        sortBar.removeAllViews();
        boolean showSort = query == null && !live;
        sortBar.setVisibility(showSort ? View.VISIBLE : View.GONE);
        if (!showSort) return;

        if (type != null) {
            Button kind = chip("Movie".equals(type) ? "电影" : "剧集");
            kind.setClickable(false);
            kind.setFocusable(false);
            sortBar.addView(kind);
        }

        final String[] columns = {"添加日期", "发行日期"};
        final String[] orders = {"升序", "降序"};

        Button columnBtn = chip(columns[sortColumn] + " ▾");
        columnBtn.setOnClickListener(v ->
                ChoiceDialog.show(this, "排序方式", columns, sortColumn, which -> {
                    sortColumn = which;
                    columnBtn.setText(columns[which] + " ▾");
                    load();
                }));
        sortBar.addView(columnBtn);

        Button orderBtn = chip(orders[sortOrder] + " ▾");
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) orderBtn.getLayoutParams();
        lp.leftMargin = Ui.dp(this, 8);
        orderBtn.setLayoutParams(lp);
        orderBtn.setOnClickListener(v ->
                ChoiceDialog.show(this, "排序顺序", orders, sortOrder, which -> {
                    sortOrder = which;
                    orderBtn.setText(orders[which] + " ▾");
                    load();
                }));
        sortBar.addView(orderBtn);
    }

    private Button chip(String text) {
        Button button = new Button(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 36));
        button.setLayoutParams(lp);
        // 参考详情页的「季」按钮：不铺灰色，白底 + 细描边
        button.setBackgroundResource(R.drawable.bg_season);
        Ui.noTint(button);
        button.setText(text);
        button.setTextColor(androidx.core.content.ContextCompat.getColorStateList(
                this, R.color.chip_text));
        button.setTextSize(13f);
        button.setAllCaps(false);
        button.setIncludeFontPadding(true);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
        button.setFocusable(true);
        button.setOnFocusChangeListener(Ui::focus);
        return button;
    }

    private void load() {
        loading.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);
        adapter.submit(new ArrayList<>());

        if (query != null) {
            MediaRepository.get().search(query, new MediaRepository.DataCallback<List<PlayListItem>>() {
                @Override
                public void onData(List<PlayListItem> data) {
                    show(data, data.size() + " 个结果");
                }

                @Override
                public void onError(String message) {
                    fail(message);
                }
            });
            return;
        }

        if (live) {
            MediaRepository.get().liveChannels(new MediaRepository.DataCallback<List<PlayListItem>>() {
                @Override
                public void onData(List<PlayListItem> data) {
                    show(data, data.size() + " 个频道");
                }

                @Override
                public void onError(String message) {
                    fail(message);
                }
            });
            return;
        }

        String column = sortColumn == 0 ? "create_time" : "release_date";
        String order = sortOrder == 0 ? "ASC" : "DESC";
        if (type != null) {
            MediaRepository.get().browseType(type, column, order, 5000,
                    new MediaRepository.DataCallback<ItemListResponse>() {
                        @Override
                        public void onData(ItemListResponse data) {
                            show(data.list, data.total + " 项");
                        }

                        @Override
                        public void onError(String message) {
                            fail(message);
                        }
                    });
            return;
        }
        if (folder) {
            MediaRepository.get().folderItems(guid, column, order, 5000,
                    new MediaRepository.DataCallback<ItemListResponse>() {
                        @Override
                        public void onData(ItemListResponse data) {
                            show(data.list, data.total + " 项");
                        }

                        @Override
                        public void onError(String message) {
                            fail(message);
                        }
                    });
            return;
        }
        MediaRepository.get().items(guid, column, order, 5000,
                new MediaRepository.DataCallback<ItemListResponse>() {
                    @Override
                    public void onData(ItemListResponse data) {
                        show(data.list, data.total + " 项");
                    }

                    @Override
                    public void onError(String message) {
                        fail(message);
                    }
                });
    }

    private void show(List<PlayListItem> data, String subtitle) {
        loading.setVisibility(View.GONE);
        subtitleView.setText(subtitle);
        adapter.submit(data);
        if (data == null || data.isEmpty()) {
            empty.setText("这里还没有内容");
            empty.setVisibility(View.VISIBLE);
        } else {
            empty.setVisibility(View.GONE);
        }
    }

    private void fail(String message) {
        loading.setVisibility(View.GONE);
        empty.setText(message);
        empty.setVisibility(View.VISIBLE);
    }

    @Override
    public void onItem(PlayListItem item) {
        if ("LiveChannel".equals(item.type)) {
            play(item, false);
            return;
        }
        boolean folder = "Directory".equals(item.type) || "Folder".equals(item.type);
        if (folder) {
            Intent intent = new Intent(this, BrowseActivity.class);
            intent.putExtra("guid", item.guid);
            intent.putExtra("title", item.title);
            intent.putExtra("folder", true);
            startActivity(intent);
            return;
        }
        Intent intent = new Intent(this, DetailActivity.class);
        intent.putExtra("guid", item.guid);
        intent.putExtra("title", item.title);
        intent.putExtra("poster", item.poster);
        intent.putExtra("category", item.getCategoryLabel());
        startActivity(intent);
    }

    private void play(PlayListItem item, boolean resume) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("guid", item.guid);
        intent.putExtra("title", item.title);
        intent.putExtra("tv_title", item.tvTitle != null ? item.tvTitle : "");
        intent.putExtra("episode_number", item.episodeNumber);
        intent.putExtra("poster", item.poster);
        intent.putExtra("category", "LiveChannel".equals(item.type)
                ? "LiveChannel" : item.getCategoryLabel());
        intent.putExtra("ts", resume ? Math.max(0, item.ts) : 0);
        intent.putExtra("duration", item.duration);
        if (item.parentGuid != null) intent.putExtra("parent_guid", item.parentGuid);
        startActivity(intent);
    }
}
