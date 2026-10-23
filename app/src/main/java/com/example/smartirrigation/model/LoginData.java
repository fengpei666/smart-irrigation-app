package com.example.smartirrigation.model;

import java.util.List;

/**
 * 登录 / 注册响应 data。
 * devices 只是登录那一刻的快照，进主页后必须用 GET /api/app/devices 覆盖。
 */
public class LoginData {
    public String user_token;
    public String expires_at;
    public UserInfo user;
    public List<DeviceItem> devices;

    public static class UserInfo {
        public long id;
        public String nickname;
        public String phone;
    }
}
