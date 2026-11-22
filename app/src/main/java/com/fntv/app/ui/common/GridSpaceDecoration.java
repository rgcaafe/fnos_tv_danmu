package com.fntv.app.ui.common;

import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * 网格间距。
 */
public class GridSpaceDecoration extends RecyclerView.ItemDecoration {

    private final int spacing;

    public GridSpaceDecoration(int spacingPx) {
        this.spacing = spacingPx;
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                               @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        int half = spacing / 2;
        outRect.left = half;
        outRect.right = half;
        outRect.bottom = spacing;
    }
}
