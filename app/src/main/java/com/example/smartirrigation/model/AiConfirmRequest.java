package com.example.smartirrigation.model;

/** POST /api/app/ai/confirm 请求体 */
public class AiConfirmRequest {
    public long id;
    /** confirm / cancel */
    public String decision;

    public AiConfirmRequest(long id, String decision) {
        this.id = id;
        this.decision = decision;
    }
}
