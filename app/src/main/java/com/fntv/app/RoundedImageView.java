package com.fntv.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatImageView;

public class RoundedImageView extends AppCompatImageView {
    private float radius = 10;
    private Path clipPath;
    private RectF rect;

    public RoundedImageView(Context c) { this(c, null); }
    public RoundedImageView(Context c, AttributeSet a) { this(c, a, 0); }
    public RoundedImageView(Context c, AttributeSet a, int defStyle) {
        super(c, a, defStyle);
        // 软件层裁剪圆角才有抗锯齿，硬件层会在角上留下黑边
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setCornerRadius(float dp) {
        radius = dp * getResources().getDisplayMetrics().density;
    }

    /** 直接按像素设置圆角，保证和描边框的圆角完全一致 */
    public void setCornerRadiusPx(float px) {
        radius = px;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        rect = new RectF(0, 0, w, h);
        clipPath = new Path();
        clipPath.addRoundRect(rect, radius, radius, Path.Direction.CW);
    }

    @Override
    public void draw(Canvas canvas) {
        if (clipPath != null) canvas.clipPath(clipPath);
        super.draw(canvas);
    }
}
