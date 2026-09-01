package com.fntv.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Choreographer;
import android.view.View;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 弹幕渲染层 — 滚动 + 顶部/底部固定弹幕，自适应刷新率 */
public class DanmuView extends View {
    private static final String TAG = "DanmuView";

    private final Paint paint;
    private final List<DanmuItem> items = new ArrayList<>();
    private float screenDensity;
    private boolean running = false;
    private long lastFrame;
    private volatile float playTime = 0;
    private int maxActive = 40;
    private float speedMul = 1f;
    private float opacity = 0.85f;
    private int areaPct = 35;
    private float fontSize = 22f;
    private boolean showOutline = true;
    private int densityPct = 100;
    private float rowSpacing = 1.8f;
    private boolean showScroll = true;
    private boolean showTop = true;
    private boolean showBottom = true;
    private int danmuOffset = 0;
    private final List<DanmuItem> activeScroll = new ArrayList<>();
    private final List<DanmuItem> activeStatic = new ArrayList<>();
    private DanmuItem pausedItem = null;
    private long pausedTime = 0;

    // 帧调度双模式兜底
    private final Handler frameHandler = new Handler(Looper.getMainLooper());
    private boolean useHandlerFallback = false;

    public DanmuView(Context c) { this(c, null); }
    public DanmuView(Context c, android.util.AttributeSet a) { this(c, a, 0); }
    public DanmuView(Context c, android.util.AttributeSet a, int defStyle) {
        super(c, a, defStyle);
        screenDensity = c.getResources().getDisplayMetrics().density;
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        updateStyle();
    }

    public void setMaxActive(int v) { maxActive = v; }
    public void setSpeedMul(float v) { speedMul = v; }
    private float playbackSpeed = 1f;
    public void setPlaybackSpeed(float speed) { playbackSpeed = speed; }
    private float getEffectiveSpeedMul() { return speedMul * Math.max(1f, playbackSpeed); }
    public void setOpacity(float v) { opacity = v; updateStyle(); }
    public void setAreaPct(int v) { areaPct = v; }
    public void setFontSize(float v) { fontSize = v; updateStyle(); }
    public void setShowOutline(boolean v) { showOutline = v; updateStyle(); }
    public void setDensityPct(int v) { densityPct = v; }
    public void setRowSpacing(float v) { rowSpacing = v; }
    public void setShowScroll(boolean v) { showScroll = v; }
    public void setShowTop(boolean v) { showTop = v; }
    public void setShowBottom(boolean v) { showBottom = v; }
    private int targetFps = 60;
    public void setTargetFps(int v) { targetFps = Math.max(1, v); }
    private boolean customFps = false;
    public void setCustomFps(boolean v) { customFps = v; }
    public void setDanmuOffset(int v) { danmuOffset = v; }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
            float tx = event.getX();
            float ty = event.getY();

            // 点击空白处恢复暂停的弹幕
            if (pausedItem != null) {
                pausedItem.paused = false;
                pausedItem = null;
                invalidate();
                return true;
            }

            // 检测点击了哪条弹幕（滚动弹幕）
            for (DanmuItem a : activeScroll) {
                float textH = fontSize * screenDensity;
                if (tx >= a.x && tx <= a.x + a.tw && ty >= a.y - textH && ty <= a.y) {
                    a.paused = true;
                    pausedItem = a;
                    pausedTime = System.currentTimeMillis();
                    invalidate();
                    return true;
                }
            }

            // 检测点击了哪条弹幕（固定弹幕）
            for (DanmuItem a : activeStatic) {
                float textH = fontSize * screenDensity;
                if (tx >= a.x && tx <= a.x + a.tw && ty >= a.y - textH && ty <= a.y) {
                    a.paused = true;
                    pausedItem = a;
                    pausedTime = System.currentTimeMillis();
                    invalidate();
                    return true;
                }
            }
        }
        // 不拦截触摸事件，让下层控件处理
        return false;
    }

    private void updateStyle() {
        paint.setTextSize(fontSize * screenDensity);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setStyle(Paint.Style.FILL);
    }

    public void setPlayTime(long ms) {
        playTime = ms / 1000f;
    }

    public void loadDanmu(List<DanmuComment> comments) {
        items.clear();
        activeScroll.clear();
        activeStatic.clear();
        eIdx = 0;
        if (comments == null) return;
        for (DanmuComment c : comments) {
            DanmuItem item = new DanmuItem();
            item.text = c.text;
            item.time = c.time;
            item.color = c.color != 0 ? c.color : 0xFFFFFFFF;
            item.type = c.type;
            item.fontSize = c.fontSize > 0 ? c.fontSize : fontSize;
            items.add(item);
        }
        Collections.sort(items, (a, b) -> Float.compare(a.time, b.time));
    }

    public void start() {
        if (running) return;
        running = true;
        lastFrame = System.nanoTime();
        if (customFps) {
            useHandlerFallback = true;
            frameHandler.post(handlerFrame);
        } else {
            useHandlerFallback = false;
            Choreographer.getInstance().postFrameCallback(frameCallback);
            frameHandler.postDelayed(refreshCheck, 2000);
        }
    }

    public void stop() {
        running = false;
        useHandlerFallback = false;
        Choreographer.getInstance().removeFrameCallback(frameCallback);
        frameHandler.removeCallbacks(handlerFrame);
        frameHandler.removeCallbacks(refreshCheck);
    }

    public void pause() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(frameCallback);
        frameHandler.removeCallbacks(handlerFrame);
        frameHandler.removeCallbacks(refreshCheck);
    }

    public void seekToTime(long ms) {
        playTime = ms / 1000f;
        activeScroll.clear();
        activeStatic.clear();
        int lo = 0, hi = items.size();
        while (lo < hi) {
            int mid = (lo + hi) >> 1;
            if (items.get(mid).time <= playTime) lo = mid + 1;
            else hi = mid;
        }
        eIdx = lo;
    }

    public void resume() {
        if (items.isEmpty()) return;
        running = true;
        lastFrame = System.nanoTime();
        if (customFps) {
            useHandlerFallback = true;
            frameHandler.post(handlerFrame);
        } else {
            useHandlerFallback = false;
            Choreographer.getInstance().postFrameCallback(frameCallback);
            frameHandler.postDelayed(refreshCheck, 2000);
        }
    }

    public void clear() {
        items.clear();
        activeScroll.clear();
        activeStatic.clear();
        eIdx = 0;
    }

    private static class DanmuItem {
        String text;
        float time;
        int color;
        int type;     // 1=滚动 4=底部 5=顶部
        float fontSize;
        float x, y, speed, tw;
        float ttl;
        boolean paused;
    }

    public static class DanmuComment {
        public String text;
        public float time;
        public int color = 0xFFFFFFFF;
        public int type = 1;
        public float fontSize;
    }

    private int eIdx = 0;

    // ── Choreographer 模式（正常刷新率 ≥ 48Hz）──
    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (!running || useHandlerFallback) return;
            tick();
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    // ── Handler 兜底模式（刷新率 < 48Hz 时自动切到这个）──
    private final Runnable handlerFrame = new Runnable() {
        @Override
        public void run() {
            if (!running || !useHandlerFallback) return;
            tick();
            frameHandler.postDelayed(this, 1000 / targetFps);  // 自定义刷新率
        }
    };

    // ── 每 2 秒检测一次屏幕刷新率，决定用哪种模式 ──
    private final Runnable refreshCheck = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            if (customFps) return;  // 自定义刷新率时不自动切换
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    float rate = getDisplay().getRefreshRate();
                    if (rate < 48f && !useHandlerFallback) {
                        // 屏幕降频了，切到 Handler 模式以自定义fps跑弹幕
                        useHandlerFallback = true;
                        Choreographer.getInstance().removeFrameCallback(frameCallback);
                        lastFrame = System.nanoTime();
                        frameHandler.post(handlerFrame);
                    } else if (rate >= 48f && useHandlerFallback) {
                        // 刷新率恢复，切回 Choreographer
                        useHandlerFallback = false;
                        frameHandler.removeCallbacks(handlerFrame);
                        lastFrame = System.nanoTime();
                        Choreographer.getInstance().postFrameCallback(frameCallback);
                    }
                } catch (Exception ignored) {}
            }
            frameHandler.postDelayed(this, 2000);
        }
    };

    // ── 每帧逻辑（Choreographer 和 Handler 都调这个）──
    private void tick() {
        long now = System.nanoTime();
        float dt = (now - lastFrame) / 1_000_000_000f;
        lastFrame = now;
        if (dt > 0.1f) dt = 0.016f;

        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        float areaH = h * areaPct / 100f;
        float lnH = fontSize * screenDensity * rowSpacing;
        int maxRow = Math.max(1, (int) (areaH / lnH));
        int maxRowBottom = Math.max(1, Math.min(3, (int) (h / lnH)));

        // ── 发射 ──
        while (eIdx < items.size()) {
            DanmuItem src = items.get(eIdx);
            float diff = playTime - src.time;

            if (diff > 1.5f) { eIdx++; continue; }
            if (diff < 0) break;
            // 发射错开
            if (diff < Math.random() * 0.3f) break;
            if (Math.random() * 100 >= densityPct) { eIdx++; continue; }
            if (activeScroll.size() + activeStatic.size() >= maxActive) break;

            // 按类型开关过滤
            if (src.type == 1 && !showScroll) { eIdx++; continue; }
            if (src.type == 5 && !showTop) { eIdx++; continue; }
            if (src.type == 4 && !showBottom) { eIdx++; continue; }

            boolean isStatic = (src.type == 4 || src.type == 5);

            if (isStatic) {
                // ── 固定弹幕（顶部/底部）──
                DanmuItem a = new DanmuItem();
                a.text = src.text;
                a.color = src.color;
                a.type = src.type;
                a.time = src.time;
                a.tw = paint.measureText(src.text);
                a.speed = 0;
                a.ttl = 5.0f;  // 显示 5 秒

                boolean isTop = (src.type == 5);
                float rowY;

                if (isTop) {
                    // 顶部弹幕：从上往下找，受显示区域限制
                    rowY = findStaticRowTop(lnH, maxRow);
                } else {
                    // 底部弹幕：从屏幕底部往上找，最多3行，不受显示区域限制
                    rowY = findStaticRowBottom(h, lnH, maxRowBottom);
                }

                if (rowY < 0) { eIdx++; continue; }  // 没位置，丢弃

                a.y = rowY;
                a.x = w / 2f - a.tw / 2f;  // 水平居中
                activeStatic.add(a);
                eIdx++;
            } else {
                // ── 滚动弹幕 ──
                DanmuItem a = new DanmuItem();
                a.text = src.text;
                a.color = src.color;
                a.type = src.type;
                a.time = src.time;
                a.tw = paint.measureText(src.text);

                // 速度基本匀速，长度影响很小
                int len = Math.max(1, src.text.length());
                float baseSpeed = 250 + len * 5;
                a.speed = baseSpeed * getEffectiveSpeedMul();

                float rowY = findScrollRow(a.tw, w, lnH, maxRow);
                if (rowY < 0) { eIdx++; continue; }

                a.y = rowY;
                a.x = w + 5;
                activeScroll.add(a);
                eIdx++;
            }
        }

        // ── 更新滚动弹幕位置 ──
        List<DanmuItem> deadScroll = new ArrayList<>();
        for (DanmuItem a : activeScroll) {
            if (!a.paused) {
                a.x -= a.speed * dt;
            }
            if (a.x + a.tw < -100) deadScroll.add(a);
        }
        activeScroll.removeAll(deadScroll);

        // ── 更新固定弹幕 TTL ──
        List<DanmuItem> deadStatic = new ArrayList<>();
        for (DanmuItem a : activeStatic) {
            a.ttl -= dt;
            if (a.ttl <= 0) deadStatic.add(a);
        }
        activeStatic.removeAll(deadStatic);

        invalidate();
    }

    /**
     * 滚动弹幕行避让：随机间隔（20dp ~ 60dp）
     */
    private float findScrollRow(float newTw, int screenW, float lnH, int maxRow) {
        float gap = (20f + (float)(Math.random() * 40)) * screenDensity;

        for (int r = 0; r < maxRow; r++) {
            float rowY = lnH + r * lnH;
            boolean blocked = false;

            for (DanmuItem a : activeScroll) {
                if (Math.abs(a.y - rowY) < lnH * 0.5f) {
                    if (a.x + a.tw + gap > screenW) {
                        blocked = true;
                        break;
                    }
                }
            }
            if (!blocked) return rowY;
        }
        return -1;
    }

    /**
     * 顶部固定弹幕：从上往下找空行，受显示区域限制
     */
    private float findStaticRowTop(float lnH, int maxRow) {
        for (int r = 0; r < maxRow; r++) {
            float rowY = lnH + r * lnH;
            boolean blocked = false;
            for (DanmuItem a : activeStatic) {
                if (Math.abs(a.y - rowY) < lnH * 0.5f) {
                    blocked = true;
                    break;
                }
            }
            if (!blocked) return rowY;
        }
        return -1;
    }

    /**
     * 底部固定弹幕：从屏幕底部往上找空行，最多3行，不受显示区域限制
     * row0 = 屏幕最底部, row1 = 往上一行, row2 = 再往上一行
     */
    private float findStaticRowBottom(int screenH, float lnH, int maxRow) {
        for (int attempt = 0; attempt < maxRow; attempt++) {
            float rowY = screenH - lnH * 0.2f - attempt * lnH;
            boolean blocked = false;
            for (DanmuItem a : activeStatic) {
                if (Math.abs(a.y - rowY) < lnH * 0.5f) {
                    blocked = true;
                    break;
                }
            }
            if (!blocked) return rowY;
        }
        return -1;
    }

    /** 绘制单条弹幕（描边 + 填充） */
    private void drawDanmu(Canvas c, DanmuItem a, float alphaMul) {
        int alpha = (int)(255 * opacity * alphaMul);
        int baseR = Color.red(a.color);
        int baseG = Color.green(a.color);
        int baseB = Color.blue(a.color);

        if (showOutline) {
            paint.setColor(Color.argb(alpha, 0, 0, 0));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(0.8f * screenDensity);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            c.drawText(a.text, a.x, a.y, paint);
        }
        paint.setColor(Color.argb(alpha, baseR, baseG, baseB));
        paint.setStyle(Paint.Style.FILL);
        c.drawText(a.text, a.x, a.y, paint);
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        paint.setTextSize(fontSize * screenDensity);

        // 底层：滚动弹幕
        for (DanmuItem a : activeScroll) {
            drawDanmu(c, a, a.paused ? 0.6f : 1f);
        }

        // 顶层：固定弹幕
        for (DanmuItem a : activeStatic) {
            drawDanmu(c, a, a.paused ? 0.6f : 1f);
        }

        // 绘制暂停弹幕的信息浮层
        if (pausedItem != null) {
            int sec = (int) pausedItem.time;
            int min = sec / 60;
            sec = sec % 60;
            String timeStr = String.format("%d:%02d", min, sec);

            String info;
            if (danmuOffset != 0) {
                // 显示原始时间和偏移后时间
                float origTime = pausedItem.time - danmuOffset;
                int origSec = (int) origTime;
                int origMin = origSec / 60;
                origSec = origSec % 60;
                String origTimeStr = String.format("%d:%02d", origMin, origSec);
                info = "原始: " + origTimeStr + "  偏移后: " + timeStr;
                info += "  (" + (danmuOffset > 0 ? "+" : "") + danmuOffset + "s)";
            } else {
                info = "时间: " + timeStr;
            }
            String typeStr = "";
            if (pausedItem.type == 5) typeStr = "顶部";
            else if (pausedItem.type == 4) typeStr = "底部";
            else typeStr = "滚动";
            info += "  [" + typeStr + "]";

            float textH = fontSize * screenDensity;
            float bgW = paint.measureText(info) + 24 * screenDensity;
            float bgH = textH + 16 * screenDensity;
            float bgX = pausedItem.x;
            float bgY = pausedItem.y - textH - 20 * screenDensity;
            if (bgX + bgW > getWidth()) bgX = getWidth() - bgW;
            if (bgX < 0) bgX = 0;
            if (bgY < 0) bgY = pausedItem.y + 8 * screenDensity;

            // 背景
            Paint bgPaint = new Paint();
            bgPaint.setColor(0xDD000000);
            c.drawRoundRect(bgX, bgY, bgX + bgW, bgY + bgH, 6 * screenDensity, 6 * screenDensity, bgPaint);

            // 文字
            Paint infoPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            infoPaint.setTextSize(14 * screenDensity);
            infoPaint.setColor(0xFFCCCCCC);
            c.drawText(info, bgX + 12 * screenDensity, bgY + textH + 2 * screenDensity, infoPaint);
        }
    }
}
