package com.fntv.app.ui.common;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;

import androidx.recyclerview.widget.RecyclerView;

/**
 * 横向列表专用：内容只会被裁到左内边距，不会滑进左侧图标栏那一溜。
 * 右侧仍允许溢出（保留卡片出血效果）。
 */
public class ClippedRecyclerView extends RecyclerView {

    private static final int FAR = 1 << 20;

    public ClippedRecyclerView(Context context) {
        super(context);
    }

    public ClippedRecyclerView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ClippedRecyclerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        int save = canvas.save();
        // 左侧多放 8dp：首张卡片选中放大后，描边不会被裁掉一半
        int room = Math.round(8 * getResources().getDisplayMetrics().density);
        canvas.clipRect(Math.max(0, getPaddingLeft() - room), 0,
                getWidth() + FAR, getHeight());
        super.dispatchDraw(canvas);
        canvas.restoreToCount(save);
    }
}
