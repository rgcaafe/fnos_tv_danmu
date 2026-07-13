package com.fntv.app.api;

import com.fntv.app.api.model.*;
import java.util.List;
import java.util.Map;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

/**
 * 飞牛影视 API 接口定义
 * 基于抓包分析 + Electron 版分析
 */
public interface FnApiService {

    // ========== 认证 ==========
    @POST("api/v1/login")
    Call<ApiResponse<LoginResponseData>> login(@Body LoginRequest req);

    @GET("api/v1/sys/config")
    Call<ApiResponse<Object>> getSysConfig();

    @GET("api/v1/sys/version")
    Call<ApiResponse<Object>> getVersion();

    @POST("api/v1/auth")
    Call<ApiResponse<FnAuthResponse>> auth(@Body Object req);

    @POST("api/v1/logout")
    Call<ApiResponse<Object>> logout();

    // ========== 用户 ==========
    @GET("api/v1/user/info")
    Call<ApiResponse<UserInfoResponse>> getUserInfo();

    // ========== 媒体库 ==========
    @GET("api/v1/mediadb/list")
    Call<ApiResponse<List<MediaDbItem>>> getMediaDbList();

    @GET("api/v1/mediadb/sum")
    Call<ApiResponse<Map<String, Integer>>> getMediaDbSum();

    // ========== 媒体浏览 ==========
    @POST("api/v1/item/list")
    Call<ApiResponse<ItemListResponse>> getItemList(@Body ItemListRequest req);

    @GET("api/v1/episode/list/{id}")
    Call<ApiResponse<List<PlayListItem>>> getEpisodeList(@Path("id") String id);

    @GET("api/v1/season/list/{id}")
    Call<ApiResponse<List<PlayListItem>>> getSeasonList(@Path("id") String id);

    // ========== 播放 ==========
    @POST("api/v1/play/info")
    Call<ApiResponse<PlayInfoResponse>> getPlayInfo(@Body Object req);

    @POST("api/v1/play/quality")
    Call<ApiResponse<Object>> getPlayQuality(@Body Object req);

    @POST("api/v1/play/play")
    Call<ApiResponse<PlayLinkResponse>> getPlayLink(@Body Object req);

    @GET("api/v1/stream/list/{guid}")
    Call<ApiResponse<Object>> getStreamList(@Path("guid") String guid);

    @POST("api/v1/stream")
    Call<ApiResponse<StreamResponse>> getStream(@Body Object req);

    @GET("api/v1/media/range/{guid}")
    Call<ResponseBody> getVideoUrl(@Path("guid") String guid);

    // ========== 状态记录 ==========
    @POST("api/v1/item/watched")
    Call<ApiResponse<Object>> setWatched(@Body Object req);

    @POST("api/v1/play/record")
    Call<ApiResponse<Object>> recordPlayStatus(@Body Object req);

    @GET("api/v1/play/list")
    Call<ApiResponse<List<PlayListItem>>> getPlayList();

    // ========== 字幕 ==========
    @GET("api/v1/subtitle/dl/{id}")
    Call<ResponseBody> downloadSubtitle(@Path("id") String id);
}
