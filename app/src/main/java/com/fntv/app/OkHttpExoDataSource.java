package com.fntv.app;

import android.net.Uri;
import android.util.Log;
import com.google.android.exoplayer2.upstream.BaseDataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class OkHttpExoDataSource extends BaseDataSource {

    private static final String TAG = "OkHttpDS";
    private static int chunkSize = 0; // 0 = 不分块
    private static String cloudCookie = "";
    public static int lastResponseCode = 0;
    public static String lastContentType = "";
    /** 最近一次请求对应的完整文件大小（来自 Content-Range 或 Content-Length，未知为 0） */
    public static long lastTotalLength = 0;

    /** 本次播放已读取的字节区间（按 URL 去重，避免拖动进度后重复累加） */
    private static final Object rangeLock = new Object();
    private static final java.util.HashMap<String, java.util.ArrayList<long[]>> loadedRanges =
            new java.util.HashMap<>();

    private final OkHttpClient client;
    private String rangeKey;
    private long rangePosition;

    public static void setCloudCookie(String cookie) { cloudCookie = cookie; }
    private Response response;
    private BufferedInputStream bufferedInput;
    private long bytesRead;
    private boolean transferStarted;

    /** 设置分块模式（字节数），用于夸克等云盘直链 */
    public static void setChunkedMode(int bytes) { chunkSize = bytes; }

    public OkHttpExoDataSource(OkHttpClient client) {
        super(true);
        this.client = client;
    }

    /** 播放新一集/切换画质时清空已下载统计 */
    public static void resetLoadedBytes() {
        synchronized (rangeLock) {
            loadedRanges.clear();
        }
    }

    /** 已下载（缓存）的总字节数（多个媒体文件按 URL 分别去重后求和） */
    public static long getLoadedBytes() {
        synchronized (rangeLock) {
            long sum = 0;
            for (java.util.ArrayList<long[]> list : loadedRanges.values()) {
                for (long[] range : list) sum += range[1] - range[0];
            }
            return sum;
        }
    }

    private void recordLoadedRange(long start, long end) {
        if (rangeKey == null || end <= start) return;
        synchronized (rangeLock) {
            java.util.ArrayList<long[]> list = loadedRanges.get(rangeKey);
            if (list == null) {
                list = new java.util.ArrayList<>();
                loadedRanges.put(rangeKey, list);
            }
            list.add(new long[]{start, end});
            java.util.Collections.sort(list, (a, b) -> Long.compare(a[0], b[0]));
            // 合并重叠/相邻区间
            int write = 0;
            for (int i = 0; i < list.size(); i++) {
                long[] cur = list.get(i);
                if (write == 0) {
                    list.set(write++, new long[]{cur[0], cur[1]});
                } else {
                    long[] last = list.get(write - 1);
                    if (cur[0] <= last[1]) {
                        last[1] = Math.max(last[1], cur[1]);
                    } else {
                        list.set(write++, new long[]{cur[0], cur[1]});
                    }
                }
            }
            while (list.size() > write) list.remove(list.size() - 1);
        }
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        rangeKey = dataSpec.uri.toString();
        rangePosition = dataSpec.position;
        Request.Builder builder = new Request.Builder()
                .url(dataSpec.uri.toString());

        // 分块模式：每次只请求 chunkSize 字节（如 10MB），避免云盘风控
        long rangeEnd = -1;
        if (chunkSize > 0 && dataSpec.length > chunkSize) {
            rangeEnd = dataSpec.position + chunkSize - 1;
        }
        String range = rangeEnd > 0
                ? "bytes=" + dataSpec.position + "-" + rangeEnd
                : "bytes=" + dataSpec.position + "-";
        builder.header("Range", range);
        builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

        // 云盘直链 Cookie
        if (!cloudCookie.isEmpty()) {
            builder.header("Cookie", cloudCookie);
        }

        if (dataSpec.httpRequestHeaders != null) {
            for (String key : dataSpec.httpRequestHeaders.keySet()) {
                builder.header(key, dataSpec.httpRequestHeaders.get(key));
            }
        }

        response = client.newCall(builder.build()).execute();

        lastResponseCode = response.code();
        lastContentType = response.header("Content-Type", "");

        if (response.code() != 200 && response.code() != 206) {
            response.close();
            throw new IOException("HTTP " + response.code() + " " + response.message());
        }
        String ct = response.header("Content-Type");
        long len = response.body() != null ? response.body().contentLength() : -1;
        // 解析总大小：206 响应优先取 Content-Range 中的完整长度
        lastTotalLength = 0;
        String contentRange = response.header("Content-Range");
        if (contentRange != null) {
            int slash = contentRange.lastIndexOf('/');
            if (slash >= 0) {
                try {
                    String total = contentRange.substring(slash + 1).trim();
                    if (!"*".equals(total)) lastTotalLength = Long.parseLong(total);
                } catch (NumberFormatException ignored) {}
            }
        }
        if (lastTotalLength <= 0 && response.code() == 200 && len > 0) lastTotalLength = len;
        Log.d(TAG, "open: code=" + response.code() + " type=" + ct + " len=" + len
                + " total=" + lastTotalLength);

        InputStream rawIn = response.body().byteStream();
        bufferedInput = new BufferedInputStream(rawIn, 65536);

        bytesRead = 0;
        transferStarted(dataSpec);
        transferStarted = true;

        // 返回实际可读长度（分块模式下不超过 chunkSize）
        long contentLen = response.body().contentLength();
        if (chunkSize > 0 && contentLen > chunkSize) contentLen = chunkSize;
        return contentLen;
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws IOException {
        if (bufferedInput == null) return -1;
        // 分块模式下，读完块大小后截断
        if (chunkSize > 0 && bytesRead >= chunkSize) return -1;
        int maxRead = readLength;
        if (chunkSize > 0) maxRead = (int) Math.min(readLength, chunkSize - bytesRead);
        int n = bufferedInput.read(buffer, offset, maxRead);
        if (n > 0) {
            bytesRead += n;
            recordLoadedRange(rangePosition, rangePosition + n);
            rangePosition += n;
            bytesTransferred(n);
        }
        return n;
    }

    @Override
    public Uri getUri() {
        return response != null && response.request() != null
                ? Uri.parse(response.request().url().toString()) : null;
    }

    @Override
    public void close() throws IOException {
        if (transferStarted) {
            transferEnded();
            transferStarted = false;
        }
        if (bufferedInput != null) bufferedInput.close();
        if (response != null) response.close();
        bufferedInput = null;
        response = null;
    }
}
