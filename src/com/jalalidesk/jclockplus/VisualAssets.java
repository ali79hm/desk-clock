package com.jalalidesk.jclockplus;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.util.LruCache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

final class VisualAssets {
    static final String MODE = "background_mode";
    static final String CUSTOM_LAYOUT = "background_custom_layout";
    static final int NONE = 0;
    static final int SEASONAL = 1;
    static final int CUSTOM = 2;
    static final int ALL_SEASONS = 4;
    static final String[] SEASONS = { "Spring", "Summer", "Autumn", "Winter" };
    private static final String[] BUNDLED = {
        "seasons/spring.jpg", "seasons/summer.jpg", "seasons/autumn.jpg", "seasons/winter.jpg"
    };
    private final Context context;
    private final LruCache<String, Bitmap> backgrounds = new LruCache<String, Bitmap>(5 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap bitmap) {
            return bitmap.getByteCount();
        }
    };
    private Bitmap weekday;
    private int weekdayIndex;

    VisualAssets(Context context) {
        this.context = context;
    }

    static int seasonForDate(String date) {
        if (date.length() < 7 || date.charAt(5) < '0' || date.charAt(5) > '1'
                || date.charAt(6) < '0' || date.charAt(6) > '9') {
            return 0;
        }
        int month = (date.charAt(5) - '0') * 10 + date.charAt(6) - '0';
        return month >= 1 && month <= 12 ? (month - 1) / 3 : 0;
    }

    Bitmap weekday(int index) {
        if (weekdayIndex != index) {
            weekday = bundled("weekdays/" + index + ".png", Bitmap.Config.ARGB_8888);
            weekdayIndex = index;
        }
        return weekday;
    }

    Bitmap background(SharedPreferences preferences, int season) {
        int mode = preferences.getInt(MODE, NONE);
        if (mode == NONE) {
            return null;
        }
        if (mode == CUSTOM) {
            int slot = preferences.getInt(CUSTOM_LAYOUT, 0) == 0 ? ALL_SEASONS : season;
            File image = customFile(slot);
            if (image.isFile()) {
                String key = image.getName();
                Bitmap bitmap = backgrounds.get(key);
                if (bitmap == null) {
                    bitmap = BitmapFactory.decodeFile(image.getPath(), options(Bitmap.Config.RGB_565));
                    if (bitmap != null) {
                        backgrounds.put(key, bitmap);
                    }
                }
                if (bitmap != null) {
                    return bitmap;
                }
            }
        }
        String path = BUNDLED[season];
        Bitmap bitmap = backgrounds.get(path);
        if (bitmap == null) {
            bitmap = bundled(path, Bitmap.Config.RGB_565);
            if (bitmap != null) {
                backgrounds.put(path, bitmap);
            }
        }
        return bitmap;
    }

    boolean hasCustom(int slot) {
        return customFile(slot).isFile();
    }

    void invalidateCustom(int slot) {
        backgrounds.remove(customFile(slot).getName());
    }

    private File customFile(int slot) {
        return new File(context.getFilesDir(), "background_" + slot + ".jpg");
    }

    private Bitmap bundled(String path, Bitmap.Config config) {
        try {
            InputStream stream = context.getAssets().open(path);
            try {
                return BitmapFactory.decodeStream(stream, null, options(config));
            } finally {
                stream.close();
            }
        } catch (IOException exception) {
            return null;
        }
    }

    private static BitmapFactory.Options options(Bitmap.Config config) {
        BitmapFactory.Options result = new BitmapFactory.Options();
        result.inPreferredConfig = config;
        return result;
    }

    boolean importPhoto(Uri uri, int slot) {
        File source = null;
        File replacement = null;
        Bitmap photo = null;
        try {
            source = File.createTempFile("photo-source", ".tmp", context.getCacheDir());
            InputStream input = context.getContentResolver().openInputStream(uri);
            if (input == null) {
                return false;
            }
            try {
                FileOutputStream output = new FileOutputStream(source);
                try {
                    byte[] buffer = new byte[16 * 1024];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        output.write(buffer, 0, count);
                    }
                } finally {
                    output.close();
                }
            } finally {
                input.close();
            }
            BitmapFactory.Options size = options(Bitmap.Config.RGB_565);
            size.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(source.getPath(), size);
            if (size.outWidth <= 0 || size.outHeight <= 0) {
                return false;
            }
            size.inJustDecodeBounds = false;
            size.inSampleSize = 1;
            while (Math.max(size.outWidth, size.outHeight) / size.inSampleSize > 1600) {
                size.inSampleSize *= 2;
            }
            photo = BitmapFactory.decodeFile(source.getPath(), size);
            if (photo == null) {
                return false;
            }
            Matrix matrix = orientationTransform(source);
            if (!matrix.isIdentity()) {
                Bitmap corrected = Bitmap.createBitmap(photo, 0, 0, photo.getWidth(),
                        photo.getHeight(), matrix, true);
                if (corrected != photo) {
                    photo.recycle();
                    photo = corrected;
                }
            }
            replacement = File.createTempFile("background", ".tmp", context.getFilesDir());
            FileOutputStream output = new FileOutputStream(replacement);
            try {
                if (!photo.compress(Bitmap.CompressFormat.JPEG, 87, output)) {
                    return false;
                }
            } finally {
                output.close();
            }
            return replacement.renameTo(customFile(slot));
        } catch (IOException | RuntimeException | OutOfMemoryError exception) {
            return false;
        } finally {
            if (photo != null) {
                photo.recycle();
            }
            if (source != null) {
                source.delete();
            }
            if (replacement != null) {
                replacement.delete();
            }
        }
    }

    private static Matrix orientationTransform(File source) {
        Matrix matrix = new Matrix();
        int orientation;
        try {
            orientation = new ExifInterface(source.getPath()).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (IOException exception) {
            return matrix;
        }
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: matrix.setScale(-1, 1); break;
            case ExifInterface.ORIENTATION_ROTATE_180: matrix.setRotate(180); break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL: matrix.setScale(1, -1); break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90: matrix.setRotate(90); break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(270);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270: matrix.setRotate(270); break;
            default: break;
        }
        return matrix;
    }
}
