package com.example.smartirrigation.network;

import android.text.TextUtils;

import com.example.smartirrigation.session.SessionManager;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 认证拦截器：
 * 1. 为所有请求统一注入 Authorization: Bearer <token>；
 * 2. 命中 401 时清除本地 Token 并通知 UI 层跳转登录页（Token 过期自动登出）。
 */
public class AuthInterceptor implements Interceptor {

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();

        SessionManager sm = SessionManager.peek();
        if (sm != null && !TextUtils.isEmpty(sm.getToken())) {
            request = request.newBuilder()
                    .header("Authorization", "Bearer " + sm.getToken())
                    .build();
        }

        Response response = chain.proceed(request);

        if (response.code() == 401 && sm != null && !sm.isDemoMode()) {
            // 离线演示使用本地假 token，401 不能清登录态
            sm.clearToken();
            NetworkModule.notifyAuthExpired();
        }
        return response;
    }
}
