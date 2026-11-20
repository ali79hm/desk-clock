package com.jalalidesk.jclockplus;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;

final class ClockFace extends View {
    static final int TIME = 0;
    static final int DATE = 1;
    static final int DAY = 2;
    static final int DIVIDER = 3;
    static final int ITEM_COUNT = 4;
    static final float MIN_SIZE = 3f;
    static final float MAX_SIZE = 120f;
    private static final float SECONDS_SCALE = .43f;
    private static final float SECONDS_GAP = .22f;

    interface Actions {
        void revealControls();
        void savePosition(int index, float x, float y, float size);
    }

    private static final class Item {
        final RectF bounds = new RectF();
        float x;
        float y;
        float size;
        float effectiveSize;
        int color;
    }

    private final Item[] items = { new Item(), new Item(), new Item(), new Item() };
    private final SegmentDigits digits = new SegmentDigits();
    private final Paint dayPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint photoPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF photoBounds = new RectF();
    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Actions actions;
    private final float density;
    private final int slop;
    private String clock = "00:00";
    private String seconds = "00";
    private String date = "----/--/--";
    private String day = "";
    private boolean colon = true;
    private Bitmap weekday;
    private Bitmap background;
    private boolean shiftPhotoRight;
    private static final float BACKGROUND_SHIFT_DP = 8f;
    private static final float WEEKDAY_INK_HEIGHT = .62f;
    private boolean editing;
    private boolean dividerVisible;
    private float dividerThickness = 2f;
    private int selected = -1;
    private int active = -1;
    private float lastX;
    private float lastY;
    private float lastSpan;
    private boolean moved;

    ClockFace(Context context, Actions actions) {
        super(context);
        this.actions = actions;
        density = getResources().getDisplayMetrics().density;
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
        outline.setStyle(Paint.Style.STROKE);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setClickable(actions != null);
    }

    void setItem(int index, float x, float y, float size) {
        Item item = items[index];
        item.x = x;
        item.y = y;
        item.size = size;
        if (getWidth() > 0 && getHeight() > 0) {
            place(index);
        }
        invalidate();
    }

    void setEditing(boolean enabled) {
        finishGesture();
        editing = enabled;
        selected = -1;
        invalidate();
    }

    void setDivider(boolean visible, float thickness) {
        dividerVisible = visible;
        dividerThickness = thickness;
        if (getWidth() > 0 && getHeight() > 0) {
            place(DIVIDER);
        }
        invalidate();
    }

    void setBackgroundPhoto(Bitmap photo, int localHour) {
        boolean shiftRight = (localHour & 1) == 0;
        if (background != photo || (photo != null && shiftPhotoRight != shiftRight)) {
            background = photo;
            shiftPhotoRight = shiftRight;
            invalidate();
        }
    }

    void update(String clock, String seconds, String date, String day, Bitmap weekday,
            boolean colon, int[] colors) {
        // The seven weekday assets share a canvas fitted to the longest name.
        boolean layoutChanged = (weekday == null) != (this.weekday == null)
                || date.length() != this.date.length() || clock.length() != this.clock.length();
        this.clock = clock;
        this.seconds = seconds;
        this.date = date;
        this.day = day;
        this.weekday = weekday;
        this.colon = colon;
        if (dayPaint.getColorFilter() == null || items[DAY].color != colors[DAY]) {
            dayPaint.setColorFilter(new PorterDuffColorFilter(colors[DAY], PorterDuff.Mode.SRC_IN));
        }
        for (int i = 0; i < items.length; i++) {
            items[i].color = colors[i];
        }
        if (layoutChanged) {
            placeAll();
        }
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int oldWidth, int oldHeight) {
        finishGesture();
        placeAll();
    }

    private void placeAll() {
        if (getWidth() > 0 && getHeight() > 0) {
            for (int index = 0; index < items.length; index++) {
                place(index);
            }
        }
    }

    private void place(int index) {
        Item item = items[index];
        float screenWidth = getWidth();
        float screenHeight = getHeight();
        if (index == DIVIDER) {
            float width = item.size * screenWidth / 100f;
            float previewScale = actions != null ? 1f : screenHeight / getResources().getDisplayMetrics().heightPixels;
            float height = Math.min(screenHeight, Math.max(1f, dividerThickness * density * previewScale));
            setBounds(item, width, height);
            item.effectiveSize = item.size;
            return;
        }
        float unitWidth = index == TIME ? clockWidth() : digits.width(date);
        float unitHeight = 1f;
        if (index == DAY) {
            unitHeight = WEEKDAY_INK_HEIGHT;
            unitWidth = weekday == null ? 1f : unitHeight * weekday.getWidth() / weekday.getHeight();
        }
        float pixels = Math.min(item.size * screenHeight / 100f,
                Math.min(.96f * screenWidth / unitWidth, .90f * screenHeight / unitHeight));
        item.effectiveSize = pixels * 100f / screenHeight;
        float width = unitWidth * pixels;
        float height = unitHeight * pixels;
        setBounds(item, width, height);
    }

    private void setBounds(Item item, float width, float height) {
        float left = (getWidth() - width) * item.x / 100f;
        float top = (getHeight() - height) * item.y / 100f;
        item.bounds.set(left, top, left + width, top + height);
    }

    private float clockWidth() {
        return digits.width(clock) + SECONDS_GAP + SECONDS_SCALE * digits.width(seconds);
    }

    @Override protected void onDraw(Canvas canvas) {
        canvas.drawColor(0xff000000);
        if (background != null) {
            float referenceWidth = actions != null ? getWidth() : getResources().getDisplayMetrics().widthPixels;
            float shift = Math.min(BACKGROUND_SHIFT_DP * density * getWidth() / referenceWidth,
                    getWidth() * .025f);
            float scale = Math.max((getWidth() + 2f * shift) / background.getWidth(),
                    (float) getHeight() / background.getHeight());
            float width = background.getWidth() * scale;
            float height = background.getHeight() * scale;
            float left = (getWidth() - width) / 2f + (shiftPhotoRight ? shift : -shift);
            float top = (getHeight() - height) / 2f;
            photoBounds.set(left, top, left + width, top + height);
            canvas.drawBitmap(background, null, photoBounds, photoPaint);
            canvas.drawColor(0x38000000);
        }
        if (dividerVisible) {
            Item divider = items[DIVIDER];
            dividerPaint.setColor(divider.color);
            canvas.drawRect(divider.bounds, dividerPaint);
        }
        Item time = items[TIME];
        RectF bounds = time.bounds;
        float height = bounds.height();
        float mainWidth = height * digits.width(clock);
        digits.draw(canvas, clock, bounds.left + mainWidth / 2f, bounds.top,
                height, mainWidth, time.color, colon);
        float secondsHeight = height * SECONDS_SCALE;
        float secondsWidth = secondsHeight * digits.width(seconds);
        digits.draw(canvas, seconds, bounds.right - secondsWidth / 2f,
                bounds.bottom - secondsHeight, secondsHeight, secondsWidth, time.color, true);

        Item calendar = items[DATE];
        bounds = calendar.bounds;
        digits.draw(canvas, date, bounds.centerX(), bounds.top, bounds.height(),
                bounds.width(), calendar.color, true);

        if (weekday != null) {
            canvas.drawBitmap(weekday, null, items[DAY].bounds, dayPaint);
        }

        if (editing) {
            for (int i = 0; i < items.length; i++) {
                if (i == DIVIDER && !dividerVisible) {
                    continue;
                }
                outline.setColor(i == selected ? 0xffa4eee3 : 0xff667f89);
                outline.setStrokeWidth(density * (i == selected ? 2f : 1f));
                RectF box = items[i].bounds;
                float pad = 4f * density;
                float verticalPad = i == DIVIDER ? 14f * density : pad;
                canvas.drawRect(Math.max(0f, box.left - pad), Math.max(0f, box.top - verticalPad),
                        Math.min(getWidth(), box.right + pad),
                        Math.min(getHeight(), box.bottom + verticalPad), outline);
            }
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (actions == null) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                actions.revealControls();
                if (editing) {
                    selected = hitTest(event.getX(), event.getY());
                    active = selected;
                    moved = false;
                    anchor(event, -1);
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (active != -1) {
                    anchor(event, -1);
                    moved = true;
                }
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                if (active != -1) {
                    anchor(event, event.getActionIndex());
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (active != -1) {
                    move(event);
                    actions.revealControls();
                }
                return true;
            case MotionEvent.ACTION_UP:
                finishGesture();
                performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                finishGesture();
                return true;
            default:
                return true;
        }
    }

    @Override public boolean performClick() {
        super.performClick();
        actions.revealControls();
        return true;
    }

    private int hitTest(float x, float y) {
        float pad = 10f * density;
        // Text is drawn above the divider and receives touches first when they overlap.
        for (int offset = 0; offset < items.length; offset++) {
            int index = (DAY - offset + items.length) % items.length;
            if (index == DIVIDER && !dividerVisible) {
                continue;
            }
            RectF box = items[index].bounds;
            float verticalPad = index == DIVIDER ? 20f * density : pad;
            if (x >= box.left - pad && x <= box.right + pad
                    && y >= box.top - verticalPad && y <= box.bottom + verticalPad) {
                return index;
            }
        }
        return -1;
    }

    private void anchor(MotionEvent event, int ignoredPointer) {
        int first = ignoredPointer == 0 ? 1 : 0;
        int second = first + 1;
        if (second == ignoredPointer) {
            second++;
        }
        if (second < event.getPointerCount()) {
            lastX = (event.getX(first) + event.getX(second)) / 2f;
            lastY = (event.getY(first) + event.getY(second)) / 2f;
            lastSpan = span(event, first, second);
        } else {
            lastX = event.getX(first);
            lastY = event.getY(first);
            lastSpan = 0f;
        }
    }

    private static float span(MotionEvent event, int first, int second) {
        return (float) Math.hypot(event.getX(first) - event.getX(second),
                event.getY(first) - event.getY(second));
    }

    private void move(MotionEvent event) {
        boolean pinch = event.getPointerCount() > 1;
        float x = pinch ? (event.getX(0) + event.getX(1)) / 2f : event.getX();
        float y = pinch ? (event.getY(0) + event.getY(1)) / 2f : event.getY();
        float dx = x - lastX;
        float dy = y - lastY;
        if (!moved && Math.hypot(dx, dy) < slop) {
            return;
        }
        moved = true;
        Item item = items[active];
        float centerX = item.bounds.centerX() + dx;
        float centerY = item.bounds.centerY() + dy;
        float nextSpan = pinch ? span(event, 0, 1) : 0f;
        if (pinch && lastSpan >= 4f && nextSpan >= 4f) {
            float max = active == DIVIDER ? 100f : MAX_SIZE;
            item.size = clamp(item.effectiveSize * nextSpan / lastSpan, MIN_SIZE, max);
            place(active);
        }
        item.x = position(centerX, item.bounds.width(), getWidth());
        item.y = position(centerY, item.bounds.height(), getHeight());
        place(active);
        lastX = x;
        lastY = y;
        lastSpan = nextSpan;
        invalidate();
    }

    private static float position(float center, float itemSize, float screenSize) {
        return clamp(100f * (center - itemSize / 2f) / Math.max(1f, screenSize - itemSize), 0f, 100f);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    void finishGesture() {
        if (active != -1 && moved) {
            Item item = items[active];
            actions.savePosition(active, item.x, item.y, item.size);
        }
        active = -1;
        moved = false;
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setContentDescription(clock + ":" + seconds + ", " + date + ", " + day);
    }
}
