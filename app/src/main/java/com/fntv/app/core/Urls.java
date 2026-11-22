package com.fntv.app.core;

/**
 * 图片地址拼装（飞牛 /sys/img 接口）。
 */
public final class Urls {

    private Urls() {
    }

    public static String poster(String baseUrl, String path, int width) {
        if (path == null || path.isEmpty() || baseUrl == null || baseUrl.isEmpty()) return null;
        String p = path.startsWith("/") ? path : "/" + path;
        return baseUrl + "/v/api/v1/sys/img" + p + "?w=" + width;
    }
}
