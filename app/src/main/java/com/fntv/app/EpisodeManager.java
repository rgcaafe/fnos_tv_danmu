package com.fntv.app;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.fntv.app.api.FnApiManager;
import com.fntv.app.api.model.ApiResponse;
import com.fntv.app.api.model.PlayListItem;

import java.util.List;

import retrofit2.Call;
import retrofit2.Response;

/** 剧集列表管理 — 加载、切换剧集 */
public class EpisodeManager {

    public interface Callback {
        String getBaseUrl();
        String getParentGuid();
        String getItemGuid();
        int getEpisodeNumber();
        FnApiManager getApiManager();
        Context getContext();
        void onSwitchEpisode(String guid, String title);
    }

    private List<PlayListItem> episodeList;
    private int currentEpIndex = -1;
    private boolean loadingEpisodes = false;
    private final Callback cb;
    private final Button btnEpisodeList;
    private final Button btnNextEp;
    private String currentGuid;
    private String currentTitle;

    private static final String TAG = "Player";

    public EpisodeManager(Callback cb, Button btnEpisodeList, Button btnNextEp) {
        this.cb = cb;
        this.btnEpisodeList = btnEpisodeList;
        this.btnNextEp = btnNextEp;
    }

    /** 是否有下一集 */
    public boolean hasNext() {
        return episodeList != null && currentEpIndex >= 0 && currentEpIndex < episodeList.size() - 1;
    }

    /** 是否正在加载 */
    public boolean isLoading() { return loadingEpisodes; }

    /** 是否已加载列表 */
    public boolean isLoaded() { return episodeList != null && !episodeList.isEmpty(); }

    /** 加载剧集列表 */
    public void loadList(String parentGuid) {
        if (parentGuid == null || parentGuid.isEmpty()) return;
        loadingEpisodes = true;
        Log.d(TAG, "getEpisodeList 请求: " + cb.getBaseUrl() + "/v/api/v1/episode/list/" + parentGuid);
        cb.getApiManager().getApi().getEpisodeList(parentGuid).enqueue(
                new retrofit2.Callback<ApiResponse<List<PlayListItem>>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<List<PlayListItem>>> call,
                                           Response<ApiResponse<List<PlayListItem>>> resp) {
                        loadingEpisodes = false;
                        Log.d(TAG, "getEpisodeList 响应 code=" + resp.code()
                                + " body=" + (resp.body() != null ? "code=" + resp.body().code + " size="
                                + (resp.body().data != null ? resp.body().data.size() : "null") : "null"));
                        if (resp.isSuccessful() && resp.body() != null && resp.body().code == 0
                                && resp.body().data != null && !resp.body().data.isEmpty()) {
                            episodeList = resp.body().data;
                            currentEpIndex = -1;
                            int epNum = cb.getEpisodeNumber();
                            String itemGuid = cb.getItemGuid();
                            for (int i = 0; i < episodeList.size(); i++) {
                                PlayListItem ep = episodeList.get(i);
                                if (ep.guid.equals(itemGuid)) {
                                    currentEpIndex = i;
                                    break;
                                }
                                if (currentEpIndex < 0 && epNum > 0 && ep.episodeNumber == epNum) {
                                    currentEpIndex = i;
                                }
                            }
                            Log.d(TAG, "getEpisodeList 成功: " + episodeList.size() + " 集, currentIdx=" + currentEpIndex
                                    + " epNum=" + epNum + " itemGuid=" + itemGuid);
                            btnEpisodeList.setVisibility(View.VISIBLE);
                            updateNextBtn();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<List<PlayListItem>>> call, Throwable t) {
                        loadingEpisodes = false;
                        Log.e(TAG, "getEpisodeList 失败: " + t.getMessage());
                    }
                });
    }

    /** 播放下一个剧集 */
    public void playNext() {
        if (!hasNext()) return;
        PlayListItem next = episodeList.get(currentEpIndex + 1);
        currentEpIndex++;
        currentGuid = next.guid;
        currentTitle = next.title;
        updateNextBtn();
        cb.onSwitchEpisode(currentGuid, currentTitle);
    }

    /** 显示剧集选择器 */
    public void showPicker() {
        if (episodeList == null || episodeList.isEmpty()) return;
        final String[] items = new String[episodeList.size()];
        for (int i = 0; i < episodeList.size(); i++) {
            PlayListItem ep = episodeList.get(i);
            items[i] = "EP" + (ep.episodeNumber > 0 ? ep.episodeNumber : (i + 1))
                    + "  " + (ep.title != null ? ep.title : "");
        }
        PlayerDialog.list(cb.getContext(), "选择剧集", items, currentEpIndex, which -> {
            if (which >= 0 && which < episodeList.size()) {
                PlayListItem s = episodeList.get(which);
                currentEpIndex = which;
                currentGuid = s.guid;
                currentTitle = s.title;
                updateNextBtn();
                cb.onSwitchEpisode(currentGuid, currentTitle);
            }
        });
    }

    /** 重置状态（切换到新剧时调用） */
    public void reset() {
        episodeList = null;
        currentEpIndex = -1;
        loadingEpisodes = false;
    }

    private void updateNextBtn() {
        btnNextEp.setVisibility(hasNext() ? View.VISIBLE : View.GONE);
    }
}
