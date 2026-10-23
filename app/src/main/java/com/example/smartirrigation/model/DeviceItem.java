package com.example.smartirrigation.model;

/**
 * 设备项：登录快照 devices[] 与 GET /api/app/devices 共用。
 * 注意：以 /api/app/devices 为唯一可信来源（online/pending_count/最近指标由服务端实时计算）。
 */
public class DeviceItem {
    public long device_id;
    public String sn;
    public String model;
    public boolean bound;
    public int online;
    public String status;        // active / disabled ...
    public String last_seen_at;
    public String bound_at;
    public Integer soil_pct;     // 可能为 null（未标定/未接传感器）
    public Integer water_pct;
    public Double temp;
    public String reported_at;
    public int pending_count;    // 未过期待执行命令数

    public boolean isOnline() {
        return online == 1;
    }

    /** 绑定标记：接口有 bound 字段时以它为准，登录快照缺少时用 bound_at 兜底 */
    public boolean isBound() {
        return bound || bound_at != null;
    }
}
