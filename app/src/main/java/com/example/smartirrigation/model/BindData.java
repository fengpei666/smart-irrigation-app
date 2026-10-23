package com.example.smartirrigation.model;

/**
 * POST /api/app/bind → {"device_id":3,"sn":"HP-0001","bound":true,"bound_at":"..."}
 * 必须 bound==true 才算绑定成功（不能只判 code==0）。
 */
public class BindData {
    public long device_id;
    public String sn;
    public boolean bound;
    public String bound_at;
}
