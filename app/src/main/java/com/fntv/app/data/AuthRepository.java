package com.fntv.app.data;

import android.content.Context;

import com.fntv.app.api.FnApiManager;
import com.fntv.app.api.model.ApiResponse;
import com.fntv.app.api.model.LoginRequest;
import com.fntv.app.api.model.LoginResponseData;
import com.fntv.app.core.Prefs;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 认证仓库：HTTP 账号登录 / 登出。
 * FN ID 登录由 FnIdLoginHelper 完成，成功后同样落到这里保存 token。
 */
public final class AuthRepository {

    public interface LoginCallback {
        void onSuccess();

        void onError(String message);
    }

    private AuthRepository() {
    }

    public static String normalizeHost(String host) {
        String h = host == null ? "" : host.trim();
        if (h.isEmpty()) return h;
        if (!h.startsWith("http://") && !h.startsWith("https://")) h = "http://" + h;
        return h.replaceAll("/+$", "");
    }

    public static void loginHttp(String host, String user, String pass, final LoginCallback callback) {
        final String base = normalizeHost(host);
        FnApiManager.getInstance().updateBaseUrl(base);
        if (FnApiManager.getInstance().getApi() == null) {
            callback.onError("服务器地址无效");
            return;
        }
        FnApiManager.getInstance().getApi().login(new LoginRequest(user, pass))
                .enqueue(new Callback<ApiResponse<LoginResponseData>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<LoginResponseData>> call,
                                           Response<ApiResponse<LoginResponseData>> response) {
                        ApiResponse<LoginResponseData> body = response.body();
                        if (response.isSuccessful() && body != null && body.code == 0
                                && body.data != null && body.data.token != null) {
                            FnApiManager.getInstance().setToken(body.data.token);
                            callback.onSuccess();
                        } else if (body != null && body.msg != null && !body.msg.isEmpty()) {
                            callback.onError(body.msg);
                        } else {
                            callback.onError("登录失败，请检查服务器地址与账号");
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<LoginResponseData>> call, Throwable t) {
                        callback.onError("网络连接失败：" + t.getMessage());
                    }
                });
    }

    public static void logout() {
        FnApiManager.getInstance().setToken(null);
    }

    public static void prepare(Context context, String host) {
        String base = normalizeHost(host);
        if (!base.isEmpty() && FnApiManager.getInstance().getApi() == null) {
            FnApiManager.getInstance().updateBaseUrl(base);
        }
        if (FnApiManager.getInstance().getToken() == null) {
            String token = Prefs.str(context, Prefs.TOKEN, "");
            if (!token.isEmpty()) FnApiManager.getInstance().setToken(token);
        }
    }
}
