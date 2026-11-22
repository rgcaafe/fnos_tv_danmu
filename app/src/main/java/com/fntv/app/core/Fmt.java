package com.fntv.app.core;

import com.fntv.app.api.model.PlayListItem;

/**
 * 展示层格式化工具。
 */
public final class Fmt {

    private Fmt() {
    }

    /** 12:34 / 1:02:03 */
    public static String clock(long seconds) {
        if (seconds <= 0) return "00:00";
        long s = seconds % 60;
        long m = (seconds / 60) % 60;
        long h = seconds / 3600;
        if (h > 0) return String.format("%d:%02d:%02d", h, m, s);
        return String.format("%02d:%02d", m, s);
    }

    /** 2 小时 5 分 */
    public static String duration(long seconds) {
        if (seconds <= 0) return "";
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        if (h > 0) return h + " 小时 " + m + " 分";
        return Math.max(1, m) + " 分钟";
    }

    public static String year(String date) {
        if (date == null || date.length() < 4) return null;
        String year = date.substring(0, 4);
        try {
            int y = Integer.parseInt(year);
            return y >= 1900 && y <= 2100 ? year : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String vote(String raw) {
        if (raw == null) return null;
        try {
            float v = Float.parseFloat(raw);
            return v > 0 ? String.format("%.1f", v) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String type(String type) {
        if (type == null) return "视频";
        switch (type) {
            case "TV":
            case "Episode":
            case "Season":
                return "剧集";
            case "Movie":
                return "电影";
            case "Directory":
            case "Folder":
                return "文件夹";
            case "Video":
                return "视频";
            case "LiveChannel":
                return "直播";
            default:
                return type;
        }
    }

    /** 继续观看的标题：剧名 + 季 + 集 */
    public static String continueTitle(PlayListItem item) {
        StringBuilder sb = new StringBuilder();
        if (item.tvTitle != null && !item.tvTitle.isEmpty()) sb.append(item.tvTitle);
        else sb.append(item.title == null ? "" : item.title);
        if (item.seasonNumber > 0) sb.append(" · 第 ").append(item.seasonNumber).append(" 季");
        if (item.episodeNumber > 0) sb.append(" · ").append(item.episodeNumber).append(" 集");
        return sb.toString().trim();
    }

    /** 卡片副标题：共 12 集 · 2024 */
    public static String cardMeta(PlayListItem item) {
        StringBuilder sb = new StringBuilder();
        if (item.numberOfEpisodes > 0) sb.append("共 ").append(item.numberOfEpisodes).append(" 集");
        else if (item.duration > 0) sb.append(clock(item.duration));
        else sb.append(type(item.type));
        String year = year(item.airDate);
        if (year != null) sb.append(" · ").append(year);
        return sb.toString();
    }

    public static String initial(String text) {
        if (text == null) return "?";
        String t = text.trim();
        return t.isEmpty() ? "?" : t.substring(0, 1).toUpperCase();
    }

    /** 详情页主标题（剧名优先） */
    public static String primaryTitle(String episodeTitle, String series) {
        if (series != null && !series.trim().isEmpty()) return series.trim();
        return episodeTitle == null ? "" : episodeTitle.trim();
    }

    /** 详情页副标题（第 N 集 · 本集标题） */
    public static String episodeLine(String episodeTitle, String series, int episodeNumber) {
        String ep = episodeTitle == null ? "" : episodeTitle.trim();
        boolean differs = series != null && !series.trim().isEmpty()
                && !ep.isEmpty() && !ep.equals(series.trim());
        if (differs && episodeNumber > 0) {
            // 本集标题就是“第 N 集”时不再重复
            String plain = "第 " + episodeNumber + " 集";
            String compact = "第" + episodeNumber + "集";
            if (ep.equals(plain) || ep.equals(compact)) differs = false;
        }
        StringBuilder sb = new StringBuilder();
        if (episodeNumber > 0) sb.append("第 ").append(episodeNumber).append(" 集");
        if (differs) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(ep);
        }
        return sb.length() == 0 ? null : sb.toString();
    }
}
