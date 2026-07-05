package com.fntv.app.api.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * 媒体库信息
 * 来自 /v/api/v1/mediadb/list 接口
 */
public class MediaDbItem {

    public String guid;
    public String title;
    public String poster;
    public List<String> posters;
    public String category;          // "Mix", "TV" 等
    @SerializedName("view_type")
    public int viewType;
    @SerializedName("poster_type")
    public int posterType;
    @SerializedName("refresh_disabled")
    public boolean refreshDisabled;

    /** 获取第一张海报路径，拼成完整URL用 */
    public String getFirstPoster() {
        if (poster != null && !poster.isEmpty()) return poster;
        if (posters != null && !posters.isEmpty()) return posters.get(0);
        return null;
    }
}
