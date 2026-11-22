package com.fntv.app.data;

import com.fntv.app.api.FnApiManager;
import com.fntv.app.api.FnApiService;
import com.fntv.app.api.model.ApiResponse;
import com.fntv.app.api.model.ItemDetail;
import com.fntv.app.api.model.ItemListRequest;
import com.fntv.app.api.model.ItemListResponse;
import com.fntv.app.api.model.ItemMeta;
import com.fntv.app.api.model.MediaDbItem;
import com.fntv.app.api.model.PlayInfoResponse;
import com.fntv.app.api.model.PlayListItem;
import com.fntv.app.api.model.TagItem;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 媒体数据仓库：页面只依赖这里，不直接拼装 Retrofit 请求。
 */
public class MediaRepository {

    public interface DataCallback<T> {
        void onData(T data);

        void onError(String message);
    }

    private static MediaRepository instance;

    public static synchronized MediaRepository get() {
        if (instance == null) instance = new MediaRepository();
        return instance;
    }

    private FnApiService api() {
        return FnApiManager.getInstance().getApi();
    }

    private static String errorOf(ApiResponse<?> body) {
        if (body == null) return "服务器无响应";
        if (body.msg != null && !body.msg.isEmpty()) return body.msg;
        return "请求失败";
    }

    // ==================== 媒体库 ====================

    public void libraries(final DataCallback<List<MediaDbItem>> cb) {
        if (api() == null) {
            cb.onError("未配置服务器");
            return;
        }
        final Call<ApiResponse<List<MediaDbItem>>> call = api().getMediaDbList();
        call.enqueue(new Callback<ApiResponse<List<MediaDbItem>>>() {
            private boolean retried;

            @Override
            public void onResponse(Call<ApiResponse<List<MediaDbItem>>> c,
                                   Response<ApiResponse<List<MediaDbItem>>> response) {
                ApiResponse<List<MediaDbItem>> body = response.body();
                if (body != null && body.code == -2 && !retried) {
                    retried = true;
                    c.clone().enqueue(this);
                    return;
                }
                if (response.isSuccessful() && body != null && body.code == 0 && body.data != null) {
                    cb.onData(body.data);
                } else {
                    cb.onError(errorOf(body));
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<MediaDbItem>>> c, Throwable t) {
                cb.onError("网络错误：" + t.getMessage());
            }
        });
    }

    // ==================== 首页数据 ====================

    public void continueWatching(final DataCallback<List<PlayListItem>> cb) {
        if (api() == null) {
            cb.onError("未配置服务器");
            return;
        }
        api().getPlayList().enqueue(new Callback<ApiResponse<List<PlayListItem>>>() {
            private boolean retried;

            @Override
            public void onResponse(Call<ApiResponse<List<PlayListItem>>> c,
                                   Response<ApiResponse<List<PlayListItem>>> response) {
                ApiResponse<List<PlayListItem>> body = response.body();
                if (body != null && body.code == -2 && !retried) {
                    retried = true;
                    c.clone().enqueue(this);
                    return;
                }
                if (response.isSuccessful() && body != null && body.code == 0) {
                    if (body.data == null) cb.onData(java.util.Collections.<PlayListItem>emptyList());
                    else cb.onData(body.data);
                } else {
                    cb.onError(errorOf(body));
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<PlayListItem>>> c, Throwable t) {
                cb.onError("网络错误：" + t.getMessage());
            }
        });
    }

    public void liveChannels(final DataCallback<List<PlayListItem>> cb) {
        if (api() == null) {
            cb.onError("未配置服务器");
            return;
        }
        api().getItemList(ItemListRequest.browseLiveChannels())
                .enqueue(new Callback<ApiResponse<ItemListResponse>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<ItemListResponse>> c,
                                           Response<ApiResponse<ItemListResponse>> response) {
                        ApiResponse<ItemListResponse> body = response.body();
                        if (response.isSuccessful() && body != null && body.code == 0
                                && body.data != null && body.data.list != null) {
                            cb.onData(body.data.list);
                        } else {
                            cb.onData(java.util.Collections.<PlayListItem>emptyList());
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<ItemListResponse>> c, Throwable t) {
                        cb.onData(java.util.Collections.<PlayListItem>emptyList());
                    }
                });
    }

    /** 媒体库前 N 个条目（首页横向预览） */
    public void preview(final String ancestorGuid, final int limit,
                        final DataCallback<List<PlayListItem>> cb) {
        if (api() == null) {
            cb.onError("未配置服务器");
            return;
        }
        ItemListRequest req = new ItemListRequest(ancestorGuid,
                Arrays.asList("Movie", "TV", "Directory", "Video"),
                true, "create_time", "DESC", Math.max(limit, 20));
        api().getItemList(req).enqueue(new Callback<ApiResponse<ItemListResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<ItemListResponse>> c,
                                   Response<ApiResponse<ItemListResponse>> response) {
                ApiResponse<ItemListResponse> body = response.body();
                if (response.isSuccessful() && body != null && body.code == 0
                        && body.data != null && body.data.list != null) {
                    List<PlayListItem> list = body.data.list;
                    cb.onData(list.size() > limit ? list.subList(0, limit) : list);
                } else {
                    cb.onData(java.util.Collections.<PlayListItem>emptyList());
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<ItemListResponse>> c, Throwable t) {
                cb.onData(java.util.Collections.<PlayListItem>emptyList());
            }
        });
    }

    // ==================== 浏览 / 搜索 ====================

    public void items(String ancestorGuid, String sortColumn, String sortOrder, int pageSize,
                      final DataCallback<ItemListResponse> cb) {
        // exclude_grouped_video = 1：和首页预览一致，把文件夹里的视频收进文件夹卡片，
        // 不再把所有集/文件平铺出来
        ItemListRequest req = new ItemListRequest(ancestorGuid,
                Arrays.asList("Movie", "TV", "Directory", "Video"),
                true, sortColumn, sortOrder, pageSize);
        list(req, cb);
    }

    /** 文件夹里的内容（服务端只认 parent_guid，ancestor_guid 会返回空） */
    public void folderItems(String parentGuid, String sortColumn, String sortOrder, int pageSize,
                            final DataCallback<ItemListResponse> cb) {
        list(ItemListRequest.folderChildren(parentGuid, sortColumn, sortOrder, pageSize), cb);
    }

    private void list(ItemListRequest req, final DataCallback<ItemListResponse> cb) {
        if (api() == null) {
            cb.onError("未配置服务器");
            return;
        }
        api().getItemList(req).enqueue(new Callback<ApiResponse<ItemListResponse>>() {
            private boolean retried;

            @Override
            public void onResponse(Call<ApiResponse<ItemListResponse>> c,
                                   Response<ApiResponse<ItemListResponse>> response) {
                ApiResponse<ItemListResponse> body = response.body();
                if (body != null && body.code == -2 && !retried) {
                    retried = true;
                    c.clone().enqueue(this);
                    return;
                }
                if (response.isSuccessful() && body != null && body.code == 0 && body.data != null) {
                    cb.onData(body.data);
                } else {
                    cb.onError(errorOf(body));
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<ItemListResponse>> c, Throwable t) {
                cb.onError("网络错误：" + t.getMessage());
            }
        });
    }

    /** 全库按类型浏览（电影 / 剧集），不限定媒体库 */
    public void browseType(final String type, String sortColumn, String sortOrder, int pageSize,
                           final DataCallback<ItemListResponse> cb) {
        if (api() == null) {
            cb.onError("未配置服务器");
            return;
        }
        ItemListRequest req = new ItemListRequest(null,
                Arrays.asList(type), false, sortColumn, sortOrder, pageSize);
        api().getItemList(req).enqueue(new Callback<ApiResponse<ItemListResponse>>() {
            private boolean retried;

            @Override
            public void onResponse(Call<ApiResponse<ItemListResponse>> c,
                                   Response<ApiResponse<ItemListResponse>> response) {
                ApiResponse<ItemListResponse> body = response.body();
                if (body != null && body.code == -2 && !retried) {
                    retried = true;
                    c.clone().enqueue(this);
                    return;
                }
                if (response.isSuccessful() && body != null && body.code == 0 && body.data != null) {
                    cb.onData(body.data);
                } else {
                    cb.onError(errorOf(body));
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<ItemListResponse>> c, Throwable t) {
                cb.onError("网络错误：" + t.getMessage());
            }
        });
    }

    /** 收藏 / 取消收藏（服务端只认 item_guid） */
    public void setFavorite(String itemGuid, boolean favorite, final DataCallback<Boolean> cb) {
        if (api() == null) {
            cb.onError("未配置服务器");
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("item_guid", itemGuid);
        Call<ApiResponse<Boolean>> call = favorite
                ? api().addFavorite(body) : api().removeFavorite(body);
        call.enqueue(new Callback<ApiResponse<Boolean>>() {
            @Override
            public void onResponse(Call<ApiResponse<Boolean>> c,
                                   Response<ApiResponse<Boolean>> response) {
                ApiResponse<Boolean> b = response.body();
                if (response.isSuccessful() && b != null && b.code == 0) {
                    cb.onData(Boolean.TRUE);
                } else {
                    cb.onError(errorOf(b));
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Boolean>> c, Throwable t) {
                cb.onError("网络错误：" + t.getMessage());
            }
        });
    }

    /** 查询某个条目是否已收藏（用 item 接口，剧集页拿到的 is_favorite 属于单集） */
    public void isFavorite(String itemGuid, final DataCallback<Boolean> cb) {
        if (api() == null) {
            cb.onError("未配置服务器");
            return;
        }
        api().getItem(itemGuid).enqueue(new Callback<ApiResponse<ItemDetail>>() {
            @Override
            public void onResponse(Call<ApiResponse<ItemDetail>> c,
                                   Response<ApiResponse<ItemDetail>> response) {
                ApiResponse<ItemDetail> b = response.body();
                if (response.isSuccessful() && b != null && b.code == 0 && b.data != null) {
                    cb.onData(b.data.isFavorite == 1);
                } else {
                    cb.onError(errorOf(b));
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<ItemDetail>> c, Throwable t) {
                cb.onError(t.getMessage() == null ? "请求失败" : t.getMessage());
            }
        });
    }

    public void search(String query, final DataCallback<List<PlayListItem>> cb) {
        if (api() == null) {
            cb.onError("未配置服务器");
            return;
        }
        api().search(query).enqueue(new Callback<ApiResponse<List<PlayListItem>>>() {
            private boolean retried;

            @Override
            public void onResponse(Call<ApiResponse<List<PlayListItem>>> c,
                                   Response<ApiResponse<List<PlayListItem>>> response) {
                ApiResponse<List<PlayListItem>> body = response.body();
                if (body != null && body.code == -2 && !retried) {
                    retried = true;
                    c.clone().enqueue(this);
                    return;
                }
                if (response.isSuccessful() && body != null && body.code == 0) {
                    cb.onData(body.data == null
                            ? java.util.Collections.<PlayListItem>emptyList() : body.data);
                } else {
                    cb.onError(errorOf(body));
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<PlayListItem>>> c, Throwable t) {
                cb.onError("网络错误：" + t.getMessage());
            }
        });
    }

    public void favorites(final DataCallback<List<PlayListItem>> cb) {
        if (api() == null) {
            cb.onError("未配置服务器");
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("page", 1);
        body.put("page_size", 100);
        api().getFavoriteList(body).enqueue(new Callback<ApiResponse<ItemListResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<ItemListResponse>> c,
                                   Response<ApiResponse<ItemListResponse>> response) {
                ApiResponse<ItemListResponse> b = response.body();
                if (response.isSuccessful() && b != null && b.code == 0
                        && b.data != null && b.data.list != null) {
                    cb.onData(b.data.list);
                } else {
                    cb.onData(java.util.Collections.<PlayListItem>emptyList());
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<ItemListResponse>> c, Throwable t) {
                cb.onData(java.util.Collections.<PlayListItem>emptyList());
            }
        });
    }

    // ==================== 详情元数据（分级 / 类型 / 国家 / 媒体库） ====================

    private final Map<Integer, String> genreNames = new HashMap<>();
    private final Map<String, String> countryNames = new HashMap<>();
    private boolean tagsLoading;
    private boolean tagsLoaded;
    private final List<Runnable> tagWaiters = new java.util.ArrayList<>();

    /**
     * 大图推荐位的副信息：分级 / 类型 / 国家 / 媒体库。
     * 单集要先上到「季」再上到「剧集」才有这些字段。
     */
    public void itemMeta(PlayListItem item, final DataCallback<ItemMeta> cb) {
        if (api() == null || item == null || item.guid == null) {
            cb.onError("未配置服务器");
            return;
        }
        ensureTags(new Runnable() {
            @Override
            public void run() {
                String start = item.guid;
                if ("Episode".equals(item.type) && item.parentGuid != null
                        && !item.parentGuid.isEmpty()) {
                    start = item.parentGuid;
                }
                fetchItemMeta(start, cb, 2);
            }
        });
    }

    private void ensureTags(final Runnable done) {
        if (tagsLoaded) {
            done.run();
            return;
        }
        synchronized (tagWaiters) {
            tagWaiters.add(done);
            if (tagsLoading) return;
            tagsLoading = true;
        }
        if (api() == null) {
            finishTags();
            return;
        }
        api().getGenreTags("zh-CN").enqueue(new Callback<ApiResponse<List<TagItem>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<TagItem>>> call,
                                   Response<ApiResponse<List<TagItem>>> resp) {
                ApiResponse<List<TagItem>> body = resp.body();
                if (body != null && body.data != null) {
                    for (TagItem t : body.data) genreNames.put(t.id, t.value);
                }
                loadCountries();
            }

            @Override
            public void onFailure(Call<ApiResponse<List<TagItem>>> call, Throwable t) {
                loadCountries();
            }
        });
    }

    private void loadCountries() {
        if (api() == null) {
            finishTags();
            return;
        }
        api().getCountryTags("zh-CN").enqueue(new Callback<ApiResponse<List<TagItem>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<TagItem>>> call,
                                   Response<ApiResponse<List<TagItem>>> resp) {
                ApiResponse<List<TagItem>> body = resp.body();
                if (body != null && body.data != null) {
                    for (TagItem t : body.data) {
                        if (t.key != null) countryNames.put(t.key.toUpperCase(), t.value);
                    }
                }
                finishTags();
            }

            @Override
            public void onFailure(Call<ApiResponse<List<TagItem>>> call, Throwable t) {
                finishTags();
            }
        });
    }

    private void finishTags() {
        List<Runnable> run;
        synchronized (tagWaiters) {
            tagsLoaded = true;
            tagsLoading = false;
            run = new java.util.ArrayList<>(tagWaiters);
            tagWaiters.clear();
        }
        for (Runnable r : run) r.run();
    }

    private void fetchItemMeta(final String guid, final DataCallback<ItemMeta> cb,
                               final int depth) {
        api().getItem(guid).enqueue(new Callback<ApiResponse<ItemDetail>>() {
            @Override
            public void onResponse(Call<ApiResponse<ItemDetail>> call,
                                   Response<ApiResponse<ItemDetail>> resp) {
                ApiResponse<ItemDetail> body = resp.body();
                ItemDetail d = body != null ? body.data : null;
                if (d == null) {
                    cb.onError(body != null ? errorOf(body) : "没有拿到详情");
                    return;
                }
                boolean hasMeta = !isBlank(d.contentRatings)
                        || (d.genres != null && !d.genres.isEmpty())
                        || (d.productionCountries != null && !d.productionCountries.isEmpty());
                if (!hasMeta && depth > 0 && !isBlank(d.parentGuid)) {
                    fetchItemMeta(d.parentGuid, cb, depth - 1);
                    return;
                }
                cb.onData(toMeta(d));
            }

            @Override
            public void onFailure(Call<ApiResponse<ItemDetail>> call, Throwable t) {
                cb.onError(t.getMessage() == null ? "请求失败" : t.getMessage());
            }
        });
    }

    private ItemMeta toMeta(ItemDetail d) {
        ItemMeta meta = new ItemMeta();
        meta.rating = isBlank(d.contentRatings) ? null : d.contentRatings.trim();

        StringBuilder genres = new StringBuilder();
        if (d.genres != null) {
            for (Integer id : d.genres) {
                String name = id == null ? null : genreNames.get(id);
                if (isBlank(name)) continue;
                if (genres.length() > 0) genres.append(" · ");
                genres.append(name);
            }
        }
        meta.genres = genres.length() == 0 ? null : genres.toString();

        StringBuilder countries = new StringBuilder();
        if (d.productionCountries != null) {
            for (String code : d.productionCountries) {
                if (isBlank(code)) continue;
                for (String part : code.split(",")) {
                    String key = part.trim().toUpperCase();
                    if (key.isEmpty()) continue;
                    String name = countryNames.containsKey(key) ? countryNames.get(key) : key;
                    if (countries.length() > 0) countries.append(" · ");
                    countries.append(name);
                }
            }
        }
        meta.countries = countries.length() == 0 ? null : countries.toString();
        return meta;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    // ==================== 详情 / 剧集 ====================

    public void detail(String itemGuid, final DataCallback<PlayInfoResponse> cb) {
        if (api() == null) {
            cb.onError("未配置服务器");
            return;
        }
        Map<String, String> body = new HashMap<>();
        body.put("item_guid", itemGuid);
        api().getPlayInfo(body).enqueue(new Callback<ApiResponse<PlayInfoResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<PlayInfoResponse>> c,
                                   Response<ApiResponse<PlayInfoResponse>> response) {
                ApiResponse<PlayInfoResponse> b = response.body();
                if (response.isSuccessful() && b != null && b.code == 0 && b.data != null) {
                    cb.onData(b.data);
                } else {
                    cb.onError(errorOf(b));
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<PlayInfoResponse>> c, Throwable t) {
                cb.onError("网络错误：" + t.getMessage());
            }
        });
    }

    /** 按 guid 直接取副信息（分级 / 类型 / 国家），详情页用 */
    public void itemMetaByGuid(String guid, final DataCallback<ItemMeta> cb) {
        if (api() == null || guid == null || guid.isEmpty()) {
            cb.onError("未配置服务器");
            return;
        }
        ensureTags(new Runnable() {
            @Override
            public void run() {
                fetchItemMeta(guid, cb, 2);
            }
        });
    }

    /** 条目详情（GET item），详情页用来取 logos 片名艺术字 */
    public void item(String itemGuid, final DataCallback<ItemDetail> cb) {
        if (api() == null || itemGuid == null || itemGuid.isEmpty()) {
            cb.onError("未配置服务器");
            return;
        }
        api().getItem(itemGuid).enqueue(new Callback<ApiResponse<ItemDetail>>() {
            @Override
            public void onResponse(Call<ApiResponse<ItemDetail>> c,
                                   Response<ApiResponse<ItemDetail>> response) {
                ApiResponse<ItemDetail> b = response.body();
                if (response.isSuccessful() && b != null && b.code == 0 && b.data != null) {
                    cb.onData(b.data);
                } else {
                    cb.onError(errorOf(b));
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<ItemDetail>> c, Throwable t) {
                cb.onError(t.getMessage() == null ? "加载失败" : t.getMessage());
            }
        });
    }

    /** 取某一项的父级 guid（集 → 季 → 剧），用于详情页上溯 */    public void itemParent(String itemGuid, final DataCallback<String> cb) {
        if (api() == null) {
            cb.onError("未配置服务器");
            return;
        }
        api().getItem(itemGuid).enqueue(new Callback<ApiResponse<ItemDetail>>() {
            @Override
            public void onResponse(Call<ApiResponse<ItemDetail>> c,
                                   Response<ApiResponse<ItemDetail>> response) {
                ApiResponse<ItemDetail> b = response.body();
                if (response.isSuccessful() && b != null && b.code == 0 && b.data != null) {
                    cb.onData(b.data.parentGuid);
                } else {
                    cb.onError(errorOf(b));
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<ItemDetail>> c, Throwable t) {
                cb.onError("网络错误：" + t.getMessage());
            }
        });
    }

    public void seasons(String itemGuid, final DataCallback<List<PlayListItem>> cb) {
        if (api() == null) {
            cb.onError("未配置服务器");
            return;
        }
        api().getSeasonList(itemGuid).enqueue(new Callback<ApiResponse<List<PlayListItem>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<PlayListItem>>> c,
                                   Response<ApiResponse<List<PlayListItem>>> response) {
                ApiResponse<List<PlayListItem>> b = response.body();
                if (response.isSuccessful() && b != null && b.code == 0 && b.data != null) {
                    cb.onData(b.data);
                } else {
                    cb.onData(java.util.Collections.<PlayListItem>emptyList());
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<PlayListItem>>> c, Throwable t) {
                cb.onData(java.util.Collections.<PlayListItem>emptyList());
            }
        });
    }

    public void episodes(String seasonGuid, final DataCallback<List<PlayListItem>> cb) {
        if (api() == null) {
            cb.onError("未配置服务器");
            return;
        }
        api().getEpisodeList(seasonGuid).enqueue(new Callback<ApiResponse<List<PlayListItem>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<PlayListItem>>> c,
                                   Response<ApiResponse<List<PlayListItem>>> response) {
                ApiResponse<List<PlayListItem>> b = response.body();
                if (response.isSuccessful() && b != null && b.code == 0 && b.data != null) {
                    cb.onData(b.data);
                } else {
                    cb.onData(java.util.Collections.<PlayListItem>emptyList());
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<PlayListItem>>> c, Throwable t) {
                cb.onData(java.util.Collections.<PlayListItem>emptyList());
            }
        });
    }
}
