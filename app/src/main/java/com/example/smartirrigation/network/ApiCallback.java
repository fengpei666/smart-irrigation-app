package com.example.smartirrigation.network;

/**
 * 统一异步回调（Retrofit 回调已在主线程执行，可直接更新 UI）。
 */
public interface ApiCallback<T> {
    void onSuccess(T data);

    /**
     * @param message 已翻译为中文的可读原因（网络异常 / 登录过期 / 业务失败原因）
     */
    void onError(String message);
}
