package com.example.smartirrigation.network;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Handler;
import android.os.Looper;

import com.example.smartirrigation.session.SessionManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * 网络模块（契约 v1.0）。
 * - BASE_URL：中文域名 下注.cc 的 punycode = xn--ghq556e.cc，必须 https；
 * - 超时：连接 5s / 读 10s；GET 失败自动重试 1 次（POST 不重试，避免重复下发命令）；
 * - 业务码与 HTTP 状态并存：两端都解析（服务端会把 400/401/403 等直接作为 HTTP 状态）；
 * - 401 → 清 token 回登录；2004 → 清本地 device_id 并通知刷新设备列表。
 */
public final class NetworkModule {

    public static final String BASE_URL = "https://xn--ghq556e.cc/";

    private static final long CONNECT_TIMEOUT_SECONDS = 5;
    private static final long READ_TIMEOUT_SECONDS = 10;
    private static final long WRITE_TIMEOUT_SECONDS = 10;
    /** AI 问答实测约 2 秒，弱网留足余量：30 秒 */
    private static final long CHAT_READ_TIMEOUT_SECONDS = 35;

    private static ApiService api;
    /** AI 问答专用通道（更长的读超时），其余接口仍用 10 秒快速失败 */
    private static ApiService chatApi;
    private static final List<WeakReference<AuthListener>> AUTH_LISTENERS = new ArrayList<>();
    private static final List<WeakReference<DeviceListener>> DEVICE_LISTENERS = new ArrayList<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Gson ERROR_GSON = new Gson();

    /** 401：登录态失效 */
    public interface AuthListener {
        void onAuthExpired();
    }

    /** 2004：设备未绑定到当前账号，需要刷新设备列表并清除本地 device_id */
    public interface DeviceListener {
        void onDeviceUnbound();
    }

    private NetworkModule() {
    }

    public static void init(Context context) {
        if (api != null) {
            return;
        }
        // 只打请求行：BODY 会在 2 秒轮询时产生大量字符串拼接与日志 I/O，拖慢弱机
        boolean debuggable = (context.getApplicationInfo().flags
                & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(debuggable
                ? HttpLoggingInterceptor.Level.BASIC
                : HttpLoggingInterceptor.Level.NONE);

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .addInterceptor(new AuthInterceptor())
                .addInterceptor(logging)
                .build();

        // 服务端用 tinyint 表示布尔的情况很常见（enabled / bound / low_water），
        // 默认 Gson 只认 true/false，会导致整包解析失败，统一做容错
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(boolean.class, new FlexibleBooleanAdapter())
                .registerTypeAdapter(Boolean.class, new FlexibleBooleanAdapter())
                .registerTypeAdapter(int.class, FlexibleNumberAdapter.of(int.class))
                .registerTypeAdapter(Integer.class, FlexibleNumberAdapter.of(Integer.class))
                .registerTypeAdapter(long.class, FlexibleNumberAdapter.of(long.class))
                .registerTypeAdapter(Long.class, FlexibleNumberAdapter.of(Long.class))
                .registerTypeAdapter(double.class, FlexibleNumberAdapter.of(double.class))
                .registerTypeAdapter(Double.class, FlexibleNumberAdapter.of(Double.class))
                .registerTypeAdapter(float.class, FlexibleNumberAdapter.of(float.class))
                .registerTypeAdapter(Float.class, FlexibleNumberAdapter.of(Float.class))
                .create();
        GsonConverterFactory converterFactory = GsonConverterFactory.create(gson);

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(converterFactory)
                .build();
        api = retrofit.create(ApiService.class);

        OkHttpClient chatClient = client.newBuilder()
                .readTimeout(CHAT_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
        chatApi = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(chatClient)
                .addConverterFactory(converterFactory)
                .build()
                .create(ApiService.class);
    }

    public static ApiService api() {
        if (api == null) {
            throw new IllegalStateException("NetworkModule.init 未调用");
        }
        return api;
    }

    /** AI 对话接口（读超时 35 秒），未初始化时回退到默认通道 */
    public static ApiService chatApi() {
        if (chatApi != null) {
            return chatApi;
        }
        return api();
    }

    // ---------------- 监听器 ----------------

    public static void addAuthListener(AuthListener listener) {
        AUTH_LISTENERS.add(new WeakReference<>(listener));
    }

    public static void removeAuthListener(AuthListener listener) {
        Iterator<WeakReference<AuthListener>> it = AUTH_LISTENERS.iterator();
        while (it.hasNext()) {
            AuthListener ref = it.next().get();
            if (ref == null || ref == listener) {
                it.remove();
            }
        }
    }

    public static void addDeviceListener(DeviceListener listener) {
        DEVICE_LISTENERS.add(new WeakReference<>(listener));
    }

    public static void removeDeviceListener(DeviceListener listener) {
        Iterator<WeakReference<DeviceListener>> it = DEVICE_LISTENERS.iterator();
        while (it.hasNext()) {
            DeviceListener ref = it.next().get();
            if (ref == null || ref == listener) {
                it.remove();
            }
        }
    }

    static void notifyAuthExpired() {
        SessionManager sm = SessionManager.peek();
        if (sm != null && sm.isDemoMode()) {
            return;
        }
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                for (WeakReference<AuthListener> wr : new ArrayList<>(AUTH_LISTENERS)) {
                    AuthListener l = wr.get();
                    if (l != null) {
                        l.onAuthExpired();
                    }
                }
            }
        });
    }

    static void notifyDeviceUnbound() {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                for (WeakReference<DeviceListener> wr : new ArrayList<>(DEVICE_LISTENERS)) {
                    DeviceListener l = wr.get();
                    if (l != null) {
                        l.onDeviceUnbound();
                    }
                }
            }
        });
    }

    // ---------------- 统一请求封装 ----------------

    public static <T> void enqueue(Call<ApiResult<T>> call, final ApiCallback<T> callback) {
        enqueueInternal(call, callback, 0);
    }

    private static <T> void enqueueInternal(final Call<ApiResult<T>> call,
                                           final ApiCallback<T> callback,
                                           final int attempt) {
        call.enqueue(new Callback<ApiResult<T>>() {
            @Override
            public void onResponse(Call<ApiResult<T>> c, Response<ApiResult<T>> response) {
                if (response.code() == 401) {
                    SessionManager sm = SessionManager.peek();
                    if (sm != null && sm.isDemoMode()) {
                        // 演示 token 是本地假的，401 不代表登录失效
                        callback.onError("离线演示模式：不连接服务器");
                        return;
                    }
                    handleAuthExpired();
                    callback.onError(ApiResult.codeText(401));
                    return;
                }
                ApiResult<T> body = response.body();
                if (!response.isSuccessful() || body == null) {
                    // 服务端把业务码当 HTTP 状态返回（400/403/404...），必须解析 errorBody
                    handleErrorBody(response, callback);
                    return;
                }
                if (body.isSuccess()) {
                    callback.onSuccess(body.data);
                    return;
                }
                dispatchBusinessCode(body.code);
                callback.onError(ApiResult.messageOf(body.code, body.msg));
            }

            @Override
            public void onFailure(Call<ApiResult<T>> c, Throwable t) {
                // 仅幂等的 GET 重试 1 次，避免控制类 POST 被重复下发
                boolean canRetry = attempt == 0
                        && "GET".equalsIgnoreCase(c.request().method())
                        && t instanceof java.io.IOException;
                if (canRetry) {
                    enqueueInternal(c.clone(), callback, 1);
                    return;
                }
                if (t instanceof java.io.IOException) {
                    callback.onError("网络异常或超时（连接 5s / 读取 10s），请检查网络");
                } else {
                    callback.onError(t.getMessage() == null ? "请求失败" : t.getMessage());
                }
            }
        });
    }

    private static void handleErrorBody(Response<?> response, final ApiCallback<?> callback) {
        String raw = "";
        try {
            if (response.errorBody() != null) {
                raw = response.errorBody().string();
            }
        } catch (Exception ignored) {
            // 读取失败时按状态码兜底
        }

        ApiResult<?> err = null;
        if (!raw.isEmpty()) {
            try {
                err = ERROR_GSON.fromJson(raw, ApiResult.class);
            } catch (Exception ignored) {
                err = null;
            }
        }

        if (err != null && err.msg != null && !err.msg.isEmpty()) {
            dispatchBusinessCode(err.code);
            callback.onError(err.msg);
            return;
        }

        if (!raw.isEmpty()) {
            String snippet = raw.replaceAll("\\s+", " ").trim();
            if (snippet.length() > 120) {
                snippet = snippet.substring(0, 120) + "…";
            }
            callback.onError("HTTP " + response.code() + "：" + snippet);
            return;
        }
        callback.onError("服务器错误（HTTP " + response.code() + "）");
    }

    private static void dispatchBusinessCode(int code) {
        if (code == 401) {
            handleAuthExpired();
        } else if (code == 2004) {
            SessionManager sm = SessionManager.peek();
            if (sm != null) {
                sm.clearDeviceId();
            }
            notifyDeviceUnbound();
        }
    }

    private static void handleAuthExpired() {
        SessionManager sm = SessionManager.peek();
        if (sm != null) {
            // 离线演示模式使用本地假 token，401 不应把用户踢回登录页
            if (sm.isDemoMode()) {
                return;
            }
            sm.clearToken();
        }
        notifyAuthExpired();
    }
}
