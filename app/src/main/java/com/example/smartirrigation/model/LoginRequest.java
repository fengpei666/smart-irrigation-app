package com.example.smartirrigation.model;

/**
 * POST /api/app/login
 * {"account":"demo","password":"demo123","label":"android"}
 */
public class LoginRequest {
    public String account;
    public String password;
    public String label;

    public LoginRequest(String account, String password, String label) {
        this.account = account;
        this.password = password;
        this.label = label;
    }
}
