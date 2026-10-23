package com.example.smartirrigation.model;

import java.util.List;

/**
 * GET /api/app/devices → {"total":1,"devices":[...]}
 * 设备唯一可信来源：online / pending_count / 最近指标都由服务端实时计算。
 */
public class DevicesData {
    public int total;
    public List<DeviceItem> devices;
}
