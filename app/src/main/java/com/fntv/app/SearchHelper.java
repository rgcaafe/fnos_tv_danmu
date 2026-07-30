package com.fntv.app;

import com.fntv.app.api.FnApiManager;
import com.fntv.app.api.model.ApiResponse;
import com.fntv.app.api.model.PlayListItem;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SearchHelper {

    public interface SearchCallback {
        void onResults(List<PlayListItem> results);
        void onEmpty();
        void onError(String msg);
    }

    public static void search(FnApiManager apiManager, String query, SearchCallback callback) {
        final int[] retryCount = {1};
        apiManager.getApi().search(query).enqueue(new Callback<ApiResponse<List<PlayListItem>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<PlayListItem>>> call,
                                   Response<ApiResponse<List<PlayListItem>>> response) {
                if (response.body() != null && response.body().code == -2 && retryCount[0] > 0) {
                    retryCount[0]--;
                    call.clone().enqueue(this);
                    return;
                }

                if (response.isSuccessful() && response.body() != null && response.body().code == 0) {
                    if (response.body().data != null && !response.body().data.isEmpty()) {
                        callback.onResults(response.body().data);
                    } else {
                        callback.onEmpty();
                    }
                } else {
                    callback.onError("搜索失败");
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<PlayListItem>>> call, Throwable t) {
                callback.onError("网络错误: " + t.getMessage());
            }
        });
    }
}
