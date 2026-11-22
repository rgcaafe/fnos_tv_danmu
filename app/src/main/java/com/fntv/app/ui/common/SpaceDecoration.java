package com.fntv.app.ui.common;

import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * 统一的卡片间距。
 */
public class SpaceDecoration extends RecyclerView.ItemDecoration {

    private final int spacing;

    public SpaceDecoration(int spacingPx) {
        this.spacing = spacingPx;
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                               @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        int position = parent.getChildAdapterPosition(view);
        if (position > 0) outRect.left = spacing;
    }
}
