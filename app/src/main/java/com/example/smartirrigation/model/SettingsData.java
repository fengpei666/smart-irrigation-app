package com.example.smartirrigation.model;

/**
 * POST /api/app/device/settings
 * 字段：soil_low / soil_high / low_water_pct / report_interval / pump_max_duration / auto_enabled(0,1) / ai_enabled(0,1)
 * 服务端回写完整设置对象。
 */
public class SettingsData {
    public long device_id;
    public int auto_enabled;
    public int soil_low;
    public int soil_high;
    public int low_water_pct;
    public int report_interval;
    public int pump_max_duration;
    public int ai_enabled;
}
