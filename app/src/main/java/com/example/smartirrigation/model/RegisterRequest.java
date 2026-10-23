package com.example.smartirrigation.model;

/**
 * POST /api/app/register
 * username 3~32 位小写字母/数字/下划线；password ≥6 位。
 */
public class RegisterRequest {
    public String username;
    public String password;
    public String nickname;

    public RegisterRequest(String username, String password, String nickname) {
        this.username = username;
        this.password = password;
        this.nickname = nickname;
    }
}
