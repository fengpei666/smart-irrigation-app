package com.example.smartirrigation.model;

/**
 * POST /api/app/bind
 * {"sn":"HP-DEMO-0001","bind_code":"123456"}
 */
public class BindRequest {
    public String sn;
    public String bind_code;

    public BindRequest(String sn, String bindCode) {
        this.sn = sn;
        this.bind_code = bindCode;
    }
}
