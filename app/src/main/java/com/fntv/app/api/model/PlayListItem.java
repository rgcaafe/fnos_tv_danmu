package com.fntv.app.api.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * 媒体列表项
 * 对应 Electron types.ts 中的 PlayListItem
 */
public class PlayListItem {

    public String guid;
    public String title;
    public String type;           // "Episode", "Video", "Folder" 等

    public String poster;

    @SerializedName("tv_title")
    public String tvTitle;

    @SerializedName("parent_title")
    public String parentTitle;

    @SerializedName("parent_guid")
    public String parentGuid;

    @SerializedName("ancestor_name")
    public String ancestorName;   // 如 "日漫"

    @SerializedName("ancestor_category")
    public String ancestorCategory;

    public int watched;           // 1=已看, 0=未看
    public long ts;               // 播放进度(秒)

    public long duration;         // 视频时长(秒)

    @SerializedName("episode_number")
    public int episodeNumber;

    @SerializedName("season_number")
    public int seasonNumber;

    @SerializedName("vote_average")
    public String voteAverage;

    public String overview;       // 简介

    @SerializedName("runtime")
    public int runtime;           // 运行时长(分钟)

    @SerializedName("is_favorite")
    public int isFavorite;

    @SerializedName("watermark_percent")
    public double watermarkPercent;

    @SerializedName("video_guid")
    public String videoGuid;

    @SerializedName("audio_guid")
    public String audioGuid;

    @SerializedName("subtitle_guid")
    public String subtitleGuid;

    @SerializedName("media_guid")
    public String mediaGuid;

    @SerializedName("single_child_guid")
    public String singleChildGuid;

    @SerializedName("media_stream")
    public MediaStreamInfo mediaStream;

    @SerializedName("douban_id")
    public long doubanId;

    @SerializedName("imdb_id")
    public String imdbId;

    @SerializedName("trim_id")
    public String trimId;

    @SerializedName("air_date")
    public String airDate;

    @SerializedName("number_of_seasons")
    public int numberOfSeasons;

    @SerializedName("number_of_episodes")
    public int numberOfEpisodes;

    @SerializedName("local_number_of_seasons")
    public int localNumberOfSeasons;

    @SerializedName("local_number_of_episodes")
    public int localNumberOfEpisodes;

    public static class MediaStreamInfo {
        public List<String> resolutions;
        @SerializedName("audio_type")
        public Object audioType;
        @SerializedName("color_range_type")
        public Object colorRangeType;
    }

    /** 判断是否为文件夹/分类（可浏览进入） */
    public boolean isFolder() {
        return "Directory".equals(type) || "TV".equals(type)
                || "Folder".equals(type) || "folder".equals(type);
    }

    /** 判断是否为可播放的媒体（Movie: 电影, Video: 视频, Episode: 剧集） */
    public boolean isPlayable() {
        return "Movie".equals(type) || "Video".equals(type) || "Episode".equals(type);
    }

    /** 获取完整海报URL */
    public String getPosterUrl(String baseUrl) {
        if (poster != null && !poster.isEmpty()) {
            return baseUrl + "/v/api/v1/sys/img" + poster + "?w=400";
        }
        return null;
    }

    /** 获取显示标题 */
    public String getDisplayTitle() {
        if (tvTitle != null && !tvTitle.isEmpty()) {
            if (episodeNumber > 0) {
                return tvTitle + " 第" + episodeNumber + "集";
            }
            return tvTitle;
        }
        return title != null ? title : "";
    }

    /** 获取分类简要名称 */
    public String getCategoryLabel() {
        if (ancestorName != null && !ancestorName.isEmpty()) {
            return ancestorName;
        }
        if (ancestorCategory != null && !ancestorCategory.isEmpty()) {
            return ancestorCategory;
        }
        return parentTitle != null ? parentTitle : "未分类";
    }
}
