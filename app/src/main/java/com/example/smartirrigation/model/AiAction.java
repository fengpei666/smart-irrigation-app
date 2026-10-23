package com.example.smartirrigation.model;

/**
 * AI 对话里返回的“待确认操作提案”。
 * 模型只生成提案，App 必须渲染卡片并由用户点确认后，服务端才会真正执行。
 */
public class AiAction {
    public long id;
    /** pump_on / pump_off / schedule_create / zone_create / settings_update */
    public String action;
    /** 参数仅用于排查，界面展示一律用 summary */
    public Object args;
    public String summary;
    public String expires_at;
    public boolean need_confirm;
}
