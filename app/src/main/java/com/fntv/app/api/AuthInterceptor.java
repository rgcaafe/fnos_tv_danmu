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
    private String cookie;

    public void setToken(String token) {
        this.token = token;
    }

    public String getToken() {
        return token;
    }

    public void setReferer(String referer) {
        this.referer = referer;
    }

    public void setCookie(String cookie) {
        this.cookie = cookie;
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
        } else {
            String query = original.url().query();
            if (query != null && !query.isEmpty()) {
                jsonBody = query;
            }
        }

        String authx = FnAuthUtils.genAuthx(urlPath, jsonBody);

        Request.Builder requestBuilder = original.newBuilder()
                .header("Accept", "*/*")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36")
                .header("Authx", authx)
                .header("x-trim-client", "web")
                .header("x-trim-client-version", "608");

        if (cookie != null && !cookie.isEmpty()) {
            requestBuilder.header("Cookie", cookie);
        } else if (token != null && !token.isEmpty()) {
            requestBuilder.header("Cookie", "language=zh-CN; Trim-MC-token=" + token);
        } else {
            requestBuilder.header("Cookie", "mode=relay");
        }

        if (token != null) {
            requestBuilder.header("Authorization", token);
        }

        if (referer != null && !referer.isEmpty() && urlPath.contains("play/record")) {
            requestBuilder.header("Referer", referer);
        }

        return chain.proceed(requestBuilder.build());
    }
}
