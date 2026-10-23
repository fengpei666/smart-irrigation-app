package com.example.smartirrigation.model;

/**
 * GET /api/app/device/status → {device, metrics, settings, safety, last_command}
 * 百分比为整数 0~100 且可能为 null；温湿度 1 位小数。
 */
public class StatusData {
    public DeviceInfo device;
    public Metrics metrics;
    public Settings settings;
    public Safety safety;
    public LastCommand last_command;

    public static class DeviceInfo {
        public long device_id;
        public String sn;
        public String model;
        public String fw;
        public int online;
        public String last_seen_at;
        public int rssi;
        public long uptime;
        public String mode;      // auto / manual
        public String status;    // active / disabled
        public boolean bound;

        public boolean isOnline() {
            return online == 1;
        }
    }

    public static class Metrics {
        public Integer soil_pct;
        public Integer soil_raw;
        public Double temp;
        public Double humi;
        public Integer light_pct;
        public Integer light_raw;
        public Integer water_pct;
        public Integer water_raw;
        public Integer pump;
        public String reported_at;

        public boolean isEmpty() {
            return soil_pct == null && temp == null && humi == null
                    && light_pct == null && water_pct == null && pump == null;
        }
    }

    /** 该设备当前设置（可直接用于设置页初值） */
    public static class Settings {
        public int auto_enabled;
        public int soil_low;
        public int soil_high;
        public int low_water_pct;
        public int report_interval;
        public int pump_max_duration;
        public int ai_enabled;

        public boolean autoEnabled() {
            return auto_enabled == 1;
        }

        public boolean aiEnabled() {
            return ai_enabled == 1;
        }
    }

    public static class Safety {
        public boolean low_water;
        public int low_water_pct;
        public int pump_max_duration;
        public boolean auto_paused;
        public String pause_reason;
    }

    public static class LastCommand {
        public long cmd_id;
        public String action;
        public String status;
        public String created_at;
    }
}
