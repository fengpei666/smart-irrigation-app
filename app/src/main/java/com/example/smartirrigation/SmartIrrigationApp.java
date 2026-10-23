package com.example.smartirrigation;

import android.app.Application;

import com.example.smartirrigation.network.NetworkModule;
import com.example.smartirrigation.session.SessionManager;

/**
 * 应用入口：初始化网络模块与会话缓存。
 */
public class SmartIrrigationApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        SessionManager.init(this);
        NetworkModule.init(this);
    }
}
