package com.fntv.app.api.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * 播放信息响应（对应 Electron types.ts PlayInfo）
 */
public class PlayInfoResponse {

    public String guid;
    public String type;
    @SerializedName("parent_guid")
    public String parentGuid;
    @SerializedName("media_guid")
    public String mediaGuid;
    @SerializedName("video_guid")
    public String videoGuid;
    @SerializedName("audio_guid")
    public String audioGuid;
    @SerializedName("subtitle_guid")
    public String subtitleGuid;
    public long ts;

    public ItemInfo item;

    public static class ItemInfo {
        public String guid;
        public String title;
        @SerializedName("original_title")
        public String originalTitle;
        @SerializedName("tv_title")
        public String tvTitle;
        @SerializedName("parent_title")
        public String parentTitle;
        public String overview;
        public String poster;
        public String posters;          // 海报路径
        public String backdrops;        // 背景大图路径
        @SerializedName("still_path")
        public String stillPath;        // 剧照

        @SerializedName("vote_average")
        public String voteAverage;

        public int runtime;
        public long duration;

        @SerializedName("episode_number")
        public int episodeNumber;
        @SerializedName("season_number")
        public int seasonNumber;

        @SerializedName("number_of_episodes")
        public int numberOfEpisodes;
        @SerializedName("number_of_seasons")
        public int numberOfSeasons;

        @SerializedName("air_date")
        public String airDate;
        @SerializedName("release_date")
        public String releaseDate;
        public String status;
        public int watched;

        @SerializedName("is_favorite")
        public int isFavorite;

        public List<Integer> genres;
        @SerializedName("production_countries")
        public List<String> productionCountries;

        @SerializedName("media_stream")
        public MediaStreamInfo mediaStream;
    }

    public static class MediaStreamInfo {
        public List<String> resolutions;
        @SerializedName("audio_type")
        public List<String> audioType;
        @SerializedName("color_range_type")
        public List<String> colorRangeType;
    }

    @SerializedName("live_channels")
    public List<LiveChannelStream> liveChannels;

    /** 直播频道流信息 */
    public static class LiveChannelStream {
        public String guid;
        public String path;
        @SerializedName("file_name")
        public String fileName;
        @SerializedName("can_play")
        public int canPlay;
    }

    /** 获取显示用的海报路径 */
    public String getPosterPath() {
        if (item != null && item.posters != null && !item.posters.isEmpty()) return item.posters;
        if (item != null && item.poster != null && !item.poster.isEmpty()) return item.poster;
        if (item != null && item.stillPath != null && !item.stillPath.isEmpty()) return item.stillPath;
        return null;
    }

    /** 获取背景图路径 */
    public String getBackdropPath() {
        if (item != null && item.backdrops != null && !item.backdrops.isEmpty()) return item.backdrops;
        return getPosterPath();
    }
}
