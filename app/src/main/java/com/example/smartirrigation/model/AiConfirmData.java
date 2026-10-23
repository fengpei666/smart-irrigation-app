package com.example.smartirrigation.model;

/** POST /api/app/ai/confirm 响应 data */
public class AiConfirmData {
    public long id;
    /** confirmed / canceled / failed */
    public String status;
    public String message;
    /** 执行结果，例如 {"schedule_id":2,"next_run_at":"..."} */
    public Object data;
}
