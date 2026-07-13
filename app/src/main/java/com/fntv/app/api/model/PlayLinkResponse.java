package com.fntv.app.api.model;

import com.google.gson.annotations.SerializedName;

public class PlayLinkResponse {

    @SerializedName("play_link")
    public String playLink;

    @SerializedName("media_guid")
    public String mediaGuid;

    @SerializedName("video_guid")
    public String videoGuid;

    @SerializedName("audio_guid")
    public String audioGuid;

    @SerializedName("subtitle_guid")
    public String subtitleGuid;

    @SerializedName("subtitle_link")
    public String subtitleLink;

    @SerializedName("video_index")
    public int videoIndex;

    @SerializedName("audio_index")
    public int audioIndex;

    @SerializedName("subtitle_index")
    public int subtitleIndex;

    @SerializedName("hls_time")
    public int hlsTime;
}
