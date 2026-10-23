package com.example.smartirrigation.model;

/**
 * 分区：GET /api/app/zones、POST /api/app/zones（带 id 为更新）。
 */
public class Zone {
    public Long id;
    public long device_id;
    public String name;
    public double area_m2;
    public String soil_type;
    public String plant;
    public String note;
    public int sort_order;
    public boolean enabled;
}
