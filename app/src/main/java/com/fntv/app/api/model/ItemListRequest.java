package com.fntv.app.api.model;

import com.google.gson.annotations.SerializedName;
import java.util.Arrays;
import java.util.List;

/**
 * 项目列表请求参数
 * 正确格式来自抓包分析：
 * {
 *   "ancestor_guid": "...",
 *   "tags": { "type": ["Movie","TV","Directory","Video"] },
 *   "exclude_grouped_video": 1,
 *   "sort_type": "DESC",
 *   "sort_column": "create_time",
 *   "page_size": 22
 * }
 */
public class ItemListRequest {

    @SerializedName("ancestor_guid")
    public String ancestorGuid;

    public Tags tags;

    @SerializedName("exclude_grouped_video")
    public Integer excludeGroupedVideo;

    @SerializedName("sort_type")
    public String sortType;

    @SerializedName("sort_column")
    public String sortColumn;

    @SerializedName("page_size")
    public int pageSize;

    @SerializedName("page")
    public Integer page;

    /** 类型标签 */
    public static class Tags {
        public List<String> type;

        public Tags(List<String> type) {
            this.type = type;
        }
    }

    /** 浏览媒体库/文件夹中的所有内容 */
    public ItemListRequest(String ancestorGuid, List<String> typeTags,
                           boolean excludeGrouped, String sortColumn,
                           String sortType, int pageSize) {
        this.ancestorGuid = ancestorGuid;
        this.tags = new Tags(typeTags);
        this.excludeGroupedVideo = excludeGrouped ? Integer.valueOf(1) : Integer.valueOf(0);
        this.sortColumn = sortColumn;
        this.sortType = sortType;
        this.pageSize = pageSize;
    }

    /** 浏览媒体库内容（包含所有类型） */
    public static ItemListRequest browseLibrary(String ancestorGuid) {
        return new ItemListRequest(ancestorGuid,
                Arrays.asList("Movie", "TV", "Directory", "Video"),
                true, "create_time", "DESC", 50);
    }

    /** 浏览子文件夹/剧集（只展示可播放项） */
    public static ItemListRequest browseChildren(String ancestorGuid) {
        return new ItemListRequest(ancestorGuid,
                Arrays.asList("Episode", "Movie", "Video"),
                true, "episode_number", "ASC", 100);
    }

    /** 浏览文件夹（含子文件夹） */
    public static ItemListRequest browseFolder(String ancestorGuid) {
        return new ItemListRequest(ancestorGuid,
                Arrays.asList("Movie", "TV", "Directory", "Video", "Episode"),
                false, "sort_title", "ASC", 100);
    }

    /** 直播频道列表（不设 ancestor_guid / exclude_grouped_video） */
    public static ItemListRequest browseLiveChannels() {
        ItemListRequest req = new ItemListRequest(null,
                Arrays.asList("LiveChannel"),
                false, "sort_title", "ASC", 10000);
        req.excludeGroupedVideo = null; // 不序列化此字段
        req.page = 1;
        return req;
    }
}
