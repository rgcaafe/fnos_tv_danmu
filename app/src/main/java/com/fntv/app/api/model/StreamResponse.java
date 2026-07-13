package com.fntv.app.api.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class StreamResponse {

    @SerializedName("direct_link_qualities")
    public List<DirectLinkQuality> directLinkQualities;

    public List<Quality> qualities;

    @SerializedName("video_stream")
    public VideoStreamInfo videoStream;

    @SerializedName("audio_streams")
    public List<AudioStreamInfo> audioStreams;

    @SerializedName("subtitle_streams")
    public List<SubtitleStreamInfo> subtitleStreams;

    @SerializedName("file_stream")
    public FileStreamInfo fileStream;

    public ResponseHeader header;

    @SerializedName("cloud_storage_info")
    public CloudStorageInfo cloudStorageInfo;

    /** 响应头（含 Cookie） */
    public static class ResponseHeader {
        public List<String> Cookie;
    }

    /** 云存储信息 */
    public static class CloudStorageInfo {
        @SerializedName("cloud_storage_type")
        public int cloudStorageType;
        public boolean valid;
        @SerializedName("cloud_nick_name")
        public String cloudNickName;
        @SerializedName("is_vip")
        public boolean isVip;
        @SerializedName("quark_vip_type")
        public String quarkVipType;
    }

    /** 直链质量 */
    public static class DirectLinkQuality {
        public int bitrate;
        public String resolution;
        public boolean progressive;
        public String url;
        @SerializedName("is_m3u8")
        public boolean isM3u8;
        @SerializedName("expired_at")
        public long expiredAt;
    }

    /** 质量（可能不含url） */
    public static class Quality {
        public int bitrate;
        public String resolution;
        public boolean progressive;
        @SerializedName("is_m3u8")
        public boolean isM3u8;
    }

    /** 视频流信息 */
    public static class VideoStreamInfo {
        public String guid;
        public int width;
        public int height;
        public int bps;
        public String codec;
        @SerializedName("codec_name")
        public String codecName;
        public String profile;
        public String level;
        @SerializedName("bit_depth")
        public int bitDepth;
        @SerializedName("dv_profile")
        public int dvProfile;
        @SerializedName("r_frame_rate")
        public String rFrameRate;
        @SerializedName("color_space")
        public String colorSpace;
        @SerializedName("color_transfer")
        public String colorTransfer;
        @SerializedName("color_primaries")
        public String colorPrimaries;
        @SerializedName("pix_fmt")
        public String pixFmt;
        @SerializedName("bits_per_raw_sample")
        public String bitsPerRawSample;
        public int duration;
    }

    /** 字幕流信息 */
    public static class SubtitleStreamInfo {
        public String guid;
        public String title;
        public String language;
        @SerializedName("codec_name")
        public String codecName;
        @SerializedName("is_external")
        public int isExternal;
        @SerializedName("is_default")
        public int isDefault;
    }

    /** 音频流信息 */
    public static class AudioStreamInfo {
        public String guid;
        public int bps;
        public int channels;
        @SerializedName("sample_rate")
        public int sampleRate;
        public String codec;
        @SerializedName("codec_name")
        public String codecName;
        @SerializedName("audio_type")
        public String audioType;
        public String language;
        public String profile;
        @SerializedName("channel_layout")
        public String channelLayout;
        @SerializedName("is_default")
        public int isDefault;
    }

    /** 文件信息 */
    public static class FileStreamInfo {
        public long size;
        public String path;
        @SerializedName("file_name")
        public String fileName;
        public int duration;
    }
}
