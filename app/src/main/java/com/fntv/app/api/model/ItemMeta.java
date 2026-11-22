package com.fntv.app.api.model;

/**
 * 大图推荐位副信息行的内容：分级 / 类型 / 国家 / 媒体库。
 */
public class ItemMeta {

    public String rating;      // TV-MA
    public String genres;      // 恐怖 · 悬疑 · 剧情
    public String countries;   // 美国

    public boolean isEmpty() {
        return blank(rating) && blank(genres) && blank(countries);
    }

    /** 拼成一行：TV-MA / 恐怖 · 悬疑 · 剧情 / 美国 */
    public String line() {
        StringBuilder sb = new StringBuilder();
        append(sb, rating);
        append(sb, genres);
        append(sb, countries);
        return sb.toString();
    }

    private static void append(StringBuilder sb, String text) {
        if (blank(text)) return;
        if (sb.length() > 0) sb.append(" / ");
        sb.append(text);
    }

    private static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
