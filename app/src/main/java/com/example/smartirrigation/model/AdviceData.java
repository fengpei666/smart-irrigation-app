package com.example.smartirrigation.model;

/**
 * GET /api/app/ai/advice
 * {water,duration_sec,reason,source,latency_ms,executable,soil_pct,water_pct}
 * source: ai（外部模型）/ rule（超时、失败或未配置时的规则兜底）
 * executable=false 表示即便 water=1 也不能执行（安全策略不允许），App 不得下发。
 */
public class AdviceData {
    public int water;
    public int duration_sec;
    public String reason;
    public String source;
    public int latency_ms;
    public boolean executable;
    public Integer soil_pct;
    public Integer water_pct;

    public boolean shouldWater() {
        return water == 1;
    }
}
