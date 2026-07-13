package com.fntv.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Display;
import android.view.Window;
import android.view.WindowManager;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import android.content.pm.ActivityInfo;
import com.fntv.app.api.FnApiManager;
import com.fntv.app.api.model.*;
import com.google.android.exoplayer2.*;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;

import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.ui.CaptionStyleCompat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerActivity extends AppCompatActivity {

    private PlayerView playerView;
    private SimpleExoPlayer player;
    private TextView tvBuffering, tvTime, infoText;
    private SeekBar seekBar;
    private Button btnPlayPause, btnRewind, btnForward, btnSpeed, btnRatio, btnInfo, btnCloseInfo, btnEpisodeList, btnNextEp, btnBack, btnDanmu, btnHdrToggle, btnQuality;
    private ImageView btnLock;
    private TextView tvTitle, tvDanmuStatus, tvDanmuMatch, tvSpeedHint, infoTextAudio, infoTextExtra;
    private Button btnCloudMode, btnBrightness, btnSkip;
    private boolean introSkipped = false, outroSkipped = false;
    private float speedBeforeLongPress = 1.0f;
    private DanmuView danmuView;
    private View controller, infoPanel, topBar;
    private boolean isLocked = false;
    private DanmuManager danmuManager;
    private QualitySelectHelper qualityHelper;

    private Handler handler = new Handler(Looper.getMainLooper());
    private String itemGuid, baseUrl, itemTitle, itemTV, itemPoster, itemCategory, parentGuid;
    private long itemDuration;
    private FnApiManager apiManager;
    private String mediaGuid, videoGuid, audioGuid, subtitleGuid, resolution;
    private boolean seeked = false, ctrlVis = false, infoVis = false;
    private long seekTs = 0;
    private float[] speeds = {1.0f, 1.25f, 1.5f, 2.0f, 0.5f, 0.75f};
    private int speedIdx = 0, ratioIdx = 0;
    private boolean isHwDecode = true;
    private EpisodeManager episodeManager;
    private int seasonNumber = 1;
    private long backPressedTime = 0;
    private CloudStreamManager cloudStreamManager;
    private boolean useHls = false;
    private int seekStep = 10000;
    private int streamBitrate = 0; // bps 来自 stream API
    private Runnable seekCommitR;
    private long pendingSeekMs = -1;
    private String savedPlaybackUrl = null; // 当前播放地址（用于硬解失败后切软解重试）
    private String customQualityRes = "";   // 非原画时的分辨率
    private int customQualityBitrate = 0;   // 非原画时的码率
    private String customPlayLink = "";     // 非原画时的 play_link
    private static final String TAG = "Player";

    private static final int[] RATIO_MODES = {0, 1, 2};
    private static final String[] RATIO_LABELS = {"适应", "拉伸", "缩放"};
    private String actualVideoDecoder = "";
    private String actualAudioDecoder = "";
    // 流 API 探测数据
    private String streamVCodec = "", streamVProfile = "", streamVPixFmt = "", streamVColor = "", streamVFps = "";
    private int streamVWidth = 0, streamVHeight = 0, streamVBitDepth = 0;
    private boolean streamVHdr = false;
    private long streamFileSize = 0;
    private int streamDuration = 0; // 秒
    private String streamContainer = "";
    private String streamResolution = "";
    private boolean hdrNotified = false; // HDR 已提示过一次
    private boolean firstReady = true;   // 首次进入 READY（用于控制初始 UI 显示）
    private java.util.List<StreamResponse.AudioStreamInfo> streamAudioTracks;
    private java.util.List<StreamResponse.SubtitleStreamInfo> streamSubtitleTracks;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.BLACK);
            getWindow().setNavigationBarColor(Color.BLACK);
        }

        apiManager = FnApiManager.getInstance();
        SharedPreferences prefs = getSharedPreferences("fntv_prefs", MODE_PRIVATE);
        baseUrl = prefs.getString("host", "").replaceAll("/+$", "");
        isHwDecode = "hardware".equals(prefs.getString("decoder_mode", "hardware"));

        itemGuid = getIntent().getStringExtra("guid");
        seekTs = getIntent().getLongExtra("ts", 0) * 1000L;
        itemDuration = getIntent().getLongExtra("duration", 0);
        itemTitle = getIntent().getStringExtra("title");
        itemTV = getIntent().getStringExtra("tv_title");
        itemPoster = getIntent().getStringExtra("poster");
        itemCategory = getIntent().getStringExtra("category");
        parentGuid = getIntent().getStringExtra("parent_guid");

        playerView = findViewById(R.id.playerView);
        tvBuffering = findViewById(R.id.tvBuffering);
        tvTime = findViewById(R.id.tvTime);
        seekBar = findViewById(R.id.seekBar);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnRewind = findViewById(R.id.btnRewind);
        btnForward = findViewById(R.id.btnForward);
        btnSpeed = findViewById(R.id.btnSpeed);
        btnRatio = findViewById(R.id.btnRatio);
        btnInfo = findViewById(R.id.btnInfo);
        btnQuality = findViewById(R.id.btnQuality);
        btnCloseInfo = findViewById(R.id.btnCloseInfo);
        btnEpisodeList = findViewById(R.id.btnEpisodeList);
        btnNextEp = findViewById(R.id.btnNextEp);
        btnBack = findViewById(R.id.btnBack);
        btnDanmu = findViewById(R.id.btnDanmu);
        danmuView = findViewById(R.id.danmuView);
        btnLock = (ImageView) findViewById(R.id.btnLock);
        tvTitle = findViewById(R.id.tvTitle);
        tvDanmuStatus = findViewById(R.id.tvDanmuStatus);
        btnCloudMode = findViewById(R.id.btnCloudMode);
        tvDanmuMatch = findViewById(R.id.tvDanmuMatch);
        tvSpeedHint = findViewById(R.id.tvSpeedHint);
        topBar = findViewById(R.id.topBar);
        controller = findViewById(R.id.controller);
        infoPanel = findViewById(R.id.infoPanel);
        infoText = findViewById(R.id.infoText);
        infoTextAudio = findViewById(R.id.infoTextAudio);
        infoTextExtra = findViewById(R.id.infoTextExtra);

        initPlayer();

        danmuManager = new DanmuManager(this, new DanmuManager.DataProvider() {
            @Override public Player getPlayer() { return player; }
            @Override public long getItemDuration() { return itemDuration; }
            @Override public String getItemTV() { return itemTV; }
            @Override public String getItemTitle() { return itemTitle; }
            @Override public String getItemGuid() { return itemGuid; }
            @Override public String getParentGuid() { return parentGuid; }
        }, danmuView, tvDanmuStatus, tvDanmuMatch, btnDanmu, prefs);
        danmuManager.initFromPrefs();

        findViewById(android.R.id.content).setOnTouchListener(new View.OnTouchListener() {
            private boolean longPressing = false;
            private android.os.Handler longPressHandler = new android.os.Handler(Looper.getMainLooper());
            @Override public boolean onTouch(View v, android.view.MotionEvent event) {
                if (isLocked) {
                    if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                        showCtrl(true);
                    }
                    return true;
                }
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        longPressing = false;
                        longPressHandler.postDelayed(() -> {
                            longPressing = true;
                            if (player != null) {
                                speedBeforeLongPress = player.getPlaybackParameters().speed;
                                player.setPlaybackSpeed(2.0f);
                                danmuView.setPlaybackSpeed(2.0f);
                                showCtrl(false);
                                if (tvSpeedHint != null) {
                                    tvSpeedHint.setVisibility(View.VISIBLE);
                                }
                            }
                        }, 500);
                        return true;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        longPressHandler.removeCallbacksAndMessages(null);
                        if (longPressing) {
                            longPressing = false;
                            if (player != null) {
                                player.setPlaybackSpeed(speedBeforeLongPress);
                                danmuView.setPlaybackSpeed(speedBeforeLongPress);
                            }
                            if (tvSpeedHint != null) {
                                tvSpeedHint.setVisibility(View.GONE);
                            }
                            return true;
                        } else {
                            showCtrl(true);
                        }
                        return true;
                }
                return false;
            }
        });

        episodeManager = new EpisodeManager(new EpisodeManager.Callback() {
            @Override public String getBaseUrl() { return baseUrl; }
            @Override public String getParentGuid() { return parentGuid; }
            @Override public String getItemGuid() { return itemGuid; }
            @Override public int getEpisodeNumber() { return getIntent().getIntExtra("episode_number", 0); }
            @Override public FnApiManager getApiManager() { return apiManager; }
            @Override public Context getContext() { return PlayerActivity.this; }
            @Override public void onSwitchEpisode(String guid, String title) {
                introSkipped = false;
                outroSkipped = false;
                itemGuid = guid;
                itemTitle = title;
                mediaGuid = null;
                seeked = false;
                seekTs = 0;
                episodeManager.reset();
                loadPlayInfo();
            }
        }, btnEpisodeList, btnNextEp);

        btnPlayPause.setOnClickListener(v -> togglePlay());
        seekStep = getSharedPreferences("fntv_prefs", MODE_PRIVATE).getInt("seek_step", 10) * 1000;
        btnRewind.setOnClickListener(v -> seekRel(-seekStep));
        btnForward.setOnClickListener(v -> seekRel(seekStep));
        btnRewind.setText("-" + (seekStep / 1000) + "秒");
        btnForward.setText("+" + (seekStep / 1000) + "秒");
        btnSpeed.setOnClickListener(v -> cycleSpeed());
        btnRatio.setOnClickListener(v -> cycleRatio());
        btnInfo.setOnClickListener(v -> toggleInfo());
        qualityHelper = new QualitySelectHelper(this, apiManager, getSharedPreferences("fntv_prefs", MODE_PRIVATE),
                new QualitySelectHelper.QualityCallback() {
                    @Override public void onQualityChanged(int level) {
                        customQualityRes = "";
                        customQualityBitrate = 0;
                        customPlayLink = "";
                        if (cloudStreamManager != null) {
                            getSharedPreferences("fntv_prefs", MODE_PRIVATE)
                                    .edit().putInt("stream_quality_level", level).apply();
                            cloudStreamManager.reloadPlayback();
                        }
                    }
                    @Override public void onPlayLinkChanged(String playLink, String res, int bps) {
                        customQualityRes = res;
                        customQualityBitrate = bps;
                        customPlayLink = playLink;
                        // 记录切换前的播放位置（秒）
                        final long seekPosMs = player != null ? Math.max(0, player.getCurrentPosition()) : 0;
                        String fullUrl = baseUrl + playLink;
                        Log.d(TAG, "画质切换新链接: " + fullUrl + " res=" + res + " bitrate=" + bps + " seek=" + seekPosMs);
                        if (player != null) {
                            savedPlaybackUrl = fullUrl;
                            useHls = fullUrl.contains(".m3u8");
                            com.google.android.exoplayer2.upstream.DataSource.Factory f = () -> new OkHttpExoDataSource(apiManager.getStreamClient());
                            if (useHls) {
                                player.setMediaSource(new com.google.android.exoplayer2.source.hls.HlsMediaSource.Factory(f).createMediaSource(MediaItem.fromUri(fullUrl)));
                            } else {
                                player.setMediaSource(new com.google.android.exoplayer2.source.ProgressiveMediaSource.Factory(f, new DefaultExtractorsFactory()).createMediaSource(MediaItem.fromUri(fullUrl)));
                            }
                            player.prepare();
                            player.setPlayWhenReady(true);
                            // 等播放器就绪后 seek 到切换前位置 + 更新信息面板
                            player.addListener(new Player.Listener() {
                                @Override public void onPlaybackStateChanged(int s) {
                                    if (s == Player.STATE_READY) {
                                        if (seekPosMs > 0) player.seekTo(seekPosMs);
                                        updateInfo();
                                        player.removeListener(this);
                                    }
                                }
                            });
                        }
                    }
                    @Override public String getMediaGuid() { return mediaGuid; }
                    @Override public String getAccount() {
                        return getSharedPreferences("fntv_prefs", MODE_PRIVATE).getString("user", "video");
                    }
                    @Override public long getPlaybackPosition() {
                        return player != null ? player.getCurrentPosition() / 1000 : 0;
                    }
                });
        if (btnQuality != null) {
            btnQuality.setOnClickListener(v -> qualityHelper.showQualityDialog());
            // 初始检查：如果右上角直链按钮已显示，隐藏画质按钮
            if (btnCloudMode.getVisibility() == View.VISIBLE) {
                btnQuality.setVisibility(View.GONE);
            }
        }
        btnBack.setOnClickListener(v -> { restoreOrientation(); finish(); });
        btnDanmu.setOnClickListener(v -> danmuManager.showSettings());
        btnLock.setOnClickListener(v -> {
            isLocked = !isLocked;
            btnLock.setImageResource(isLocked ? R.drawable.ic_lock : R.drawable.ic_unlock);
            if (isLocked) {
                topBar.setVisibility(View.INVISIBLE);
                controller.setVisibility(View.INVISIBLE);
                btnLock.setVisibility(View.INVISIBLE);
            } else {
                showCtrl(true);
                // 解锁后焦点还给视频区域
                playerView.requestFocus();
            }
        });
        btnCloseInfo.setOnClickListener(v -> { infoPanel.setVisibility(View.GONE); infoVis = false; });
        btnBrightness = findViewById(R.id.btnBrightness);
        if (btnBrightness != null) {
            btnBrightness.setOnClickListener(v -> showBrightnessDialog());
        }
        btnSkip = findViewById(R.id.btnSkip);
        if (btnSkip != null) {
            btnSkip.setOnClickListener(v -> showIntroOutroDialog());
        }
        // 应用保存的亮度和 HDR 设置
        int savedBright = getSharedPreferences("fntv_prefs", MODE_PRIVATE).getInt("video_brightness", 100);
        if (savedBright != 100) applyBrightness(savedBright);
        applyHdrMode();
        btnEpisodeList.setOnClickListener(v -> episodeManager.showPicker());
        btnNextEp.setOnClickListener(v -> episodeManager.playNext());

        cloudStreamManager = new CloudStreamManager(new CloudStreamManager.Callback() {
            @Override public String getBaseUrl() { return baseUrl; }
            @Override public String getMediaGuid() { return mediaGuid; }
            @Override public FnApiManager getApiManager() { return apiManager; }
            @Override public Context getContext() { return PlayerActivity.this; }
            @Override public SharedPreferences getPrefs() { return getSharedPreferences("fntv_prefs", MODE_PRIVATE); }
            @Override public void onStreamInfoParsed(CloudStreamManager.StreamInfo info) {
                streamBitrate = info.bitrate;
                streamVCodec = info.vCodec;
                streamVProfile = info.vProfile;
                streamVWidth = info.width;
                streamVHeight = info.height;
                streamVBitDepth = info.bitDepth;
                streamVHdr = info.vHdr;
                streamVPixFmt = info.vPixFmt;
                streamVColor = info.vColor;
                streamVFps = info.vFps;
                streamDuration = info.duration;
                streamFileSize = info.fileSize;
                streamContainer = info.container;
                streamResolution = info.resolution != null ? info.resolution : "";
                streamAudioTracks = info.audioTracks;
                streamSubtitleTracks = info.subtitleTracks;
                if (streamVCodec.isEmpty() || streamContainer.isEmpty()) {
                    probeWithMediaExtractor();
                }
            }
            @Override public void onStreamDataFailed() { startPlayback(); }
            @Override public void startPlayback() { PlayerActivity.this.startPlayback(); }
            @Override public void onTrackChanged() {
                final Format oldFmt = player != null ? player.getAudioFormat() : null;
                final int[] tries = {6};
                handler.post(new Runnable() {
                    @Override public void run() {
                        if (player == null) return;
                        Format newFmt = player.getAudioFormat();
                        if (newFmt != null && newFmt != oldFmt) {
                            updateInfo();
                        } else if (tries[0] > 0) {
                            tries[0]--;
                            handler.postDelayed(this, 500);
                        }
                    }
                });
            }
            @Override public void reloadPlayback() {
                mediaGuid = null;
                seeked = false;
                seekTs = 0;
                cloudStreamManager.resetForQualitySwitch();
                loadPlayInfo();
            }
            @Override public void probeWithMediaExtractor() { PlayerActivity.this.probeWithMediaExtractor(); }
            @Override public void onCloudBtnVisibilityChanged(boolean vis) {
                if (tvDanmuMatch != null) {
                    ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) tvDanmuMatch.getLayoutParams();
                    if (lp != null) {
                        lp.rightMargin = vis ? (int) (100 * getResources().getDisplayMetrics().density) : 20;
                        tvDanmuMatch.setLayoutParams(lp);
                    }
                }
                // 直链/STRM 按钮显示时，隐藏画质按钮
                if (vis && btnQuality != null) btnQuality.setVisibility(View.GONE);
            }
            @Override public void runOnUiThread(Runnable r) { PlayerActivity.this.runOnUiThread(r); }
        }, btnCloudMode, getSharedPreferences("fntv_prefs", MODE_PRIVATE));
        cloudStreamManager.initFromPrefs();
        cloudStreamManager.setPlayer(player);

        // 顶部栏焦点链
        btnCloudMode.setNextFocusLeftId(btnBack.getId());
        btnCloudMode.setNextFocusDownId(btnLock.getId());
        btnBack.setNextFocusRightId(btnCloudMode.getId());
        btnBack.setNextFocusDownId(btnDanmu.getId());
        btnDanmu.setNextFocusUpId(btnBack.getId());
        btnLock.setNextFocusUpId(btnCloudMode.getId());

        // 音轨/字幕选择按钮
        Button btnAudioTrack = findViewById(R.id.btnAudioTrack);
        Button btnSubtitleTrack = findViewById(R.id.btnSubtitleTrack);
        btnHdrToggle = findViewById(R.id.btnHdrToggle);
        if (btnAudioTrack != null) {
            btnAudioTrack.setOnClickListener(v -> cloudStreamManager.showAudioTrackDialog(PlayerActivity.this));
        }
        if (btnSubtitleTrack != null) {
            btnSubtitleTrack.setOnClickListener(v -> cloudStreamManager.showSubtitleTrackDialog(PlayerActivity.this));
        }
        if (btnHdrToggle != null) {
            btnHdrToggle.setOnClickListener(v -> toggleHdr());
            updateHdrButtonText();
        }

        setupFocusAutoHide();

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (fromUser && player != null) {
                    // 立即更新 UI（时间显示）
                    tvTime.setText(FormatUtils.fmt(p) + " / " + FormatUtils.fmt(player.getDuration()));
                    if (tvSeekOverlay.getVisibility() == View.VISIBLE) {
                        tvSeekOverlay.setText(FormatUtils.fmt(p) + " / " + FormatUtils.fmt(player.getDuration()));
                    }
                    // 防抖：停止操作 1s 后才真正 seek，避免按住时大量请求
                    if (seekCommitR != null) handler.removeCallbacks(seekCommitR);
                    pendingSeekMs = p;
                    seekCommitR = () -> {
                        if (player != null) {
                            player.seekTo(p);
                            if (danmuManager != null) danmuManager.onSeekTo(p);
                        }
                        pendingSeekMs = -1;
                    };
                    handler.postDelayed(seekCommitR, 1000);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {
                // 触摸松开时立即执行最后的 seek
                pendingSeekMs = -1;
                if (seekCommitR != null) handler.removeCallbacks(seekCommitR);
                if (player != null && sb.getProgress() >= 0) {
                    player.seekTo(sb.getProgress());
                    if (danmuManager != null) danmuManager.onSeekTo(sb.getProgress());
                }
            }
        });

        showCtrl(true);
        loadPlayInfo();
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        hideSystemUi();

        // 控制栏隐藏时的进度时间浮层
        tvSeekOverlay = new TextView(this);
        tvSeekOverlay.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ((FrameLayout.LayoutParams) tvSeekOverlay.getLayoutParams()).gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        tvSeekOverlay.setPadding(32, 16, 32, 16);
        tvSeekOverlay.setTextColor(Color.WHITE);
        tvSeekOverlay.setTextSize(22);
        tvSeekOverlay.setBackgroundColor(0x88000000);
        tvSeekOverlay.setVisibility(View.GONE);
        ((FrameLayout) findViewById(android.R.id.content)).addView(tvSeekOverlay);

        // 初始焦点给视频区域，始终由 playerView 持有焦点
        playerView.setFocusable(true);
        playerView.requestFocus();
    }

    private void initPlayer() {
        // 强制最高刷新率（Android 11+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Window win = getWindow();
            if (win != null) {
                WindowManager.LayoutParams lp = win.getAttributes();
                Display.Mode[] modes = getWindowManager().getDefaultDisplay().getSupportedModes();
                float maxRefresh = 60f;
                for (Display.Mode m : modes) {
                    if (m.getRefreshRate() > maxRefresh) maxRefresh = m.getRefreshRate();
                }
                lp.preferredDisplayModeId = 0;
                for (Display.Mode m : modes) {
                    if (m.getRefreshRate() == maxRefresh) {
                        lp.preferredDisplayModeId = m.getModeId();
                        break;
                    }
                }
                win.setAttributes(lp);
            }
        }
        DefaultRenderersFactory rf = new DefaultRenderersFactory(this);
        if ("software".equals(getSharedPreferences("fntv_prefs", MODE_PRIVATE).getString("decoder_mode", "hardware"))) {
            rf.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER);
        } else {
            rf.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON);
        }
        player = new SimpleExoPlayer.Builder(this, rf)
                .setTrackSelector(new DefaultTrackSelector(this)).build();
        playerView.setPlayer(player);
        playerView.setUseController(false);
        playerView.setShutterBackgroundColor(Color.TRANSPARENT);
        playerView.setKeepScreenOn(true);
        // 字幕样式：白色文字，透明背景，黑色描边
        com.google.android.exoplayer2.ui.CaptionStyleCompat captionStyle =
                new com.google.android.exoplayer2.ui.CaptionStyleCompat(
                        Color.WHITE,                    // 前景色
                        Color.TRANSPARENT,              // 背景色（透明）
                        Color.TRANSPARENT,              // 窗口色（透明）
                        com.google.android.exoplayer2.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                        Color.BLACK,                    // 描边色
                        null                            // 字体
                );
        if (playerView.getSubtitleView() != null) {
            playerView.getSubtitleView().setStyle(captionStyle);
        }

        player.addAnalyticsListener(new com.google.android.exoplayer2.analytics.AnalyticsListener() {
            @Override
            public void onVideoDecoderInitialized(EventTime eventTime, String decoderName,
                                                  long initializedTimestampMs) {
                actualVideoDecoder = decoderName;
                Log.d(TAG, "视频解码器: " + decoderName);
            }

            @Override
            public void onAudioDecoderInitialized(EventTime eventTime, String decoderName,
                                                  long initializedTimestampMs) {
                actualAudioDecoder = decoderName;
                Log.d(TAG, "音频解码器: " + decoderName);
            }
        });

        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int s) {
                tvBuffering.setVisibility(s == Player.STATE_BUFFERING ? View.VISIBLE : View.GONE);
                if (s == Player.STATE_READY) {
                    if (!seeked && seekTs > 0) { player.seekTo(seekTs); seeked = true; }
                    startSave(); updateTime();
                    if (firstReady) { saveProgress(); showCtrl(true); firstReady = false; }
                    btnPlayPause.setText(player.isPlaying() ? "暂停" : "播放");
                    if (danmuManager != null) danmuManager.onPlayerReady();
                    // HDR 检测（延时等格式就绪）
                    checkHdr();
                    // 打印音轨信息
                    com.google.android.exoplayer2.Format af2 = player.getAudioFormat();
                    if (af2 != null) {
                        Log.d(TAG, "音轨: codec=" + af2.codecs + " mime=" + af2.sampleMimeType
                                + " 采样率=" + af2.sampleRate + "Hz"
                                + " 声道=" + af2.channelCount
                                + " 码率=" + af2.bitrate);
                    } else {
                        Log.d(TAG, "音轨: 无音频信息");
                    }
                    // 片头跳过（只开始触发一次，片尾在 updateTime 实时监测）
                    if (!introSkipped && (parentGuid != null || (itemTV != null && !itemTV.isEmpty()))) {
                        SharedPreferences sp = getSharedPreferences("fntv_prefs", MODE_PRIVATE);
                        String skipId = parentGuid != null && !parentGuid.isEmpty() ? parentGuid : itemTV;
                        int introSec = sp.getInt("skip_" + skipId + "_intro", 0);
                        if (introSec > 0) {
                            int pos = (int)(player.getCurrentPosition() / 1000);
                            if (pos < introSec) { player.seekTo(introSec * 1000L); danmuManager.showDanmuStatus("跳过片头 " + introSec + "秒"); }
                            introSkipped = true;
                        }
                    }
                } else if (s == Player.STATE_ENDED) {
                    Log.d(TAG, "STATE_ENDED hasNext=" + (episodeManager != null && episodeManager.hasNext()));
                    if (episodeManager != null && episodeManager.hasNext()) {
                        episodeManager.playNext();
                    }
                } else {
                    stopSave();
                    if (danmuManager != null) danmuManager.onPlayerPause();
                }
            }
            int retryCount = 0;
            private boolean swDecoderTried = false;
            @Override public void onPlayerError(PlaybackException e) {
                // 打印完整错误链
                StringBuilder sb2 = new StringBuilder("播放错误: " + e.getMessage());
                Throwable tc = e;
                while (tc != null) {
                    sb2.append("\n  ").append(tc.getClass().getSimpleName()).append(": ").append(tc.getMessage());
                    tc = tc.getCause();
                }
                Log.e(TAG, sb2.toString());
                // 视频解码器崩溃 → 自动切软解重试
                if (!swDecoderTried && e instanceof ExoPlaybackException
                        && e.getCause() instanceof com.google.android.exoplayer2.video.MediaCodecVideoDecoderException) {
                    swDecoderTried = true;
                    Log.d(TAG, "硬解失败，切换到软解重试");
                    getSharedPreferences("fntv_prefs", MODE_PRIVATE)
                            .edit().putString("decoder_mode", "software").apply();
                    isHwDecode = false;
                    handler.post(() -> recreatePlayerWithSwDecoder());
                    return;
                }
                // 按响应码切换
                int code = OkHttpExoDataSource.lastResponseCode;
                if (code == 200 && !useHls && cloudStreamManager.hasDirectUrl()) {
                    useHls = true;
                    Log.d(TAG, "响应200，切换到HLS");
                    switchMediaSource(true);
                    return;
                } else if (code == 206 && useHls && cloudStreamManager.hasDirectUrl()) {
                    useHls = false;
                    Log.d(TAG, "响应206，切换到渐进式");
                    switchMediaSource(false);
                    return;
                } else if (cloudStreamManager.hasDirectUrl() && useHls) {
                    // 非200/206时按渐进式重试
                    useHls = false;
                    Log.d(TAG, "非200/206响应码(" + code + ")，切换到渐进式");
                    switchMediaSource(false);
                    return;
                }
                if (retryCount < 5 && player != null) {
                    retryCount++;
                    handler.postDelayed(() -> {
                        if (player != null) {
                            player.prepare();
                            player.setPlayWhenReady(true);
                        }
                    }, 2000 * retryCount);
                }
            }
        });


    }

    private void loadPlayInfo() {
        hdrNotified = false;
        Map<String, String> b = new HashMap<>(); b.put("item_guid", itemGuid);
        Log.d(TAG, "play/info 请求: " + new com.google.gson.Gson().toJson(b));
        apiManager.getApi().getPlayInfo(b).enqueue(new retrofit2.Callback<ApiResponse<PlayInfoResponse>>() {
            @Override public void onResponse(retrofit2.Call<ApiResponse<PlayInfoResponse>> call,
                                             retrofit2.Response<ApiResponse<PlayInfoResponse>> r) {
                if (r.isSuccessful() && r.body() != null && r.body().code == 0 && r.body().data != null) {
                    PlayInfoResponse info = r.body().data;
                    mediaGuid = info.mediaGuid; videoGuid = info.videoGuid; audioGuid = info.audioGuid;
                    if (info.guid != null && !info.guid.isEmpty()) itemGuid = info.guid;
                    if (info.parentGuid != null && !info.parentGuid.isEmpty()) parentGuid = info.parentGuid;
                    Log.d(TAG, "play/info 返回: type=" + info.getClass().getSimpleName()
                            + " guid=" + info.guid
                            + " mediaGuid=" + info.mediaGuid
                            + " audioGuid='" + info.audioGuid + "'"
                            + " videoGuid=" + info.videoGuid
                            + " subtitleGuid=" + info.subtitleGuid
                            + " raw=" + new com.google.gson.Gson().toJson(info));
                    // 从 intent 的 parent_guid 兜底（详情页传递的）
                    if (parentGuid == null || parentGuid.isEmpty()) {
                        parentGuid = getIntent().getStringExtra("parent_guid");
                    }
                    subtitleGuid = info.subtitleGuid != null ? info.subtitleGuid : "_no_display_";
                    if (info.item != null && info.item.tvTitle != null) itemTV = info.item.tvTitle;
                    if (info.item != null) itemTitle = info.item.title;
                    if (info.item != null && info.item.seasonNumber > 0) seasonNumber = info.item.seasonNumber;
                    if (info.item != null) getIntent().putExtra("episode_number", info.item.episodeNumber);
                    int epNum = info.item != null ? info.item.episodeNumber : 0;
                    String matchName = itemTV != null && !itemTV.isEmpty() ? itemTV : itemTitle;
                    if (matchName != null && !matchName.isEmpty() && epNum > 0) {
                        matchName = matchName + " S" + String.format("%02d", seasonNumber) + "E" + String.format("%02d", epNum);
                    }
                    if (danmuManager != null) danmuManager.loadDanmu(matchName, itemGuid);
                    if (info.item != null && info.item.mediaStream != null
                            && info.item.mediaStream.resolutions != null
                            && !info.item.mediaStream.resolutions.isEmpty())
                        resolution = info.item.mediaStream.resolutions.get(0);

                    // 直播频道：直接从 live_channels 取第一个流地址播放
                    if (info.liveChannels != null && !info.liveChannels.isEmpty()) {
                        String liveUrl = info.liveChannels.get(0).path;
                        Log.d(TAG, "直播频道播放地址: " + liveUrl);
                        playLiveStream(liveUrl);
                        tvTitle.setText(itemTitle != null ? itemTitle : "直播");
                        return;
                    }

                    // 获取直链信息，获取完后开始播放
                    cloudStreamManager.fetchDirectLink(itemGuid, mediaGuid);
                }
            }
            @Override public void onFailure(retrofit2.Call<ApiResponse<PlayInfoResponse>> call, Throwable t) {}
        });
    }

    /** 开始播放（加载到 ExoPlayer） */
    private void startPlayback() {
        if (mediaGuid == null) return;
        CloudStreamManager.PlaybackConfig cfg = cloudStreamManager.getPlaybackConfig(baseUrl, mediaGuid);
        OkHttpExoDataSource.setChunkedMode(cfg.chunkedModeSize);
        useHls = cfg.hls;
        savedPlaybackUrl = cfg.url;
        com.google.android.exoplayer2.upstream.DataSource.Factory f = () -> new OkHttpExoDataSource(apiManager.getStreamClient());
        if (useHls) {
            player.setMediaSource(new com.google.android.exoplayer2.source.hls.HlsMediaSource.Factory(f).createMediaSource(MediaItem.fromUri(cfg.url)));
            Log.d(TAG, "播放器: HLS");
        } else {
            player.setMediaSource(new com.google.android.exoplayer2.source.ProgressiveMediaSource.Factory(f, new DefaultExtractorsFactory()).createMediaSource(MediaItem.fromUri(cfg.url)));
            Log.d(TAG, "播放器: 渐进式");
        }
        player.prepare(); player.setPlayWhenReady(true);
        Log.d(TAG, "startPlayback: parentGuid=" + parentGuid + " episodeLoaded=" + (episodeManager != null && episodeManager.isLoaded()) + " loadingEp=" + (episodeManager != null && episodeManager.isLoading()));
        if (parentGuid != null && !parentGuid.isEmpty() && episodeManager != null && !episodeManager.isLoaded() && !episodeManager.isLoading())
            episodeManager.loadList(parentGuid);
    }

    /** 直播频道播放（直接用 live_channels 返回的地址） */
    private void playLiveStream(String url) {
        if (player == null) return;
        savedPlaybackUrl = url;
        useHls = url.contains(".m3u8");
        com.google.android.exoplayer2.upstream.DataSource.Factory f = () -> new OkHttpExoDataSource(apiManager.getStreamClient());
        if (useHls) {
            player.setMediaSource(new com.google.android.exoplayer2.source.hls.HlsMediaSource.Factory(f).createMediaSource(MediaItem.fromUri(url)));
            Log.d(TAG, "直播: HLS");
        } else {
            player.setMediaSource(new com.google.android.exoplayer2.source.ProgressiveMediaSource.Factory(f, new DefaultExtractorsFactory()).createMediaSource(MediaItem.fromUri(url)));
            Log.d(TAG, "直播: 渐进式");
        }
        player.prepare();
        player.setPlayWhenReady(true);
    }

    /** 硬解失败后切到软解，重新创建播放器 */
    private void recreatePlayerWithSwDecoder() {
        if (player != null) {
            player.stop();
            player.release();
            player = null;
        }
        // 重新创建播放器（只允许 Google 软件解码器）
        DefaultRenderersFactory rf2 = new DefaultRenderersFactory(this) {
            @Override
            protected void buildVideoRenderers(Context context, int extensionRendererMode,
                                                com.google.android.exoplayer2.mediacodec.MediaCodecSelector mediaCodecSelector,
                                                boolean enableDecoderFallback, android.os.Handler eventHandler,
                                                com.google.android.exoplayer2.video.VideoRendererEventListener eventListener,
                                                long allowedVideoJoiningTimeMs, java.util.ArrayList<Renderer> out) {
                // 只保留 omx.google. 开头的软件解码器
                com.google.android.exoplayer2.mediacodec.MediaCodecSelector googleOnly = new com.google.android.exoplayer2.mediacodec.MediaCodecSelector() {
                    @Override
                    public java.util.List<com.google.android.exoplayer2.mediacodec.MediaCodecInfo> getDecoderInfos(
                            String mimeType, boolean requiresSecureDecoder, boolean requiresTunnelingDecoder)
                            throws com.google.android.exoplayer2.mediacodec.MediaCodecUtil.DecoderQueryException {
                        java.util.List<com.google.android.exoplayer2.mediacodec.MediaCodecInfo> all = com.google.android.exoplayer2.mediacodec.MediaCodecSelector.DEFAULT
                                .getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder);
                        java.util.List<com.google.android.exoplayer2.mediacodec.MediaCodecInfo> google = new java.util.ArrayList<>();
                        for (com.google.android.exoplayer2.mediacodec.MediaCodecInfo info : all) {
                            // omx.google.* = 旧版 Google 软解, c2.android.* = 新版 Google 软解
                            if (info.name.startsWith("omx.google.") || info.name.startsWith("c2.android.")) {
                                google.add(info);
                            }
                        }
                        return !google.isEmpty() ? google : all;
                    }
                };
                super.buildVideoRenderers(context, extensionRendererMode, googleOnly, enableDecoderFallback,
                        eventHandler, eventListener, allowedVideoJoiningTimeMs, out);
            }
        };
        rf2.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER);
        player = new SimpleExoPlayer.Builder(this, rf2)
                .setTrackSelector(new DefaultTrackSelector(this)).build();
        playerView.setPlayer(player);
        playerView.setUseController(false);
        playerView.setKeepScreenOn(true);
        // 重新挂载事件监听（错误处理等）
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int s) {
                tvBuffering.setVisibility(s == Player.STATE_BUFFERING ? View.VISIBLE : View.GONE);
                if (s == Player.STATE_READY) {
                    if (!seeked && seekTs > 0) { player.seekTo(seekTs); seeked = true; }
                    if (firstReady) { showCtrl(true); firstReady = false; }
                    btnPlayPause.setText(player.isPlaying() ? "暂停" : "播放");
                } else if (s == Player.STATE_ENDED) {
                    if (episodeManager != null && episodeManager.hasNext()) episodeManager.playNext();
                }
            }
            int retryCount = 0;
            private boolean swDecoderTried = false;
            @Override public void onPlayerError(PlaybackException e) {
                StringBuilder sb2 = new StringBuilder("播放错误(软解): " + e.getMessage());
                Throwable tc = e;
                while (tc != null) {
                    sb2.append("\n  ").append(tc.getClass().getSimpleName()).append(": ").append(tc.getMessage());
                    tc = tc.getCause();
                }
                Log.e(TAG, sb2.toString());
            }
        });
        // 重放（直播和普通视频都用 savedPlaybackUrl）
        if (savedPlaybackUrl != null) {
            useHls = savedPlaybackUrl.contains(".m3u8");
            com.google.android.exoplayer2.upstream.DataSource.Factory f = () -> new OkHttpExoDataSource(apiManager.getStreamClient());
            if (useHls) {
                player.setMediaSource(new com.google.android.exoplayer2.source.hls.HlsMediaSource.Factory(f).createMediaSource(MediaItem.fromUri(savedPlaybackUrl)));
            } else {
                player.setMediaSource(new com.google.android.exoplayer2.source.ProgressiveMediaSource.Factory(f, new DefaultExtractorsFactory()).createMediaSource(MediaItem.fromUri(savedPlaybackUrl)));
            }
            player.prepare();
            player.setPlayWhenReady(true);
            Log.d(TAG, "已切 Google 软解重试: " + savedPlaybackUrl);
        }
    }

    // ========== 剧集移至 EpisodeManager ==========

    // ========== 控制 ==========

    private void togglePlay() {
        if (player == null) return;
        if (player.isPlaying()) {
            player.pause();
            btnPlayPause.setText("播放");
            if (danmuManager != null) danmuManager.onPlayerPause();
        } else {
            player.play();
            btnPlayPause.setText("暂停");
            updateTime();
            if (danmuManager != null) danmuManager.onPlayerReady();
        }
    }

    private void seekRel(int ms) {
        if (player == null) return;
        long p = Math.max(0, Math.min(player.getDuration(), player.getCurrentPosition() + ms));
        player.seekTo(p);
        if (danmuManager != null) danmuManager.onSeekTo(p);
    }

    private void cycleSpeed() {
        speedIdx = (speedIdx + 1) % speeds.length;
        float s = speeds[speedIdx];
        btnSpeed.setText((s == (int)s ? String.valueOf((int)s) : String.valueOf(s)) + "x");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) player.setPlaybackSpeed(s);
        if (danmuManager != null) danmuView.setPlaybackSpeed(s);
    }

    private void cycleRatio() {
        ratioIdx = (ratioIdx + 1) % RATIO_MODES.length;
        btnRatio.setText(RATIO_LABELS[ratioIdx]);
        if (playerView != null) {
            playerView.setResizeMode(RATIO_MODES[ratioIdx]);
            // 拉伸/缩放时把字幕上移，避免被裁切
            if (playerView.getSubtitleView() != null) {
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) playerView.getSubtitleView().getLayoutParams();
                if (lp != null) {
                    int bottom = RATIO_MODES[ratioIdx] == 0 ? 0 : (int)(55 * getResources().getDisplayMetrics().density);
                    if (lp.bottomMargin != bottom) {
                        lp.bottomMargin = bottom;
                        playerView.getSubtitleView().setLayoutParams(lp);
                    }
                }
            }
        }
    }

    private void checkHdr() {
        handler.postDelayed(() -> {
            if (player == null) return;
            Log.d(TAG, "HDR检查: isHdr=" + isHdrVideo()
                    + " streamVHdr=" + streamVHdr
                    + " color=" + streamVColor);
            // 统一用 applyHdrMode 处理开/关（切剧集、重缓冲时也会正确切换 colorMode）
            applyHdrMode();
            updateHdrButtonText();
        }, 1500);
    }

    private boolean deviceSupportsHdr() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Display.HdrCapabilities caps = getWindowManager()
                    .getDefaultDisplay().getHdrCapabilities();
            if (caps != null) {
                for (int type : caps.getSupportedHdrTypes()) {
                    if (type == Display.HdrCapabilities.HDR_TYPE_HDR10
                            || type == Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION) {
                        return true;
                    }
                }
            }
        }
        return false;
    }


    private void showIntroOutroDialog() {
        SharedPreferences p = getSharedPreferences("fntv_prefs", MODE_PRIVATE);
        String skipId = parentGuid != null && !parentGuid.isEmpty() ? parentGuid : itemTV;
        String key = "skip_" + skipId;
        int defIntro = p.getInt(key + "_intro", 0);
        int defOutro = p.getInt(key + "_outro", 0);

        final android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar);
        dialog.setContentView(R.layout.dialog_skip);
        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xDD1A1A1A));

        // 标题
        TextView tvTitle = dialog.findViewById(R.id.tv_skip_title);
        if (tvTitle != null) tvTitle.setText((itemTV != null ? itemTV : "当前视频") + " - 跳过设置");

        // 片头滑条
        final TextView introLabel = dialog.findViewById(R.id.dm_label);
        final SeekBar introSb = dialog.findViewById(R.id.dm_seekbar);
        // 片尾滑条（第二个 include 的 ID 是 dm_outro，里面的子控件 ID 相同）
        final TextView outroLabel = ((ViewGroup)dialog.findViewById(R.id.dm_outro)).findViewById(R.id.dm_label);
        final SeekBar outroSb = ((ViewGroup)dialog.findViewById(R.id.dm_outro)).findViewById(R.id.dm_seekbar);

        if (introLabel != null) introLabel.setText("跳过片头: " + defIntro + "秒");
        if (outroLabel != null) outroLabel.setText("跳过片尾: " + defOutro + "秒");

        if (introSb != null) {
            introSb.setMax(600);
            introSb.setProgress(defIntro);
            introSb.setKeyProgressIncrement(1);
            introSb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar sb, int v, boolean u) {
                    if (introLabel != null) introLabel.setText("跳过片头: " + v + "秒");
                }
                @Override public void onStartTrackingTouch(SeekBar s) {}
                @Override public void onStopTrackingTouch(SeekBar s) {}
            });
        }
        if (outroSb != null) {
            outroSb.setMax(600);
            outroSb.setProgress(defOutro);
            outroSb.setKeyProgressIncrement(1);
            outroSb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar sb, int v, boolean u) {
                    if (outroLabel != null) outroLabel.setText("跳过片尾: " + v + "秒");
                }
                @Override public void onStartTrackingTouch(SeekBar s) {}
                @Override public void onStopTrackingTouch(SeekBar s) {}
            });
        }

        Button reset = dialog.findViewById(R.id.dm_reset);
        Button cancel = dialog.findViewById(R.id.dm_cancel);
        Button ok = dialog.findViewById(R.id.dm_ok);

        if (reset != null) reset.setOnClickListener(v -> { if (introSb != null) introSb.setProgress(0); if (outroSb != null) outroSb.setProgress(0); });
        if (cancel != null) cancel.setOnClickListener(v -> dialog.dismiss());
        if (ok != null) ok.setOnClickListener(v -> {
            if (introSb != null) p.edit().putInt(key + "_intro", introSb.getProgress()).apply();
            if (outroSb != null) p.edit().putInt(key + "_outro", outroSb.getProgress()).apply();
            dialog.dismiss();
        });
        dialog.show();
    }

    private void switchMediaSource(boolean toHls) {
        if (player == null || !cloudStreamManager.hasDirectUrl()) return;
        handler.post(() -> {
            String u = cloudStreamManager.getCloudDirectUrl();
            com.google.android.exoplayer2.upstream.DataSource.Factory f2 = () -> new OkHttpExoDataSource(apiManager.getStreamClient());
            com.google.android.exoplayer2.source.MediaSource ms = toHls
                    ? new com.google.android.exoplayer2.source.hls.HlsMediaSource.Factory(f2).createMediaSource(MediaItem.fromUri(u))
                    : new com.google.android.exoplayer2.source.ProgressiveMediaSource.Factory(f2, new DefaultExtractorsFactory()).createMediaSource(MediaItem.fromUri(u));
            player.stop();
            player.setMediaSource(ms);
            player.prepare();
            player.setPlayWhenReady(true);
        });
    }

    private void showBrightnessDialog() {
        SharedPreferences p = getSharedPreferences("fntv_prefs", MODE_PRIVATE);
        int brightness = p.getInt("video_brightness", 100);
        if (brightness > 100) brightness = 100;
        final android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar);
        dialog.setContentView(R.layout.dialog_brightness);
        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xDD1A1A1A));

        final TextView label = dialog.findViewById(R.id.dm_label);
        final SeekBar sb = dialog.findViewById(R.id.dm_seekbar);
        final Button cancel = dialog.findViewById(R.id.dm_cancel);
        final Button ok = dialog.findViewById(R.id.dm_ok);
        final Button reset = dialog.findViewById(R.id.dm_reset);

        if (label != null) label.setText("亮度: " + (brightness - 100) + "%");
        if (sb != null) {
            sb.setMax(200);
            sb.setProgress(brightness);
            sb.setKeyProgressIncrement(5);
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar seek, int val, boolean fromUser) {
                    int adj = val - 100;
                    if (label != null) label.setText("亮度: " + (adj > 0 ? "+" : "") + adj + "%");
                    if (fromUser) applyBrightness(val);
                }
                @Override public void onStartTrackingTouch(SeekBar s) {}
                @Override public void onStopTrackingTouch(SeekBar s) {}
            });
        }
        if (reset != null) reset.setOnClickListener(v -> { if (sb != null) { sb.setProgress(100); applyBrightness(100); if (label != null) label.setText("亮度: 0%"); } });
        if (cancel != null) cancel.setOnClickListener(v -> dialog.dismiss());
        if (ok != null) ok.setOnClickListener(v -> {
            if (sb != null) p.edit().putInt("video_brightness", sb.getProgress()).apply();
            dialog.dismiss();
        });
        dialog.show();
    }

    /** 切换 HDR 开关 */
    private void toggleHdr() {
        SharedPreferences prefs = getSharedPreferences("fntv_prefs", MODE_PRIVATE);
        boolean wasEnabled = prefs.getBoolean("hdr_enabled", false);
        prefs.edit().putBoolean("hdr_enabled", !wasEnabled).apply();
        applyHdrMode();
        updateHdrButtonText();
    }

    /** 更新 HDR 按钮文字 */
    private void updateHdrButtonText() {
        if (btnHdrToggle == null) return;
        boolean enabled = getSharedPreferences("fntv_prefs", MODE_PRIVATE).getBoolean("hdr_enabled", false);
        boolean videoHdr = isHdrVideo();
        if (videoHdr) {
            btnHdrToggle.setText(enabled ? "HDR:开" : "HDR:关");
            btnHdrToggle.setTextColor(enabled ? 0xFF81C784 : 0xFFE57373);
        } else {
            btnHdrToggle.setText("HDR");
            btnHdrToggle.setTextColor(0xFF808080);
        }
    }

    private void applyHdrMode() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        boolean enabled = getSharedPreferences("fntv_prefs", MODE_PRIVATE).getBoolean("hdr_enabled", false);
        boolean videoHdr = isHdrVideo();
        Log.d(TAG, "applyHdrMode: enabled=" + enabled + " videoHdr=" + videoHdr);
        if (enabled && videoHdr) {
            getWindow().setColorMode(ActivityInfo.COLOR_MODE_HDR);
            danmuManager.showDanmuStatus("HDR 已开启");
        } else {
            getWindow().setColorMode(0);
            if (videoHdr) danmuManager.showDanmuStatus("HDR 已关闭");
        }
    }

    private boolean isHdrVideo() {
        Format vf = player != null ? player.getVideoFormat() : null;
        if (vf != null && vf.colorInfo != null) {
            int cs = vf.colorInfo.colorSpace;
            int ct = vf.colorInfo.colorTransfer;
            if (cs >= 6 && (ct == 7 || ct == 16 || ct == 18)) return true;
        }
        // streamVHdr（杜比视界）→ 仅在设备支持 Dolby Vision 时算 HDR
        if (streamVHdr) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                android.view.Display.HdrCapabilities caps = getWindowManager()
                        .getDefaultDisplay().getHdrCapabilities();
                if (caps != null) {
                    for (int type : caps.getSupportedHdrTypes()) {
                        if (type == android.view.Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION) return true;
                    }
                }
            }
            return false;
        }
        return !streamVColor.isEmpty() && (streamVColor.contains("bt2020") || streamVColor.contains("2020"));
    }

    /** 调节屏幕亮度（仅当前 Activity），val 0~200，100=系统默认 */
    private void applyBrightness(int val) {
        android.view.WindowManager.LayoutParams lp = getWindow().getAttributes();
        if (val == 100) {
            lp.screenBrightness = -1f; // 恢复系统默认
        } else {
            float f = val / 100f;
            f = Math.max(0.01f, Math.min(1.0f, f));
            lp.screenBrightness = f;
        }
        getWindow().setAttributes(lp);
    }

    private void toggleInfo() {
        infoVis = !infoVis;
        infoPanel.setVisibility(infoVis ? View.VISIBLE : View.GONE);
        if (infoVis) {
            // 信息面板打开时，禁止焦点跳到其他控件
            ((ViewGroup) controller).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            ((ViewGroup) topBar).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            // 信息面板内焦点全方向循环（防止方向键逃出面板）
            View btnAudioTrack = findViewById(R.id.btnAudioTrack);
            View btnSubtitleTrack = findViewById(R.id.btnSubtitleTrack);
            View btnHdr = findViewById(R.id.btnHdrToggle);
            if (btnAudioTrack != null) {
                btnAudioTrack.setNextFocusUpId(btnCloseInfo.getId());
                btnAudioTrack.setNextFocusLeftId(btnCloseInfo.getId());
                btnAudioTrack.setNextFocusRightId(btnSubtitleTrack != null ? btnSubtitleTrack.getId() : (btnHdr != null ? btnHdr.getId() : btnCloseInfo.getId()));
            }
            if (btnSubtitleTrack != null) {
                btnSubtitleTrack.setNextFocusUpId(btnCloseInfo.getId());
                btnSubtitleTrack.setNextFocusLeftId(btnAudioTrack != null ? btnAudioTrack.getId() : btnCloseInfo.getId());
                btnSubtitleTrack.setNextFocusRightId(btnHdr != null ? btnHdr.getId() : btnCloseInfo.getId());
            }
            if (btnHdr != null) {
                btnHdr.setNextFocusUpId(btnCloseInfo.getId());
                btnHdr.setNextFocusLeftId(btnSubtitleTrack != null ? btnSubtitleTrack.getId() : (btnAudioTrack != null ? btnAudioTrack.getId() : btnCloseInfo.getId()));
                btnHdr.setNextFocusRightId(btnCloseInfo.getId());
                btnHdr.setNextFocusDownId(btnCloseInfo.getId());
            }
            int closeDown = btnAudioTrack != null ? btnAudioTrack.getId()
                    : (btnSubtitleTrack != null ? btnSubtitleTrack.getId()
                    : (btnHdr != null ? btnHdr.getId() : btnCloseInfo.getId()));
            btnCloseInfo.setNextFocusDownId(closeDown);
            btnCloseInfo.setNextFocusLeftId(btnHdr != null ? btnHdr.getId()
                    : (btnSubtitleTrack != null ? btnSubtitleTrack.getId()
                    : (btnAudioTrack != null ? btnAudioTrack.getId() : btnCloseInfo.getId())));
            btnCloseInfo.setNextFocusRightId(btnAudioTrack != null ? btnAudioTrack.getId()
                    : (btnSubtitleTrack != null ? btnSubtitleTrack.getId()
                    : (btnHdr != null ? btnHdr.getId() : btnCloseInfo.getId())));
            updateInfo();
            btnCloseInfo.post(() -> btnCloseInfo.requestFocus());
        } else {
            // 关闭时恢复焦点导航
            ((ViewGroup) controller).setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            ((ViewGroup) topBar).setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            // 确保控制栏可见，否则焦点无法设置到按钮上
            showCtrl(true);
            btnInfo.requestFocus();
        }
    }

    private void updateTitle() {
        int epNum = getIntent().getIntExtra("episode_number", 0);
        String epName = itemTitle != null ? itemTitle : "";
        StringBuilder sb = new StringBuilder();
        if (itemTV != null && !itemTV.isEmpty()) {
            sb.append(itemTV);
            if (epNum > 0) sb.append(" 第").append(epNum).append("集");
            if (epName != null && !epName.isEmpty() && !epName.equals(itemTV)) {
                sb.append(" ").append(epName);
            }
        } else {
            sb.append(epName);
        }
        tvTitle.setText(sb.toString().trim());
    }

    private void showCtrl(boolean show) {
        if (show && isLocked) {
            btnLock.setVisibility(View.VISIBLE);
            return;
        }
        ctrlVis = show;
        controller.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
        topBar.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
        btnLock.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
        btnDanmu.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
        if (show) {
            updateTitle();
            resetHideTimer();
        }
        else hideSystemUi();
    }
    private void resetHideTimer() {
        handler.removeCallbacks(hideC);
        handler.postDelayed(hideC, 5000);
    }
    private final Runnable hideC = () -> {
        // 焦点在控制器按钮上时推迟隐藏，infoPanel/顶栏/无焦点时正常隐藏
        if (controller.hasFocus() || btnDanmu.hasFocus() || btnLock.hasFocus()
                || btnCloudMode.hasFocus() || btnBrightness.hasFocus() || btnSkip.hasFocus()
                || btnInfo.hasFocus() || btnBack.hasFocus()
                || (btnHdrToggle != null && btnHdrToggle.hasFocus())
                || (btnQuality != null && btnQuality.hasFocus())) {
            resetHideTimer();
            return;
        }
        showCtrl(false);
    };

    private void setupFocusAutoHide() {
        View.OnFocusChangeListener l = (v, hasFocus) -> {
            if (hasFocus) resetHideTimer();
        };
        btnPlayPause.setOnFocusChangeListener(l);
        btnRewind.setOnFocusChangeListener(l);
        btnForward.setOnFocusChangeListener(l);
        btnSpeed.setOnFocusChangeListener(l);
        btnRatio.setOnFocusChangeListener(l);
        btnInfo.setOnFocusChangeListener(l);
        if (btnQuality != null) btnQuality.setOnFocusChangeListener(l);
        btnEpisodeList.setOnFocusChangeListener(l);
        btnNextEp.setOnFocusChangeListener(l);
        btnBack.setOnFocusChangeListener(l);
        btnDanmu.setOnFocusChangeListener(l);
        btnLock.setOnFocusChangeListener(l);
        btnCloudMode.setOnFocusChangeListener(l);
        if (btnBrightness != null) btnBrightness.setOnFocusChangeListener(l);
        if (btnSkip != null) btnSkip.setOnFocusChangeListener(l);
        View btnAudioTrack = findViewById(R.id.btnAudioTrack);
        View btnSubtitleTrack = findViewById(R.id.btnSubtitleTrack);
        if (btnAudioTrack != null) btnAudioTrack.setOnFocusChangeListener(l);
        if (btnSubtitleTrack != null) btnSubtitleTrack.setOnFocusChangeListener(l);
        if (btnHdrToggle != null) btnHdrToggle.setOnFocusChangeListener(l);
        btnCloseInfo.setOnFocusChangeListener(l);
        infoPanel.setOnFocusChangeListener(l);
    };

    private void updateTime() {
        if (player == null) return;
        long cur = player.getCurrentPosition(), dur = player.getDuration();
        seekBar.setMax((int) Math.max(dur, 1));
        seekBar.setKeyProgressIncrement(5000); // 方向键每次 5 秒
        // 防抖期间不覆盖 UI，避免抽搐（tvTime 和 seekBar 进度由 onProgressChanged 控制）
        if (pendingSeekMs < 0) {
            tvTime.setText(FormatUtils.fmt(cur) + " / " + FormatUtils.fmt(dur));
            seekBar.setProgress((int) cur);
        }
        if (danmuManager != null) danmuManager.setPlayTime(cur);
        // 实时监测片尾位置
        if (!outroSkipped && dur > 0) {
            SharedPreferences sp = getSharedPreferences("fntv_prefs", MODE_PRIVATE);
            String sid = parentGuid != null && !parentGuid.isEmpty() ? parentGuid : (itemTV != null ? itemTV : null);
            if (sid != null) {
                int outroSec = sp.getInt("skip_" + sid + "_outro", 0);
                if (outroSec > 0) Log.d(TAG, "片尾检测: cur=" + (cur/1000) + "s dur=" + (dur/1000) + "s 阈值=" + (dur/1000 - outroSec) + "s");
                if (outroSec > 0 && cur / 1000 > dur / 1000 - outroSec) {
                outroSkipped = true;
                danmuManager.showDanmuStatus("检测到片尾");
                if (episodeManager != null && episodeManager.hasNext())
                    handler.postDelayed(() -> episodeManager.playNext(), 1000);
                }
            }
        }
        handler.postDelayed(timeR, 200);
    }
    private final Runnable timeR = () -> { if (player != null && player.isPlaying()) updateTime(); };

    private void probeWithMediaExtractor() {
        if (mediaGuid == null || baseUrl == null) return;
        final String url = baseUrl + "/v/api/v1/media/range/" + mediaGuid;
        new Thread(() -> {
            try {
                android.media.MediaExtractor ex = new android.media.MediaExtractor();
                try {
                    ex.setDataSource(url);
                    for (int i = 0; i < ex.getTrackCount(); i++) {
                        android.media.MediaFormat mf = ex.getTrackFormat(i);
                        String mime = mf.getString(android.media.MediaFormat.KEY_MIME);
                        if (mime == null) continue;
                        if (mime.startsWith("video/")) {
                            if (streamVWidth <= 0) streamVWidth = mf.containsKey(android.media.MediaFormat.KEY_WIDTH) ? mf.getInteger(android.media.MediaFormat.KEY_WIDTH) : 0;
                            if (streamVHeight <= 0) streamVHeight = mf.containsKey(android.media.MediaFormat.KEY_HEIGHT) ? mf.getInteger(android.media.MediaFormat.KEY_HEIGHT) : 0;
                            if (streamBitrate <= 0) streamBitrate = mf.containsKey(android.media.MediaFormat.KEY_BIT_RATE) ? mf.getInteger(android.media.MediaFormat.KEY_BIT_RATE) : 0;
                            if (streamVCodec.isEmpty()) streamVCodec = mime.replace("video/", "");
                        }
                    }
                } finally { ex.release(); }
            } catch (Exception e) {
                Log.w(TAG, "MediaExtractor 失败: " + e.getMessage());
            }
        }).start();
    }

    private void updateInfo() {
        if (player == null) return;
        Format vf = player.getVideoFormat();
        Format af = player.getAudioFormat();

        // 视频（左列）
        StringBuilder v = new StringBuilder();
        v.append("── 视频 ──\n");
        String codec = FormatUtils.fmtVideoCodec(streamVCodec.isEmpty() ? (vf != null ? vf.codecs : null) : streamVCodec);
        v.append("编码 ").append(codec).append("\n");
        // 优先用 ExoPlayer 实际解码的格式（切换画质后自动更新）
        int w = vf != null && vf.width > 0 ? vf.width : streamVWidth;
        int h = vf != null && vf.height > 0 ? vf.height : streamVHeight;
        if (w > 0 && h > 0) v.append("分辨率 ").append(w).append("×").append(h).append("\n");
        float fps = 0;
        if (!streamVFps.isEmpty()) { try { fps = Float.parseFloat(streamVFps.replaceAll("[^0-9.]", "")); } catch (Exception ignored) {} }
        if (fps <= 0 && vf != null) fps = vf.frameRate;
        if (fps > 0) v.append("帧率 ").append(String.format("%.3f fps", fps)).append("\n");
        if (vf != null && vf.bitrate > 0) v.append("码率 ").append(FormatUtils.formatBitrate(vf.bitrate)).append("\n");
        else if (streamBitrate > 0) v.append("码率 ").append(FormatUtils.formatBitrate(streamBitrate)).append("\n");
        if (streamVBitDepth > 0) v.append("色深 ").append(streamVBitDepth).append("bit\n");
        if (streamVHdr || (vf != null && vf.colorInfo != null)) v.append("HDR10\n");
        v.append("解码 ").append(actualVideoDecoder.isEmpty() ? (isHwDecode ? "硬解" : "软解") : actualVideoDecoder);
        infoText.setText(v.toString());

        // 音频（右列）
        StringBuilder a = new StringBuilder();
        a.append("── 音频 ──\n");
        if (af != null) {
            String ac = FormatUtils.fmtAudioCodec(af.codecs != null ? af.codecs : af.sampleMimeType);
            a.append("编码 ").append(ac).append("\n");
            int ch = af.channelCount;
            a.append("声道 ").append(ch > 0 ? (ch == 8 ? "7.1" : ch == 6 ? "5.1" : ch + "ch") : "?").append("\n");
            a.append("采样 ").append(af.sampleRate > 0 ? af.sampleRate + "Hz" : "?").append("\n");
            if (af.bitrate > 0) a.append("码率 ").append(af.bitrate/1000).append("kbps\n");
            a.append("解码 ").append(actualAudioDecoder.isEmpty() ? (isHwDecode ? "硬解" : "软解") : actualAudioDecoder);
            // 显示用户选择的音轨（如有）
            String selAudio = cloudStreamManager != null ? cloudStreamManager.getLastAudioTrackLabel() : "";
            if (!selAudio.isEmpty() && !selAudio.equals("默认")) {
                a.append("\n已选 ").append(selAudio);
            }
        } else {
            a.append("无音轨\n");
        }
        if (infoTextAudio != null) infoTextAudio.setText(a.toString());

        // 额外信息（字幕、音轨、时长）
        StringBuilder x = new StringBuilder();
        // 额外音轨
        if (streamAudioTracks != null && streamAudioTracks.size() > 1) {
            for (int i = 1; i < streamAudioTracks.size(); i++) {
                StreamResponse.AudioStreamInfo asi = streamAudioTracks.get(i);
                String an = FormatUtils.fmtAudioCodec(asi.codecName);
                String al = asi.language != null && !asi.language.isEmpty() ? asi.language : "";
                String ach = asi.channels > 0 ? (asi.channels == 8 ? "7.1" : asi.channels == 6 ? "5.1" : asi.channels + "ch") : "?";
                String ab = asi.bps > 0 ? " " + FormatUtils.formatBitrate(asi.bps) : "";
                x.append("音轨").append(i + 1).append(" ").append(an);
                if (!al.isEmpty()) x.append(" ").append(al);
                x.append(" ").append(ach).append(ab).append("  ");
            }
        }
        // 字幕
        if (streamSubtitleTracks != null && !streamSubtitleTracks.isEmpty()) {
            if (x.length() > 0) x.append("\n");
            x.append("字幕 ");
            for (int i = 0; i < streamSubtitleTracks.size(); i++) {
                StreamResponse.SubtitleStreamInfo sub = streamSubtitleTracks.get(i);
                if (i > 0) x.append("  ");
                String sf = sub.codecName != null ? sub.codecName.toUpperCase() : "?";
                String lang = sub.language != null && !sub.language.isEmpty() ? sub.language : "?";
                String def = sub.isDefault != 0 ? "[默认]" : "";
                x.append(sf).append(" ").append(lang).append(def);
            }
        }
        // 时长
        long durMs = player.getDuration();
        if (durMs > 0) {
            if (x.length() > 0) x.append("\n");
            x.append("时长 ").append(FormatUtils.fmtTime((int)(durMs/1000)));
        }
        if (infoTextExtra != null) infoTextExtra.setText(x.toString());
    }

    // ========== 弹幕全部移至 DanmuManager ==========

    // ========== 进度保存 ==========

    private void startSave() { handler.removeCallbacks(saveR); handler.postDelayed(saveR, 10000); }
    private void stopSave() { handler.removeCallbacks(saveR); }
    private final Runnable saveR = new Runnable() {
        @Override public void run() { saveProgress(); handler.postDelayed(this, 15000); }
    };

    private void saveProgress() {
        if (player == null || player.getPlaybackState() != Player.STATE_READY) return;
        long p = player.getCurrentPosition(); if (p <= 0) return;
        long ts = p / 1000;
        Map<String, Object> r = new HashMap<>();
        r.put("item_guid", itemGuid); r.put("media_guid", mediaGuid);
        r.put("video_guid", videoGuid != null ? videoGuid : "");
        r.put("audio_guid", audioGuid != null ? audioGuid : "");
        r.put("subtitle_guid", subtitleGuid != null ? subtitleGuid : "_no_display_");
        // 非原画模式：用切换后的分辨率和码率，并记录 play_link
        if (customQualityBitrate > 0 && !customQualityRes.isEmpty()) {
            r.put("resolution", customQualityRes);
            r.put("bitrate", customQualityBitrate);
            if (!customPlayLink.isEmpty()) r.put("play_link", customPlayLink);
        } else {
            r.put("resolution", !streamResolution.isEmpty() ? streamResolution : (resolution != null ? resolution : ""));
            r.put("bitrate", streamBitrate);
        }
        r.put("ts", ts); r.put("duration", itemDuration > 0 ? itemDuration : player.getDuration()/1000);
        apiManager.setReferer(baseUrl + "/v/video/" + itemGuid + "?media_guid=" + mediaGuid);
        Log.d(TAG, "recordPlayStatus 请求: " + (r != null ? new com.google.gson.Gson().toJson(r) : "null"));
        apiManager.getApi().recordPlayStatus(r).enqueue(new retrofit2.Callback<ApiResponse<Object>>() {
            @Override public void onResponse(retrofit2.Call<ApiResponse<Object>> call, retrofit2.Response<ApiResponse<Object>> response) {
                String respBody = response.body() != null
                        ? "code=" + response.body().code + " msg='" + response.body().msg + "' data=" + response.body().data
                        : "nullBody";
                Log.d(TAG, "recordPlayStatus 响应: HTTP " + response.code() + " " + respBody
                        + " (raw: " + (response.body() != null ? new com.google.gson.Gson().toJson(response.body()) : "null") + ")");
            }
            @Override public void onFailure(retrofit2.Call<ApiResponse<Object>> call, Throwable t) {
                Log.e(TAG, "recordPlayStatus 失败: " + t.getMessage());
            }
        });
    }

    // ========== 按键 ==========

    @Override public boolean onKeyDown(int k, KeyEvent e) {
        if (isLocked) {
            if (k == KeyEvent.KEYCODE_BACK) {
                if (btnLock.hasFocus() || controller.hasFocus()) {
                    controller.clearFocus();
                    btnLock.clearFocus();
                    return true;
                }
                isLocked = false;
                btnLock.setImageResource(R.drawable.ic_unlock);
                showCtrl(true);
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) {
                isLocked = false;
                btnLock.setImageResource(R.drawable.ic_unlock);
                showCtrl(true);
                return true;
            }
            return true;
        }
        if (ctrlVis) {
            switch (k) {
                case KeyEvent.KEYCODE_BACK:
                    if (infoVis) { toggleInfo(); return true; }
                    // 有控件焦点 → 清掉，自动回退到 playerView
                    if (controller.hasFocus() || btnDanmu.hasFocus() || btnLock.hasFocus() || btnCloudMode.hasFocus() || btnBrightness.hasFocus() || btnSkip.hasFocus() || topBar.hasFocus() || btnBack.hasFocus()) {
                        topBar.clearFocus();
                        controller.clearFocus();
                        btnDanmu.clearFocus();
                        btnLock.clearFocus();
                        return true;
                    }
                    // 无按钮焦点（playerView 或其它）→ 收起控制栏
                    showCtrl(false);
                    return true;
                // LEFT/RIGHT 由 SeekBar 自身处理（已设 keyProgressIncrement=5000）
                case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
                    if (seekBar.hasFocus() || btnRewind.hasFocus() || btnForward.hasFocus()
                            || btnSpeed.hasFocus() || btnRatio.hasFocus() || btnInfo.hasFocus()
                            || btnEpisodeList.hasFocus() || btnNextEp.hasFocus() || btnBrightness.hasFocus() || btnSkip.hasFocus()) {
                        return true;
                    }
                    togglePlay(); return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                    // 顶栏按上→收起，其余情况交给系统焦点导航
                    if (topBar.hasFocus() || btnCloudMode.hasFocus()) {
                        showCtrl(false);
                        return true;
                    }
                    return super.onKeyDown(k, e);
                case KeyEvent.KEYCODE_INFO: case KeyEvent.KEYCODE_MENU:
                    toggleInfo(); return true;
            }
            return super.onKeyDown(k, e);
        } else {
            switch (k) {
                case KeyEvent.KEYCODE_BACK:
                    if (backPressedTime + 2000 > System.currentTimeMillis()) {
                        restoreOrientation();
                        finish();
                    } else {
                        backPressedTime = System.currentTimeMillis();
                        Toast.makeText(this, "再按一次退出播放", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_DPAD_UP:
                    showCtrl(true);
                    btnPlayPause.requestFocus();
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT: {
                    long step = k == KeyEvent.KEYCODE_DPAD_LEFT ? -seekStep : seekStep;
                    long cur = pendingSeekMs >= 0 ? pendingSeekMs : (player != null ? player.getCurrentPosition() : 0);
                    long dur = player != null ? player.getDuration() : 0;
                    long target = Math.max(0, Math.min(dur, cur + step));
                    // 立即更新 UI
                    String timeText = FormatUtils.fmt(target) + " / " + FormatUtils.fmt(dur);
                    tvSeekOverlay.setText(timeText);
                    tvSeekOverlay.setVisibility(View.VISIBLE);
                    tvTime.setText(timeText);
                    handler.removeCallbacks(hideSeekOverlayR);
                    handler.postDelayed(hideSeekOverlayR, 2000);
                    // 防抖：真正 seek 延迟到停止操作后
                    pendingSeekMs = target;
                    if (seekCommitR != null) handler.removeCallbacks(seekCommitR);
                    seekCommitR = () -> {
                        if (player != null) {
                            player.seekTo(target);
                            if (danmuManager != null) danmuManager.onSeekTo(target);
                        }
                        pendingSeekMs = -1;
                    };
                    handler.postDelayed(seekCommitR, 1000);
                    return true;
                }
                case KeyEvent.KEYCODE_INFO: case KeyEvent.KEYCODE_MENU:
                    toggleInfo(); return true;
            }
            return super.onKeyDown(k, e);
        }
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private TextView tvSeekOverlay;
    private final Runnable hideSeekOverlayR = () -> { if (tvSeekOverlay != null) tvSeekOverlay.setVisibility(View.GONE); };


    /** 控制栏隐藏时显示进度时间浮层 */
    private void showSeekOverlay() {
        if (player == null) return;
        updateTime();
        tvSeekOverlay.setText(FormatUtils.fmt(player.getCurrentPosition()) + " / " + FormatUtils.fmt(player.getDuration()));
        tvSeekOverlay.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideSeekOverlayR);
        handler.postDelayed(hideSeekOverlayR, 2000);
    }

    private boolean isTvDevice() {
        android.app.UiModeManager uiModeManager = (android.app.UiModeManager) getSystemService(UI_MODE_SERVICE);
        return uiModeManager != null
                && uiModeManager.getCurrentModeType() == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION;
    }

    @Override
    public void finish() {
        // 退出时恢复系统亮度
        try {
            android.view.WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.screenBrightness = -1f; // 恢复系统默认
            getWindow().setAttributes(lp);
        } catch (Exception ignored) {}
        super.finish();
    }

    private void restoreOrientation() {
        if (isTvDevice()) {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        }
    }

    @Override protected void onPause() {
        super.onPause();
        restoreOrientation();
    }
    @Override protected void onStop() { super.onStop(); saveProgress(); if (player != null) player.setPlayWhenReady(false); }
    @Override protected void onDestroy() {
        saveProgress();
        super.onDestroy(); handler.removeCallbacksAndMessages(null);
        if (danmuManager != null) { danmuManager.destroy(); }
        if (player != null) { player.release(); player = null; }
    }
}


