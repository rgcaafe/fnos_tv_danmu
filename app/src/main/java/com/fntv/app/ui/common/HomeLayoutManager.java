package com.fntv.app.ui.common;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * 首页纵向列表的布局管理器：关闭 LayoutManager 自带的“焦点滚进视野”，
 * 由 HomeActivity 统一控制滚动，避免它在我们排好位置后又平滑滚一次。
 */
public class HomeLayoutManager extends LinearLayoutManager {

    public HomeLayoutManager(Context context) {
        super(context);
    }

    @Override
    public boolean requestChildRectangleOnScreen(RecyclerView parent, View child, Rect rect,
                                                 boolean immediate, boolean focusedChildVisible) {
        return false;
    }
}
