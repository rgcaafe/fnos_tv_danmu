package com.fntv.app.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.LruCache;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 简易图片加载器 - 使用 OkHttp（自动经过 AuthInterceptor 签名）
 * 兼容 Android 4.4+
 */
public class SimpleImageLoader {

    private static final String TAG = "ImageLoader";
    private static final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap bitmap) {
            return bitmap.getByteCount();
        }
    };

    /** 加载图片（使用 OkHttpClient，自动携带认证头） */
    public static void load(String url, ImageView view, OkHttpClient client) {
        load(url, view, client, false, false, false, null);
    }

    /**
     * 加载带透明通道的图片（片名艺术字 logo）。
     * 必须用 ARGB_8888 解码，RGB_565 会把透明区域变成黑色。
     */
    public static void loadAlpha(String url, ImageView view, OkHttpClient client, Runnable onLoaded) {
        load(url, view, client, true, false, false, onLoaded);
    }

    /**
     * 加载片名 logo，并按当前明暗主题处理：
     * 浅色主题下把白色的字改成深色，深色主题下把纯黑的字改成白色，
     * 金色/红色等彩色部分保持原样。否则白 logo 放在浅色背景上会看不见。
     */
    public static void loadAlpha(String url, ImageView view, OkHttpClient client,
                                 boolean nightMode, Runnable onLoaded) {
        load(url, view, client, true, true, nightMode, onLoaded);
    }

    private static void load(String url, ImageView view, OkHttpClient client,
                             boolean alpha, boolean adaptLogo, boolean nightMode,
                             Runnable onLoaded) {
        if (url == null || url.isEmpty()) {
            if (alpha) {
                view.setVisibility(View.GONE);
                return;
            }
            view.setImageBitmap(null);
            view.setBackgroundColor(0xFF333333);
            return;
        }

        String key = (alpha ? (adaptLogo ? (nightMode ? "N" : "L") : "A") : "") + "|" + url;
        Bitmap cached = cache.get(key);
        if (cached != null) {
            view.setImageBitmap(cached);
            if (onLoaded != null) onLoaded.run();
            return;
        }

        // Android 4.4 AsyncTask 默认串行，用线程池实现并行下载
        new ImageLoadTask(view, client, key, alpha, adaptLogo, nightMode, onLoaded)
                .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, url);
    }

    public static void clearCache() {
        cache.evictAll();
    }

    private static class ImageLoadTask extends AsyncTask<String, Void, Bitmap> {
        private final ImageView imageView;
        private final OkHttpClient client;
        private final String cacheKey;
        private final boolean alpha;
        private final boolean adaptLogo;
        private final boolean nightMode;
        private final Runnable onLoaded;

        ImageLoadTask(ImageView imageView, OkHttpClient client, String cacheKey,
                      boolean alpha, boolean adaptLogo, boolean nightMode,
                      Runnable onLoaded) {
            this.imageView = imageView;
            this.client = client;
            this.cacheKey = cacheKey;
            this.alpha = alpha;
            this.adaptLogo = adaptLogo;
            this.nightMode = nightMode;
            this.onLoaded = onLoaded;
        }

        @Override
        protected Bitmap doInBackground(String... params) {
            String urlStr = params[0];
            try {
                Request request = new Request.Builder()
                        .url(urlStr)
                        .build();

                Response response = client.newCall(request).execute();
                int code = response.code();
                String contentType = response.header("Content-Type");
                long len = response.body() != null ? response.body().contentLength() : -1;
                // Log.d(TAG, "HTTP " + code + " type=" + contentType + " len=" + len + " " + urlStr);

                if (code != 200) return null;

                // 不是图片就打印并跳过
                if (contentType != null && !contentType.contains("image")) {
                    String body = response.body() != null ? response.body().string() : "";
                    String snippet = body.length() > 200 ? body.substring(0, 200) : body;
                    Log.w(TAG, "NOT_IMAGE: " + snippet);
                    return null;
                }

                // 读取图片字节
                InputStream input = response.body().byteStream();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = input.read(buf)) != -1) baos.write(buf, 0, n);
                input.close();
                byte[] imageData = baos.toByteArray();

                // 三重解码尝试
                Bitmap bitmap = tryDecode(imageData, alpha);
                if (bitmap != null && adaptLogo) {
                    bitmap = adaptLogoForTheme(bitmap, nightMode);
                }

                if (bitmap != null) {
                    cache.put(cacheKey, bitmap);
                    Log.d(TAG, "OK (" + bitmap.getWidth() + "x" + bitmap.getHeight() + ")");
                } else {
                    Log.e(TAG, "DECODE_FAILED");
                }
                return bitmap;
            } catch (Exception e) {
                Log.e(TAG, "FAIL: " + e.getMessage());
                return null;
            }
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);
                if (onLoaded != null) onLoaded.run();
            } else if (alpha) {
                // logo 加载失败就别留一块灰底
                imageView.setVisibility(View.GONE);
            } else {
                imageView.setBackgroundColor(0xFF444444);
            }
        }
    }

    private static Bitmap tryDecode(byte[] data, boolean alpha) {
        if (alpha) {
            try {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inPreferredConfig = Bitmap.Config.ARGB_8888;
                o.inPurgeable = true;
                o.inInputShareable = true;
                return BitmapFactory.decodeByteArray(data, 0, data.length, o);
            } catch (Exception e) {
                return null;
            }
        }

        Bitmap bmp = null;
        if (bmp == null) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            o.inPurgeable = true;
            o.inInputShareable = true;
            try { bmp = BitmapFactory.decodeByteArray(data, 0, data.length, o); } catch (Exception ignored) {}
        }
        if (bmp == null) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;
            o.inPurgeable = true;
            o.inInputShareable = true;
            try { bmp = BitmapFactory.decodeByteArray(data, 0, data.length, o); } catch (Exception ignored) {}
        }
        if (bmp == null) {
            try { bmp = BitmapFactory.decodeByteArray(data, 0, data.length); } catch (Exception ignored) {}
        }
        return bmp;
    }

    /**
     * 只改接近灰白的像素（保留金色、红色等彩色部分）：
     * - 浅色主题：整张 logo 平均很亮 → 白字改成深灰
     * - 深色主题：整张 logo 平均很暗 → 黑字改成白色
     */
    private static Bitmap adaptLogoForTheme(Bitmap src, boolean nightMode) {
        if (src == null) return null;
        final int w = src.getWidth(), h = src.getHeight();
        if (w <= 0 || h <= 0) return src;

        int[] pixels = new int[w * h];
        src.getPixels(pixels, 0, w, 0, 0, w, h);

        long sum = 0;
        int count = 0;
        for (int p : pixels) {
            int a = (p >>> 24) & 0xFF;
            if (a < 32) continue;
            int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
            sum += (r * 299 + g * 587 + b * 114) / 1000;
            count++;
        }
        if (count == 0) return src;
        int avg = (int) (sum / count);

        final int target;
        if (!nightMode && avg > 170) {
            target = 0x1A;             // 浅色背景：白 logo → 深灰
        } else if (nightMode && avg < 90) {
            target = 0xFF;             // 深色背景：黑 logo → 白色
        } else {
            return src;
        }

        boolean changed = false;
        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            int a = (p >>> 24) & 0xFF;
            if (a == 0) continue;
            int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
            int max = Math.max(r, Math.max(g, b));
            int min = Math.min(r, Math.min(g, b));
            int lum = (r * 299 + g * 587 + b * 114) / 1000;
            int sat = max - min;

            if (!nightMode) {
                if (sat < 48) {
                    // 白色/灰色字：直接压成深灰，保证在浅色背景上清晰
                    pixels[i] = (p & 0xFF000000) | (target << 16) | (target << 8) | target;
                    changed = true;
                } else if (lum > 150) {
                    // 金色、亮红等彩色字：整体压暗，保留色相
                    float f = Math.max(0.45f, 110f / Math.max(1, lum));
                    int nr = Math.round(r * f), ng = Math.round(g * f), nb = Math.round(b * f);
                    pixels[i] = (p & 0xFF000000) | (nr << 16) | (ng << 8) | nb;
                    changed = true;
                }
            } else {
                if (sat < 48) {
                    // 深色背景：黑色字提成白色
                    pixels[i] = (p & 0xFF000000) | (target << 16) | (target << 8) | target;
                    changed = true;
                } else if (lum < 80) {
                    // 深色背景：很暗的彩色字略微提亮
                    float f = Math.min(3f, 150f / Math.max(1, lum));
                    int nr = Math.min(255, Math.round(r * f));
                    int ng = Math.min(255, Math.round(g * f));
                    int nb = Math.min(255, Math.round(b * f));
                    pixels[i] = (p & 0xFF000000) | (nr << 16) | (ng << 8) | nb;
                    changed = true;
                }
            }
        }
        if (!changed) return src;

        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        out.setPixels(pixels, 0, w, 0, 0, w, h);
        src.recycle();
        return out;
    }
}
