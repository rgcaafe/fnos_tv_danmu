package com.fntv.app.api.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * 条目详情（GET /v/api/v1/item/{guid}）。
 * 只取页面展示需要的字段：分级 / 类型 / 出品国家 / 所属媒体库。
 */
public class ItemDetail {

    public String guid;
    public String type;
    public String title;

    @SerializedName("tv_title")
    public String tvTitle;

    @SerializedName("parent_guid")
    public String parentGuid;

    @SerializedName("ancestor_guid")
    public String ancestorGuid;

    @SerializedName("ancestor_name")
    public String ancestorName;

    @SerializedName("ancestor_category")
    public String ancestorCategory;

    public List<Integer> genres;

    @SerializedName("content_ratings")
    public String contentRatings;

    @SerializedName("production_countries")
    public List<String> productionCountries;

    @SerializedName("vote_average")
    public String voteAverage;

    @SerializedName("is_favorite")
    public int isFavorite;

    @SerializedName("number_of_episodes")
    public int numberOfEpisodes;

    @SerializedName("number_of_seasons")
    public int numberOfSeasons;

    @SerializedName("release_date")
    public String releaseDate;

    @SerializedName("air_date")
    public String airDate;

    public String overview;

    /** 片名艺术字（webp，带透明通道），网页/App 用同一张 */
    public String logos;
}
