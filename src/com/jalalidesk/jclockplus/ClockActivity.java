package com.jalalidesk.jclockplus;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.util.Calendar;
import java.util.Locale;

public final class ClockActivity extends Activity {
    private static final int BACKGROUND = Color.BLACK;
    private static final int PANEL = 0xff15171b;
    private static final int INK = 0xfff5f1e7;
    private static final int MUTED = 0xffb1b5bf;
    private static final int ACCENT = 0xff9de3db;
    private static final int DEFAULT_DIM = 0xff6a6a6a;
    private static final String KEEP_SCREEN_ON = "keep_screen_on";
    private static final String ORIENTATION = "orientation";
    private static final String SHOW_DIVIDER = "show_divider";
    private static final int DIVIDER_THICKNESS = 2;
    private static final int PICK_PHOTO = 700;
    private static final String[] BACKGROUND_MODES = { "None", "Seasonal", "Custom" };
    private static final String[] CUSTOM_LAYOUTS = { "One photo for all seasons", "A photo for each season" };
    private static final String BACKGROUND_SEASON = "background_season";
    private static final String[] SEASON_CHOICES = { "Current season", "Spring", "Summer", "Autumn", "Winter" };
    private static final String[] ORIENTATION_NAMES = { "Auto", "Portrait", "Landscape" };
    private static final int[] ORIENTATIONS = {
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    };
    private static final long SETTINGS_BUTTON_TIMEOUT_MS = 5000L;
    private static final String[] ROW_TITLES = { "Time", "Jalali date", "Weekday", "Divider" };
    // Each row contains X, Y, and size percentages.
    private static final int[][] LANDSCAPE = {
        { 50, 24, 46 }, { 8, 85, 12 }, { 96, 85, 9 }, { 50, 70, 94 }
    };
    private static final int[][] PORTRAIT = {
        { 50, 39, 32 }, { 50, 60, 12 }, { 50, 78, 10 }, { 50, 51, 94 }
    };
    private static final String[] WEEKDAYS = {
        "", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه"
    };
    private static final int[] SWATCHES = {
        INK, Color.WHITE, DEFAULT_DIM, 0xffeda963, ACCENT, 0xff87b5ff
    };
    private static final String[] SWATCH_NAMES = {
        "Warm white", "White", "Gray", "Amber", "Mint", "Blue"
    };

    private final Handler handler = new Handler();
    private final int[] colors = new int[ClockFace.ITEM_COUNT];
    private SharedPreferences preferences;
    private ClockFace face;
    private ClockFace preview;
    private LinearLayout backgroundControls;
    private VisualAssets visuals;
    private View settingsButton;
    private PenButton penButton;
    private boolean editing;
    private boolean landscapeLayout;
    private boolean inSettings;
    private int cachedYear = -1;
    private int cachedMonth = -1;
    private int cachedDay = -1;
    private String cachedDate = "----/--/--";

    private final Runnable hideSettingsButton = new Runnable() {
        @Override public void run() {
            if (settingsButton != null) {
                settingsButton.setVisibility(View.INVISIBLE);
                penButton.setVisibility(View.INVISIBLE);
            }
        }
    };

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (face != null || preview != null) {
                updateClock();
                handler.postDelayed(this, 1000L - System.currentTimeMillis() % 1000L);
            }
        }
    };

    @Override protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        preferences = getSharedPreferences("clock_settings", MODE_PRIVATE);
        visuals = new VisualAssets(getApplicationContext());
        landscapeLayout = isLandscape();
        editing = savedState != null && savedState.getBoolean("editing");
        applyKeepScreenOn(preferences.getBoolean(KEEP_SCREEN_ON, true));
        applyOrientation();
        if (savedState != null && savedState.getBoolean("settings")) {
            showSettings();
        } else {
            showClock();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        restartClock();
        revealSettingsButton();
    }

    @Override protected void onPause() {
        if (face != null) {
            face.finishGesture();
        }
        handler.removeCallbacks(tick);
        handler.removeCallbacks(hideSettingsButton);
        super.onPause();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("settings", inSettings);
        state.putBoolean("editing", editing);
        super.onSaveInstanceState(state);
    }

    @Override public void onBackPressed() {
        if (inSettings) {
            showClock();
        } else if (editing) {
            setEditing(false);
        } else {
            super.onBackPressed();
        }
    }

    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        if (face != null) {
            face.finishGesture();
        }
        landscapeLayout = isLandscape();
        if (inSettings) {
            showSettings();
        } else {
            loadPositions(face);
            revealSettingsButton();
        }
    }

    private boolean isLandscape() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private void showClock() {
        inSettings = false;
        preview = null;
        backgroundControls = null;
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BACKGROUND);
        face = new ClockFace(this, new ClockFace.Actions() {
            @Override public void revealControls() {
                revealSettingsButton();
            }

            @Override public void savePosition(int index, float x, float y, float size) {
                preferences.edit().putFloat(layoutKey(index, "x"), x)
                        .putFloat(layoutKey(index, "y"), y)
                        .putFloat(layoutKey(index, "size"), size).apply();
            }
        });
        root.addView(face, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        loadPositions(face);

        TextView gear = label("Settings", 14, 0xffa6afbb);
        gear.setGravity(Gravity.CENTER);
        gear.setContentDescription("Settings");
        gear.setBackground(buttonBackground(BACKGROUND, 0xff25292f));
        gear.setClickable(true);
        gear.setFocusable(true);
        gear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                showSettings();
            }
        });
        FrameLayout.LayoutParams gearParams = new FrameLayout.LayoutParams(dp(76), dp(48),
                Gravity.BOTTOM | Gravity.RIGHT);
        gearParams.rightMargin = dp(5);
        root.addView(gear, gearParams);
        settingsButton = gear;

        penButton = new PenButton(this);
        penButton.setBackground(buttonBackground(BACKGROUND, 0xff25292f));
        penButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                setEditing(!editing);
            }
        });
        FrameLayout.LayoutParams penParams = new FrameLayout.LayoutParams(dp(56), dp(48),
                Gravity.BOTTOM | Gravity.LEFT);
        penParams.leftMargin = dp(5);
        root.addView(penButton, penParams);
        setContentView(root);
        setEditing(editing);
        restartClock();
        revealSettingsButton();
    }

    private void revealSettingsButton() {
        if (settingsButton != null) {
            settingsButton.setVisibility(View.VISIBLE);
            penButton.setVisibility(View.VISIBLE);
            handler.removeCallbacks(hideSettingsButton);
            handler.postDelayed(hideSettingsButton, SETTINGS_BUTTON_TIMEOUT_MS);
        }
    }

    private void setEditing(boolean enabled) {
        editing = enabled;
        face.setEditing(enabled);
        penButton.setEditing(enabled);
        revealSettingsButton();
    }

    private String layoutKey(int index, String property) {
        return (landscapeLayout ? "landscape_" : "portrait_") + index + "_" + property;
    }

    private int[] defaultLayout(int index) {
        return (landscapeLayout ? LANDSCAPE : PORTRAIT)[index];
    }

    private void loadPositions(ClockFace target) {
        for (int index = 0; index < ClockFace.ITEM_COUNT; index++) {
            int[] initial = defaultLayout(index);
            target.setItem(index, preferences.getFloat(layoutKey(index, "x"), initial[0]),
                    preferences.getFloat(layoutKey(index, "y"), initial[1]),
                    preferences.getFloat(layoutKey(index, "size"), initial[2]));
        }
        target.setDivider(preferences.getBoolean(SHOW_DIVIDER, true),
                preferences.getFloat(layoutKey(ClockFace.DIVIDER, "thickness"), DIVIDER_THICKNESS));
    }

    private void restartClock() {
        handler.removeCallbacks(tick);
        if (face != null || preview != null) {
            tick.run();
        }
    }

    private void updateClock() {
        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY);
        int minute = now.get(Calendar.MINUTE);
        int second = now.get(Calendar.SECOND);
        String time = new String(new char[] { digit(hour / 10), digit(hour % 10), ':',
                digit(minute / 10), digit(minute % 10) });
        String seconds = new String(new char[] { digit(second / 10), digit(second % 10) });
        int year = now.get(Calendar.YEAR);
        int month = now.get(Calendar.MONTH) + 1;
        int day = now.get(Calendar.DAY_OF_MONTH);
        if (year != cachedYear || month != cachedMonth || day != cachedDay) {
            String converted = JalaliCalendar.date(year, month, day);
            cachedDate = converted == null ? "----/--/--" : converted;
            cachedYear = year;
            cachedMonth = month;
            cachedDay = day;
        }
        boolean dim = preferences.getBoolean("dim_enabled", false)
                && DimSchedule.contains(hour * 60 + minute, startMinute(), endMinute());
        for (int i = 0; i < colors.length; i++) {
            colors[i] = dim ? preferences.getInt("dim_" + i, DEFAULT_DIM)
                    : preferences.getInt("normal_" + i, Color.WHITE);
        }
        ClockFace target = face != null ? face : preview;
        if (target == null) {
            return;
        }
        int weekday = now.get(Calendar.DAY_OF_WEEK);
        int selectedSeason = preferences.getInt(BACKGROUND_SEASON, -1);
        int season = selectedSeason < 0 ? VisualAssets.seasonForDate(cachedDate) : selectedSeason;
        target.setBackgroundPhoto(dim ? null : visuals.background(preferences, season), hour);
        target.update(time, seconds, cachedDate, WEEKDAYS[weekday], visuals.weekday(weekday),
                second % 2 == 0, colors);
    }

    private static char digit(int number) {
        return (char) ('0' + number);
    }

    private int startMinute() {
        return preferences.getInt("dim_start", DimSchedule.DEFAULT_START);
    }

    private int endMinute() {
        return preferences.getInt("dim_end", DimSchedule.DEFAULT_END);
    }

    private void applyKeepScreenOn(boolean enabled) {
        int flag = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        getWindow().setFlags(enabled ? flag : 0, flag);
    }

    private void applyOrientation() {
        int requested = ORIENTATIONS[preferences.getInt(ORIENTATION, 0)];
        if (getRequestedOrientation() != requested) {
            setRequestedOrientation(requested);
        }
    }

    private void showSettings() {
        inSettings = true;
        if (face != null) {
            face.finishGesture();
        }
        face = null;
        settingsButton = null;
        penButton = null;
        handler.removeCallbacks(tick);
        handler.removeCallbacks(hideSettingsButton);
        getWindow().getDecorView().setSystemUiVisibility(0);

        LinearLayout root = column();
        root.setBackgroundColor(BACKGROUND);
        LinearLayout header = row();
        header.setPadding(dp(20), dp(6), dp(16), dp(6));
        TextView title = label("Clock settings", 24, INK);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(title, weight());
        Button done = button("Done");
        done.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                showClock();
            }
        });
        header.addView(done);
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = column();
        content.setPadding(dp(20), dp(4), dp(20), dp(20));
        addDisplaySettings(content);
        addBackgroundSettings(content);
        addSchedule(content);
        TextView layoutTitle = label(landscapeLayout ? "Landscape layout" : "Portrait layout",
                15, MUTED);
        layoutTitle.setPadding(dp(2), dp(6), dp(2), dp(10));
        content.addView(layoutTitle);
        for (int i = 0; i < ROW_TITLES.length; i++) {
            addLineSettings(content, i);
        }
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        restartClock();
    }

    private void addDisplaySettings(LinearLayout content) {
        LinearLayout panel = addPanel(content);
        addSettingSwitch(panel, "Keep screen on", KEEP_SCREEN_ON, true);
        CheckBox divider = new CheckBox(this);
        divider.setText("Show divider");
        divider.setTextSize(18);
        divider.setTextColor(INK);
        divider.setMinHeight(dp(48));
        divider.setChecked(preferences.getBoolean(SHOW_DIVIDER, true));
        divider.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton button, boolean checked) {
                preferences.edit().putBoolean(SHOW_DIVIDER, checked).apply();
                refreshPreview();
            }
        });
        panel.addView(divider);
        addOrientationSetting(panel);
    }

    private void addBackgroundSettings(LinearLayout content) {
        LinearLayout panel = addPanel(content);
        TextView heading = label("Background preview", 18, INK);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        panel.addView(heading);

        int displayWidth = getResources().getDisplayMetrics().widthPixels;
        int displayHeight = getResources().getDisplayMetrics().heightPixels;
        float aspect = (float) displayWidth / displayHeight;
        int width = Math.max(dp(96), Math.min(displayWidth - dp(68), (int) (aspect * dp(245))));
        int height = Math.max(dp(64), (int) (width / aspect));
        preview = new ClockFace(this, null);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(width, height);
        previewParams.gravity = Gravity.CENTER_HORIZONTAL;
        previewParams.topMargin = dp(10);
        previewParams.bottomMargin = dp(10);
        panel.addView(preview, previewParams);
        backgroundControls = column();
        panel.addView(backgroundControls);
        populateBackgroundControls();
        refreshPreview();
    }

    private void refreshPreview() {
        if (preview != null) {
            loadPositions(preview);
            updateClock();
        }
    }

    private void populateBackgroundControls() {
        backgroundControls.removeAllViews();
        int mode = preferences.getInt(VisualAssets.MODE, VisualAssets.NONE);
        addChoice(backgroundControls, "Background", BACKGROUND_MODES, mode,
                new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        preferences.edit().putInt(VisualAssets.MODE, which).apply();
                        populateBackgroundControls();
                        refreshPreview();
                    }
                });

        boolean perSeason = preferences.getInt(VisualAssets.CUSTOM_LAYOUT, 0) == 1;
        if (mode == VisualAssets.CUSTOM) {
            addChoice(backgroundControls, "Custom photos", CUSTOM_LAYOUTS, perSeason ? 1 : 0,
                    new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface dialog, int which) {
                            preferences.edit().putInt(VisualAssets.CUSTOM_LAYOUT, which).apply();
                            populateBackgroundControls();
                            refreshPreview();
                        }
                    });
        }
        if (mode == VisualAssets.SEASONAL || (mode == VisualAssets.CUSTOM && perSeason)) {
            addChoice(backgroundControls, "Season", SEASON_CHOICES,
                    preferences.getInt(BACKGROUND_SEASON, -1) + 1,
                    new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface dialog, int which) {
                            preferences.edit().putInt(BACKGROUND_SEASON, which - 1).apply();
                            populateBackgroundControls();
                            refreshPreview();
                        }
                    });
        }

        if (mode == VisualAssets.CUSTOM) {
            if (perSeason) {
                for (int season = 0; season < VisualAssets.SEASONS.length; season++) {
                    addPhotoButton(season, VisualAssets.SEASONS[season]);
                }
            } else {
                addPhotoButton(VisualAssets.ALL_SEASONS, "All seasons");
            }
            TextView hint = label("An unset photo uses the built-in seasonal background.", 13, MUTED);
            hint.setPadding(0, dp(6), 0, 0);
            backgroundControls.addView(hint);
        }
    }

    private void addChoice(LinearLayout panel, final String title, final String[] options,
            final int selected, final DialogInterface.OnClickListener onChoice) {
        LinearLayout line = row();
        line.addView(label(title, 15, INK), weight());
        Button choice = button(options[selected]);
        choice.setMaxWidth(dp(210));
        choice.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                new AlertDialog.Builder(ClockActivity.this).setTitle(title)
                        .setSingleChoiceItems(options, selected, new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                onChoice.onClick(dialog, which);
                            }
                        })
                        .setNegativeButton("Cancel", null).show();
            }
        });
        line.addView(choice);
        panel.addView(line);
    }

    private void addPhotoButton(final int slot, String name) {
        Button photo = button((visuals.hasCustom(slot) ? "Replace photo · " : "Choose photo · ") + name);
        photo.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                Intent picker = new Intent(Intent.ACTION_GET_CONTENT);
                picker.setType("image/*");
                picker.addCategory(Intent.CATEGORY_OPENABLE);
                try {
                    startActivityForResult(Intent.createChooser(picker, "Choose a background"),
                            PICK_PHOTO + slot);
                } catch (ActivityNotFoundException exception) {
                    Toast.makeText(ClockActivity.this, "No photo picker is available.", Toast.LENGTH_LONG).show();
                }
            }
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(5);
        backgroundControls.addView(photo, params);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        final int slot = requestCode - PICK_PHOTO;
        if (slot < 0 || slot > VisualAssets.ALL_SEASONS || resultCode != RESULT_OK
                || data == null || data.getData() == null) {
            return;
        }
        final Uri uri = data.getData();
        new AsyncTask<Void, Void, Boolean>() {
            @Override protected Boolean doInBackground(Void... ignored) {
                return visuals.importPhoto(uri, slot);
            }

            @Override protected void onPostExecute(Boolean saved) {
                if (saved) {
                    visuals.invalidateCustom(slot);
                }
                if (isFinishing()) {
                    return;
                }
                if (!saved) {
                    Toast.makeText(ClockActivity.this, "Could not load this photo.", Toast.LENGTH_LONG).show();
                } else if (backgroundControls != null) {
                    populateBackgroundControls();
                    refreshPreview();
                }
            }
        }.execute();
    }

    private void addOrientationSetting(LinearLayout panel) {
        LinearLayout line = row();
        line.addView(label("Orientation", 18, INK), weight());
        final Button choice = button(ORIENTATION_NAMES[preferences.getInt(ORIENTATION, 0)]);
        choice.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                new AlertDialog.Builder(ClockActivity.this)
                        .setTitle("Orientation")
                        .setSingleChoiceItems(ORIENTATION_NAMES, preferences.getInt(ORIENTATION, 0),
                                new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) {
                                preferences.edit().putInt(ORIENTATION, which).apply();
                                choice.setText(ORIENTATION_NAMES[which]);
                                dialog.dismiss();
                                applyOrientation();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
        line.addView(choice);
        panel.addView(line);
    }

    private void addSettingSwitch(LinearLayout panel, String title, final String key,
            boolean defaultValue) {
        Switch enabled = new Switch(this);
        enabled.setText(title);
        enabled.setTextColor(INK);
        enabled.setTextSize(18);
        enabled.setMinHeight(dp(48));
        enabled.setChecked(preferences.getBoolean(key, defaultValue));
        enabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton button, boolean checked) {
                preferences.edit().putBoolean(key, checked).apply();
                if (KEEP_SCREEN_ON.equals(key)) {
                    applyKeepScreenOn(checked);
                }
                refreshPreview();
            }
        });
        panel.addView(enabled);
    }

    private void addSchedule(LinearLayout content) {
        LinearLayout panel = addPanel(content);
        addSettingSwitch(panel, "Dim schedule", "dim_enabled", false);

        LinearLayout times = row();
        final Button start = button("Start " + timeLabel(startMinute()));
        final Button end = button("End " + timeLabel(endMinute()));
        addPair(times, start, end);
        panel.addView(times);
        final TextView note = label("", 13, MUTED);
        note.setPadding(0, dp(8), 0, 0);
        panel.addView(note);
        updateScheduleNote(note);
        TextView backgroundNote = label("Dim mode hides the background until the schedule ends.",
                13, MUTED);
        backgroundNote.setPadding(0, dp(6), 0, 0);
        panel.addView(backgroundNote);

        start.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                pickTime(true, start, note);
            }
        });
        end.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                pickTime(false, end, note);
            }
        });
    }

    private void updateScheduleNote(TextView note) {
        int start = startMinute();
        int end = endMinute();
        if (start == end) {
            note.setText("Choose different start and end times to use dim colors.");
        } else if (start > end) {
            note.setText("Uses second colors overnight, until the end time the next morning.");
        } else {
            note.setText("Uses second colors during this time range.");
        }
    }

    private void pickTime(final boolean isStart, final Button button, final TextView note) {
        int initial = isStart ? startMinute() : endMinute();
        final TimePicker picker = new TimePicker(this);
        picker.setIs24HourView(true);
        picker.setCurrentHour(initial / 60);
        picker.setCurrentMinute(initial % 60);
        FrameLayout field = new FrameLayout(this);
        field.addView(picker, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        new AlertDialog.Builder(this)
                .setTitle(isStart ? "Dim start time" : "Dim end time")
                .setView(field)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        picker.clearFocus();
                        int value = picker.getCurrentHour() * 60 + picker.getCurrentMinute();
                        preferences.edit().putInt(isStart ? "dim_start" : "dim_end", value).apply();
                        button.setText((isStart ? "Start " : "End ") + timeLabel(value));
                        updateScheduleNote(note);
                        refreshPreview();
                    }
                }).show();
    }

    private static String timeLabel(int value) {
        return String.format(Locale.US, "%02d:%02d", value / 60, value % 60);
    }

    private void addLineSettings(LinearLayout content, final int index) {
        LinearLayout panel = addPanel(content);
        TextView title = label(ROW_TITLES[index], 18, INK);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        panel.addView(title);

        int[] initial = defaultLayout(index);
        boolean divider = index == ClockFace.DIVIDER;
        String sizeLabel = divider ? "Width" : "Size";
        addSlider(panel, sizeLabel, ROW_TITLES[index] + " " + sizeLabel.toLowerCase(Locale.US),
                layoutKey(index, "size"), (int) ClockFace.MIN_SIZE,
                divider ? 100 : (int) ClockFace.MAX_SIZE, initial[2], "%");
        if (divider) {
            addSlider(panel, "Thickness", "Divider thickness", layoutKey(index, "thickness"),
                    1, 24, DIVIDER_THICKNESS, " dp");
        }
        addSlider(panel, "X position", ROW_TITLES[index] + " X position",
                layoutKey(index, "x"), 0, 100, initial[0], "%");
        addSlider(panel, "Y position", ROW_TITLES[index] + " Y position",
                layoutKey(index, "y"), 0, 100, initial[1], "%");

        LinearLayout palette = row();
        addPair(palette, colorButton(index, false), colorButton(index, true));
        panel.addView(palette);
    }

    private void addSlider(LinearLayout panel, String title, String description,
            final String key, final int min, int max, int defaultValue, final String unit) {
        LinearLayout scale = row();
        scale.addView(label(title, 15, MUTED));
        final SeekBar slider = new SeekBar(this);
        slider.setContentDescription(description);
        slider.setMax(max - min);
        int current = Math.round(preferences.getFloat(key, defaultValue));
        slider.setProgress(current - min);
        LinearLayout.LayoutParams sliderParams = weight();
        sliderParams.height = dp(48);
        sliderParams.leftMargin = dp(8);
        sliderParams.rightMargin = dp(8);
        scale.addView(slider, sliderParams);
        final TextView value = label(current + unit, 15, ACCENT);
        value.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        scale.addView(value, new LinearLayout.LayoutParams(dp(56), dp(48)));
        panel.addView(scale);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int setting = min + progress;
                value.setText(setting + unit);
                if (fromUser) {
                    preferences.edit().putFloat(key, setting).apply();
                    refreshPreview();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

    }

    private Button colorButton(final int index, final boolean dim) {
        final String caption = dim ? "Second color" : "First color";
        final String key = (dim ? "dim_" : "normal_") + index;
        final Button button = button("");
        updateColorButton(button, caption, preferences.getInt(key, dim ? DEFAULT_DIM : Color.WHITE));
        button.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                pickColor(index, key, caption, dim ? DEFAULT_DIM : Color.WHITE, button);
            }
        });
        return button;
    }

    private void pickColor(int index, final String key, final String caption, int defaultColor,
            final Button button) {
        LinearLayout field = column();
        field.setPadding(dp(20), dp(10), dp(20), dp(8));
        field.addView(label("Choose a color or enter a hex code.", 15, MUTED));

        final EditText hex = new EditText(this);
        hex.setSingleLine(true);
        hex.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        hex.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        hex.setFilters(new InputFilter[] { new InputFilter.LengthFilter(7) });
        hex.setContentDescription("Hex color code");
        hex.setText(hexLabel(preferences.getInt(key, defaultColor)));
        hex.setSelectAllOnFocus(true);

        LinearLayout presets = row();
        for (int i = 0; i < SWATCHES.length; i++) {
            final int color = SWATCHES[i];
            Button preset = new Button(this);
            preset.setMinimumWidth(0);
            preset.setMinWidth(0);
            preset.setMinHeight(0);
            preset.setContentDescription(SWATCH_NAMES[i]);
            preset.setBackground(buttonBackground(color, contrastOn(color)));
            preset.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    hex.setText(hexLabel(color));
                }
            });
            LinearLayout.LayoutParams params = weight();
            params.height = dp(40);
            params.topMargin = dp(12);
            params.rightMargin = i == SWATCHES.length - 1 ? 0 : dp(6);
            presets.addView(preset, params);
        }
        field.addView(presets);
        field.addView(hex);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(ROW_TITLES[index] + " · " + caption)
                .setView(field)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        dialog.show();
        final View.OnClickListener save = new View.OnClickListener() {
            @Override public void onClick(View view) {
                String text = hex.getText().toString().trim();
                if (!text.matches("#?[0-9a-fA-F]{6}")) {
                    hex.setError("Enter six hex digits, for example #6A6A6A");
                    return;
                }
                int color = Color.parseColor(text.startsWith("#") ? text : "#" + text);
                preferences.edit().putInt(key, color).apply();
                updateColorButton(button, caption, color);
                dialog.dismiss();
                refreshPreview();
            }
        };
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(save);
        hex.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView view, int action, KeyEvent event) {
                if (action == EditorInfo.IME_ACTION_DONE) {
                    save.onClick(view);
                    return true;
                }
                return false;
            }
        });
    }

    private void updateColorButton(Button button, String caption, int color) {
        button.setText(caption + "\n" + hexLabel(color));
        button.setTextColor(contrastOn(color));
        button.setBackground(buttonBackground(color, 0xff828791));
        button.setMinHeight(dp(56));
    }

    private static String hexLabel(int color) {
        return String.format(Locale.US, "#%06X", color & 0xffffff);
    }

    private static int contrastOn(int color) {
        double luminance = .2126 * linear(Color.red(color)) + .7152 * linear(Color.green(color))
                + .0722 * linear(Color.blue(color));
        return luminance > .179 ? Color.BLACK : Color.WHITE;
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= .04045 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4);
    }

    private LinearLayout addPanel(LinearLayout parent) {
        LinearLayout panel = column();
        panel.setPadding(dp(14), dp(12), dp(14), dp(12));
        panel.setBackground(rounded(PANEL));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(10);
        parent.addView(panel, params);
        return panel;
    }

    private void addPair(LinearLayout parent, Button firstButton, Button secondButton) {
        LinearLayout.LayoutParams first = weight();
        first.rightMargin = dp(8);
        parent.addView(firstButton, first);
        parent.addView(secondButton, weight());
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setTextColor(ColorStateList.valueOf(INK));
        button.setBackground(buttonBackground(0xff24272d, 0xff41454e));
        button.setMinHeight(dp(48));
        button.setMinimumWidth(dp(68));
        return button;
    }

    private StateListDrawable buttonBackground(int normal, int active) {
        StateListDrawable state = new StateListDrawable();
        state.addState(new int[] { android.R.attr.state_pressed }, rounded(active));
        state.addState(new int[] { android.R.attr.state_focused }, rounded(active));
        state.addState(new int[0], rounded(normal));
        return state;
    }

    private GradientDrawable rounded(int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(9));
        return shape;
    }

    private TextView label(String text, int size, int color) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(size);
        label.setTextColor(color);
        return label;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private static LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
