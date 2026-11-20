package com.jalalidesk.jclockplus;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

final class SegmentDigits {
    private static final float DIGIT_WIDTH = 0.64f;
    private static final float GAP = 0.095f;
    private static final int[] DIGIT_MASK = {
        0x3f, 0x06, 0x5b, 0x4f, 0x66, 0x6d, 0x7d, 0x07, 0x7f, 0x6f
    };
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path[] segments = {
        polygon(.036f,.012f, .604f,.012f, .474f,.145f, .166f,.145f),
        polygon(.628f,.034f, .628f,.429f, .560f,.480f, .489f,.423f, .489f,.164f),
        polygon(.628f,.571f, .628f,.966f, .489f,.836f, .489f,.577f, .560f,.520f),
        polygon(.036f,.988f, .166f,.855f, .474f,.855f, .604f,.988f),
        polygon(.012f,.571f, .080f,.520f, .151f,.577f, .151f,.836f, .012f,.966f),
        polygon(.012f,.034f, .151f,.164f, .151f,.423f, .080f,.480f, .012f,.429f),
        polygon(.103f,.500f, .182f,.435f, .458f,.435f, .537f,.500f, .458f,.565f, .182f,.565f)
    };
    private final Path slash = polygon(.018f,.822f, .273f,.180f, .411f,.180f,
            .411f,.262f, .155f,.903f, .018f,.903f);

    float width(String value) {
        float width = 0f;
        for (int i = 0; i < value.length(); i++) {
            width += cellWidth(value.charAt(i));
        }
        return Math.max(0f, width + (value.length() - 1) * GAP);
    }

    void draw(Canvas canvas, String value, float centerX, float top, float height,
            float maxWidth, int color, boolean colonVisible) {
        float unitWidth = width(value);
        float xScale = Math.min(height, maxWidth / unitWidth);
        paint.setColor(color);
        canvas.save();
        canvas.translate(centerX - unitWidth * xScale / 2f, top);
        canvas.scale(xScale, height);
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character >= '0' && character <= '9') {
                int mask = DIGIT_MASK[character - '0'];
                for (int segment = 0; segment < segments.length; segment++) {
                    if ((mask & (1 << segment)) != 0) {
                        canvas.drawPath(segments[segment], paint);
                    }
                }
            } else if (character == ':' && colonVisible) {
                canvas.drawRect(.065f, .274f, .198f, .407f, paint);
                canvas.drawRect(.065f, .609f, .198f, .742f, paint);
            } else if (character == '/') {
                canvas.drawPath(slash, paint);
            } else if (character == '-') {
                canvas.drawRect(.070f, .451f, .570f, .549f, paint);
            }
            canvas.translate(cellWidth(character) + GAP, 0f);
        }
        canvas.restore();
    }

    private static float cellWidth(char character) {
        if (character == ':') {
            return .263f;
        }
        if (character == '/') {
            return .429f;
        }
        return DIGIT_WIDTH;
    }

    private static Path polygon(float... vertices) {
        Path path = new Path();
        path.moveTo(vertices[0], vertices[1]);
        for (int i = 2; i < vertices.length; i += 2) {
            path.lineTo(vertices[i], vertices[i + 1]);
        }
        path.close();
        return path;
    }
}
