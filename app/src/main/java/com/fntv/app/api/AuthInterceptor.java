package com.fntv.app.api;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okio.Buffer;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * 核心拦截器：注入签名
 */
public class AuthInterceptor implements Interceptor {
    private String token;
    private String referer;

    public void setToken(String token) {
        this.token = token;
    }

    public String getToken() {
        return token;
    }

    public void setReferer(String referer) {
        this.referer = referer;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request original = chain.request();

        String urlPath = original.url().encodedPath();
        String jsonBody = null;
        if (original.body() != null) {
            Buffer buffer = new Buffer();
            original.body().writeTo(buffer);
            jsonBody = buffer.readString(Charset.forName("UTF-8"));
        }

        String authx = FnAuthUtils.genAuthx(urlPath, jsonBody);

        Request.Builder requestBuilder = original.newBuilder()
                .header("Content-Type", "application/json")
                .header("Authx", authx)
                .header("Cookie", "mode=relay")
                .header("x-trim-client", "web")
                .header("x-trim-client-version", "608");

        if (token != null) {
            requestBuilder.header("Authorization", token);
        }

        if (referer != null && !referer.isEmpty() && urlPath.contains("play/record")) {
            requestBuilder.header("Referer", referer);
        }

        return chain.proceed(requestBuilder.build());
    }
}
