package com.fntv.app.ui.common;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

/**
 * 首页纵向列表：关闭 RecyclerView 自带的“焦点滚进视野”逻辑，
 * 改由 HomeActivity 统一处理。
 * 自带逻辑会在拿到焦点后立刻做一次平滑补偿滚动，把刚摆好的行标题又顶出屏幕。
 * （LayoutManager 那边对应的是 {@link HomeLayoutManager}。）
 */
public class HomeRecyclerView extends RecyclerView {

    public HomeRecyclerView(Context context) {
        super(context);
    }

    public HomeRecyclerView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public HomeRecyclerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public boolean requestChildRectangleOnScreen(View child, Rect rect, boolean immediate) {
        return false;
    }
}
