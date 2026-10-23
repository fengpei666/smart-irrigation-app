package com.example.smartirrigation.model;

/**
 * 灌溉计划：type=time|condition|rotation
 * - time：time_of_day + weekdays（周一~周日，1=启用，如 "1111100"）
 * - condition：condition_type = soil_below|temp_above|humi_below|light_below + condition_value
 * - rotation：interval_min 轮灌间隔（分钟）
 * 计划由服务器 cron/tick.php 调度执行，App 只做配置。
 */
public class Schedule {
    public Long id;
    public long device_id;
    public String name;
    public String type;
    public String time_of_day;
    public String weekdays;
    public Integer interval_min;
    public String condition_type;
    public Double condition_value;
    public int duration_sec;
    public boolean enabled;
    public String next_run_at;
    public String last_run_at;
}
