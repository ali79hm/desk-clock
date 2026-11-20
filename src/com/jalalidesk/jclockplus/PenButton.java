package com.jalalidesk.jclockplus;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

final class PenButton extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path icon = new Path();
    private boolean editing;

    PenButton(Context context) {
        super(context);
        paint.setStrokeWidth(1.8f);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStyle(Paint.Style.STROKE);
        setFocusable(true);
        setEditing(false);
    }

    void setEditing(boolean enabled) {
        editing = enabled;
        setContentDescription(enabled ? "Done editing" : "Edit layout");
        icon.reset();
        if (enabled) {
            icon.moveTo(4, 12);
            icon.lineTo(9, 17);
            icon.lineTo(20, 6);
        } else {
            icon.moveTo(4, 20);
            icon.lineTo(5, 15);
            icon.lineTo(16, 4);
            icon.lineTo(20, 8);
            icon.lineTo(9, 19);
            icon.close();
            icon.moveTo(13, 7);
            icon.lineTo(17, 11);
        }
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        float side = 24f * getResources().getDisplayMetrics().density;
        canvas.save();
        canvas.translate((getWidth() - side) / 2f, (getHeight() - side) / 2f);
        canvas.scale(side / 24f, side / 24f);
        paint.setColor(editing ? 0xffa4eee3 : 0xffa6afbb);
        canvas.drawPath(icon, paint);
        canvas.restore();
    }
}
